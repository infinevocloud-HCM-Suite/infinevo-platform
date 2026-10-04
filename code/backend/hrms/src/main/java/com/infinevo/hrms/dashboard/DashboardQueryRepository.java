package com.infinevo.hrms.dashboard;

import com.infinevo.hrms.project.Priority;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.hrms.project.TaskStatus;
import com.infinevo.hrms.timesheet.TimesheetStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard's aggregate reads (W-44 §4) over {@code hrms.project}, {@code hrms.task}, {@code hrms.assignment},
 * {@code hrms.clock_session} and the four timesheet tables. Every statement binds {@code tenant_id}; counts and sums
 * happen in the database; a list is at most {@link #LIMIT} rows. It adds no finder to the W-40, W-41 or W-42
 * repositories, so no ticket collides. A method given an empty collection answers empty without a round trip. Named
 * explicitly: payroll has a {@code DashboardQueryRepository} too (W-37), and one context holds both.
 */
@Repository("hrmsDashboardQueryRepository")
@Transactional(readOnly = true)
public class DashboardQueryRepository {

    /** Rows per list (W-44 §13 decision 2). */
    public static final int LIMIT = 5;

    /** One week of one employee's timesheet with the sum of its day entries; {@code hours} null when none. */
    public record WeekRow(UUID timesheetId, LocalDate weekStart, TimesheetStatus status, BigDecimal hours) {}

    public record ProjectRow(UUID id, String name, ProjectStatus status, int progress, LocalDate endDate) {}

    public record TaskRow(
            UUID id,
            UUID projectId,
            String projectName,
            String title,
            TaskStatus status,
            Priority priority,
            LocalDate dueDate) {}

    /** Open and overdue tasks of one project. */
    public record TaskLoad(long open, long overdue) {}

    public record WaitingRow(
            UUID timesheetId,
            UUID projectEntryId,
            UUID employeeId,
            String firstName,
            String lastName,
            String projectName,
            LocalDate weekStart,
            Instant submittedAt) {}

    /** Open tasks due before today, and due from today to the end of the week. */
    public record DueCounts(long overdue, long dueThisWeek) {}

    /** Live started projects on which the employee has a live assignment: shared by the count and the list. */
    private static final String MY_PROJECTS = " FROM Project p WHERE p.tenantId = :tenantId AND p.deleted = false"
            + " AND p.status = :started AND EXISTS (SELECT 1 FROM Assignment a WHERE a.tenantId = :tenantId"
            + " AND a.projectId = p.id AND a.employeeId = :employeeId AND a.deleted = false)";

    /** Live tasks assigned to the employee on live projects, not completed. */
    private static final String MY_OPEN_TASKS = " FROM Task t JOIN Project p ON p.id = t.projectId"
            + " AND p.tenantId = :tenantId AND p.deleted = false"
            + " WHERE t.tenantId = :tenantId AND t.assigneeEmployeeId = :employeeId AND t.deleted = false"
            + " AND t.status <> :completed";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * The employee's non-cancelled sheets for the given weeks, each with the sum of its
     * {@code timesheet_day_entry.hours}. The unique index admits one non-cancelled sheet per employee and week, so a
     * week is at most one row.
     */
    public List<WeekRow> weeks(UUID tenantId, UUID employeeId, Collection<LocalDate> weekStarts) {
        if (weekStarts.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery(
                        "SELECT t.id, t.weekStartDate, t.status, SUM(d.hours) FROM Timesheet t"
                                + " LEFT JOIN t.projects pe ON pe.tenantId = :tenantId"
                                + " LEFT JOIN pe.tasks te ON te.tenantId = :tenantId"
                                + " LEFT JOIN te.days d ON d.tenantId = :tenantId"
                                + " WHERE t.tenantId = :tenantId AND t.employeeId = :employeeId"
                                + " AND t.weekStartDate IN (:weeks) AND t.status <> :cancelled"
                                + " GROUP BY t.id, t.weekStartDate, t.status",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("employeeId", employeeId)
                .setParameter("weeks", weekStarts)
                .setParameter("cancelled", TimesheetStatus.CANCELLED)
                .getResultList()
                .stream()
                .map(r -> new WeekRow((UUID) r[0], (LocalDate) r[1], (TimesheetStatus) r[2], (BigDecimal) r[3]))
                .toList();
    }

    public long countMyProjects(UUID tenantId, UUID employeeId) {
        return entityManager
                .createQuery("SELECT COUNT(p)" + MY_PROJECTS, Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("employeeId", employeeId)
                .setParameter("started", ProjectStatus.STARTED)
                .getSingleResult();
    }

    /** The first {@link #LIMIT} of the employee's live started projects by end date, open-ended last. */
    public List<ProjectRow> myProjects(UUID tenantId, UUID employeeId) {
        return entityManager
                .createQuery(
                        "SELECT p.id, p.name, p.status, p.progress, p.endDate" + MY_PROJECTS
                                + " ORDER BY p.endDate ASC NULLS LAST, p.name ASC, p.id ASC",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("employeeId", employeeId)
                .setParameter("started", ProjectStatus.STARTED)
                .setMaxResults(LIMIT)
                .getResultList()
                .stream()
                .map(DashboardQueryRepository::toProjectRow)
                .toList();
    }

    /** The employee's open tasks counted by status; a status with none is absent. */
    public Map<TaskStatus, Long> myOpenTasksByStatus(UUID tenantId, UUID employeeId) {
        List<Object[]> rows = entityManager
                .createQuery("SELECT t.status, COUNT(t)" + MY_OPEN_TASKS + " GROUP BY t.status", Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("employeeId", employeeId)
                .setParameter("completed", TaskStatus.COMPLETED)
                .getResultList();
        Map<TaskStatus, Long> result = new EnumMap<>(TaskStatus.class);
        for (Object[] row : rows) {
            result.put((TaskStatus) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    /** The employee's open tasks due before {@code today}, and due from {@code today} to {@code weekEnd}. */
    public DueCounts myDueCounts(UUID tenantId, UUID employeeId, LocalDate today, LocalDate weekEnd) {
        Object[] row = entityManager
                .createQuery(
                        "SELECT COALESCE(SUM(CASE WHEN t.dueDate < :today THEN 1 ELSE 0 END), 0),"
                                + " COALESCE(SUM(CASE WHEN t.dueDate >= :today AND t.dueDate <= :weekEnd"
                                + " THEN 1 ELSE 0 END), 0)" + MY_OPEN_TASKS,
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("employeeId", employeeId)
                .setParameter("completed", TaskStatus.COMPLETED)
                .setParameter("today", today)
                .setParameter("weekEnd", weekEnd)
                .getSingleResult();
        return new DueCounts(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
    }

    /** The employee's next {@link #LIMIT} open tasks by due date, undated last. */
    public List<TaskRow> myNextTasks(UUID tenantId, UUID employeeId) {
        return entityManager
                .createQuery(
                        "SELECT t.id, t.projectId, p.name, t.title, t.status, t.priority, t.dueDate" + MY_OPEN_TASKS
                                + " ORDER BY t.dueDate ASC NULLS LAST, t.title ASC, t.id ASC",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("employeeId", employeeId)
                .setParameter("completed", TaskStatus.COMPLETED)
                .setMaxResults(LIMIT)
                .getResultList()
                .stream()
                .map(r -> new TaskRow(
                        (UUID) r[0],
                        (UUID) r[1],
                        (String) r[2],
                        (String) r[3],
                        (TaskStatus) r[4],
                        (Priority) r[5],
                        (LocalDate) r[6]))
                .toList();
    }

    /** Live projects among {@code projectIds} counted by status; a status with none is absent. */
    public Map<ProjectStatus, Long> projectsByStatus(UUID tenantId, Collection<UUID> projectIds) {
        Map<ProjectStatus, Long> result = new EnumMap<>(ProjectStatus.class);
        if (projectIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager
                .createQuery(
                        "SELECT p.status, COUNT(p) FROM Project p WHERE p.tenantId = :tenantId"
                                + " AND p.id IN (:ids) AND p.deleted = false GROUP BY p.status",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", projectIds)
                .getResultList();
        for (Object[] row : rows) {
            result.put((ProjectStatus) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    /** The first {@link #LIMIT} live {@code STARTED} projects among {@code projectIds} by end date, open-ended last. */
    public List<ProjectRow> startedProjects(UUID tenantId, Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery(
                        "SELECT p.id, p.name, p.status, p.progress, p.endDate FROM Project p"
                                + " WHERE p.tenantId = :tenantId AND p.id IN (:ids) AND p.deleted = false"
                                + " AND p.status = :started ORDER BY p.endDate ASC NULLS LAST, p.name ASC, p.id ASC",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", projectIds)
                .setParameter("started", ProjectStatus.STARTED)
                .setMaxResults(LIMIT)
                .getResultList()
                .stream()
                .map(DashboardQueryRepository::toProjectRow)
                .toList();
    }

    /** Live assignments per project; a project with none is absent. */
    public Map<UUID, Long> teamSizes(UUID tenantId, Collection<UUID> projectIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager
                .createQuery(
                        "SELECT a.projectId, COUNT(a) FROM Assignment a WHERE a.tenantId = :tenantId"
                                + " AND a.projectId IN (:ids) AND a.deleted = false GROUP BY a.projectId",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", projectIds)
                .getResultList();
        for (Object[] row : rows) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    /** Live, not completed tasks per project, and of those the ones due before {@code today}; none is absent. */
    public Map<UUID, TaskLoad> taskLoads(UUID tenantId, Collection<UUID> projectIds, LocalDate today) {
        Map<UUID, TaskLoad> result = new HashMap<>();
        if (projectIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager
                .createQuery(
                        "SELECT t.projectId, COUNT(t), COALESCE(SUM(CASE WHEN t.dueDate < :today THEN 1 ELSE 0 END), 0)"
                                + " FROM Task t WHERE t.tenantId = :tenantId AND t.projectId IN (:ids)"
                                + " AND t.deleted = false AND t.status <> :completed GROUP BY t.projectId",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", projectIds)
                .setParameter("completed", TaskStatus.COMPLETED)
                .setParameter("today", today)
                .getResultList();
        for (Object[] row : rows) {
            result.put((UUID) row[0], new TaskLoad(((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }
        return result;
    }

    /** Project entries {@code SUBMITTED} on {@code projectIds}; a draft is never counted (W-44 §13 decision 3). */
    public long countWaiting(UUID tenantId, Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return 0;
        }
        return entityManager
                .createQuery(
                        "SELECT COUNT(pe) FROM TimesheetProjectEntry pe WHERE pe.tenantId = :tenantId"
                                + " AND pe.projectId IN (:ids) AND pe.status = :submitted",
                        Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", projectIds)
                .setParameter("submitted", TimesheetStatus.SUBMITTED)
                .getSingleResult();
    }

    /** The oldest {@link #LIMIT} of those entries by their week's {@code submitted_at}, with the names to show. */
    public List<WaitingRow> oldestWaiting(UUID tenantId, Collection<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery(
                        "SELECT t.id, pe.id, t.employeeId, e.firstName, e.lastName, p.name, t.weekStartDate,"
                                + " t.submittedAt FROM TimesheetProjectEntry pe"
                                + " JOIN pe.timesheet t"
                                + " JOIN Project p ON p.id = pe.projectId AND p.tenantId = :tenantId"
                                + " LEFT JOIN Employee e ON e.id = t.employeeId AND e.tenantId = :tenantId"
                                + " WHERE pe.tenantId = :tenantId AND t.tenantId = :tenantId"
                                + " AND pe.projectId IN (:ids) AND pe.status = :submitted"
                                + " ORDER BY t.submittedAt ASC NULLS LAST, t.weekStartDate ASC, pe.id ASC",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", projectIds)
                .setParameter("submitted", TimesheetStatus.SUBMITTED)
                .setMaxResults(LIMIT)
                .getResultList()
                .stream()
                .map(r -> new WaitingRow(
                        (UUID) r[0],
                        (UUID) r[1],
                        (UUID) r[2],
                        (String) r[3],
                        (String) r[4],
                        (String) r[5],
                        (LocalDate) r[6],
                        (Instant) r[7]))
                .toList();
    }

    /** How many of {@code employeeIds} have a non-voided clock session, open or closed, dated {@code date}. */
    public long countClockedIn(UUID tenantId, Collection<UUID> employeeIds, LocalDate date) {
        if (employeeIds.isEmpty()) {
            return 0;
        }
        return entityManager
                .createQuery(
                        "SELECT COUNT(DISTINCT c.employeeId) FROM ClockSession c WHERE c.tenantId = :tenantId"
                                + " AND c.employeeId IN (:ids) AND c.attendanceDate = :date AND c.voidedAt IS NULL",
                        Long.class)
                .setParameter("tenantId", tenantId)
                .setParameter("ids", employeeIds)
                .setParameter("date", date)
                .getSingleResult();
    }

    private static ProjectRow toProjectRow(Object[] r) {
        return new ProjectRow(
                (UUID) r[0], (String) r[1], (ProjectStatus) r[2], ((Number) r[3]).intValue(), (LocalDate) r[4]);
    }
}
