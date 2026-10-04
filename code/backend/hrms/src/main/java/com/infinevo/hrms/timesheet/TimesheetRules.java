package com.infinevo.hrms.timesheet;

import com.infinevo.hrms.timesheet.TimesheetRequest.DayLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The shape rules for a timesheet request (W-42.1 §4, "Validation"): everything that can be decided from the body
 * alone. What needs the database (is the project live and assigned to the caller, is the task on that project) is
 * the service's.
 *
 * <p>Pure: no clock, no repository, so every row of the spec's table is a unit test. Keys name the offending
 * field, for example {@code projects[0].tasks[1].days[2].hours}.
 */
public final class TimesheetRules {

    static final BigDecimal MAX_HOURS = BigDecimal.valueOf(24);
    static final int MAX_DESCRIPTION = 500;

    private TimesheetRules() {}

    /** The Monday of the week that contains {@code date}. */
    public static LocalDate mondayOf(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Whether {@code date} is a Monday: the week runs Monday to Sunday, as legacy, and is not per tenant. */
    static boolean isMonday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY;
    }

    /**
     * Every shape error in the request, by field; empty when it is sound.
     *
     * @param request the body
     */
    static Map<String, String> validate(TimesheetRequest request) {
        return validate(request, Map.of());
    }

    /**
     * As {@link #validate(TimesheetRequest)}, when the request is not the whole week: {@code carried} is the hours per
     * date that the week already holds on lines the request does not replace (a resubmit sends only the rejected
     * projects), so that the 24 hours a day rule still counts the whole week.
     */
    static Map<String, String> validate(TimesheetRequest request, Map<LocalDate, BigDecimal> carried) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request == null) {
            errors.put("request", "A request body is required");
            return errors;
        }

        LocalDate weekStart = request.weekStartDate();
        boolean weekOk = false;
        if (weekStart == null) {
            errors.put("week_start_date", "week_start_date is required");
        } else if (!isMonday(weekStart)) {
            errors.put("week_start_date", "week_start_date must be a Monday; the week runs Monday to Sunday");
        } else {
            weekOk = true;
        }

        List<ProjectLine> projects = request.projects();
        if (projects == null || projects.isEmpty()) {
            errors.put("projects", "At least one project with one day entry is required");
            return errors;
        }

        Set<UUID> seenProjects = new HashSet<>();
        // Hours per date across every project and task, for the 24 hours a day rule.
        Map<LocalDate, BigDecimal> dayTotals = new TreeMap<>(carried);
        for (int p = 0; p < projects.size(); p++) {
            ProjectLine project = projects.get(p);
            String pk = "projects[" + p + "]";
            if (project == null) {
                errors.put(pk, "A project line is required");
                continue;
            }
            if (project.projectId() == null) {
                errors.put(pk + ".project_id", "project_id is required");
            } else if (!seenProjects.add(project.projectId())) {
                errors.put(pk + ".project_id", "A project may appear only once on a timesheet");
            }
            validateTasks(errors, pk, project.tasks(), weekOk ? weekStart : null, dayTotals);
        }

        for (Map.Entry<LocalDate, BigDecimal> total : dayTotals.entrySet()) {
            if (total.getValue().compareTo(MAX_HOURS) > 0) {
                errors.put(
                        "days[" + total.getKey() + "]",
                        "More than 24 hours on " + total.getKey() + " across all tasks (" + total.getValue() + ")");
            }
        }
        return errors;
    }

    private static void validateTasks(
            Map<String, String> errors,
            String pk,
            List<TaskLine> tasks,
            LocalDate weekStart,
            Map<LocalDate, BigDecimal> dayTotals) {
        if (tasks == null || tasks.isEmpty()) {
            errors.put(pk + ".tasks", "Each project needs at least one task");
            return;
        }
        Set<UUID> seenTasks = new HashSet<>();
        for (int t = 0; t < tasks.size(); t++) {
            TaskLine task = tasks.get(t);
            String tk = pk + ".tasks[" + t + "]";
            if (task == null) {
                errors.put(tk, "A task line is required");
                continue;
            }
            if (task.taskId() == null) {
                errors.put(tk + ".task_id", "task_id is required");
            } else if (!seenTasks.add(task.taskId())) {
                errors.put(tk + ".task_id", "A task may appear only once on a project");
            }
            validateDays(errors, tk, task.days(), weekStart, dayTotals);
        }
    }

    private static void validateDays(
            Map<String, String> errors,
            String tk,
            List<DayLine> days,
            LocalDate weekStart,
            Map<LocalDate, BigDecimal> dayTotals) {
        if (days == null || days.isEmpty()) {
            errors.put(tk + ".days", "Each task needs at least one day entry");
            return;
        }
        Set<LocalDate> seenDates = new HashSet<>();
        for (int d = 0; d < days.size(); d++) {
            DayLine day = days.get(d);
            String dk = tk + ".days[" + d + "]";
            if (day == null) {
                errors.put(dk, "A day entry is required");
                continue;
            }
            boolean dateOk = false;
            if (day.date() == null) {
                errors.put(dk + ".date", "date is required");
            } else if (weekStart != null
                    && (day.date().isBefore(weekStart) || day.date().isAfter(weekStart.plusDays(6)))) {
                errors.put(
                        dk + ".date",
                        "date " + day.date() + " is outside the week " + weekStart + " to " + weekStart.plusDays(6));
            } else if (!seenDates.add(day.date())) {
                errors.put(dk + ".date", "A date may appear only once on a task");
            } else {
                dateOk = true;
            }

            boolean hoursOk = false;
            BigDecimal hours = day.hours();
            if (hours == null) {
                errors.put(dk + ".hours", "hours is required");
            } else if (hours.signum() <= 0) {
                errors.put(dk + ".hours", "hours must be greater than 0");
            } else if (hours.compareTo(MAX_HOURS) > 0) {
                errors.put(dk + ".hours", "hours must be at most 24");
            } else if (hours.stripTrailingZeros().scale() > 2) {
                errors.put(dk + ".hours", "hours may have at most two decimal places");
            } else {
                hoursOk = true;
            }

            if (day.description() != null && day.description().length() > MAX_DESCRIPTION) {
                errors.put(dk + ".description", "description must be at most " + MAX_DESCRIPTION + " characters");
            }
            if (dateOk && hoursOk) {
                dayTotals.merge(day.date(), hours, BigDecimal::add);
            }
        }
    }
}
