package com.infinevo.hrms.timesheet;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.hrms.project.Project;
import com.infinevo.hrms.project.ProjectRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The two notifications of the timesheet approval flow (W-42.3 §4): {@code APPROVAL_PENDING} to each approver when a
 * week or a rejected project goes for approval, and {@code APPROVAL_DECIDED} to the employee when a project line is
 * decided.
 *
 * <p>A failed notification must never undo the change it reports, so every call is wrapped and logged
 * ({@code ProofOutcomeHandler} does the same). The pending notices are composed after the submit commits, in a
 * transaction of their own: the submit is already safe by then, and a mail that cannot be composed cannot touch it.
 * The decided notice is composed inside the outcome handler's own transaction, which exists to carry the outcome.
 */
@Component
class TimesheetNotifier {

    private static final Logger log = LoggerFactory.getLogger(TimesheetNotifier.class);

    /** One approver to tell: whose line of which project went for approval. */
    record Pending(UUID approverEmployeeId, UUID projectId) {}

    private final NotificationService notificationService;
    private final EmployeeService employeeService;
    private final ProjectRepository projectRepository;
    private final PlatformTransactionManager transactionManager;

    TimesheetNotifier(
            NotificationService notificationService,
            EmployeeService employeeService,
            ProjectRepository projectRepository,
            PlatformTransactionManager transactionManager) {
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
    }

    /**
     * Tells each approver, once the caller's transaction has committed (at once when there is none).
     *
     * <p>Every name and title is read now, inside the caller's transaction, so that the callback does one thing only:
     * compose, in a transaction of its own. It runs while the caller's connection is still held, so reads there would
     * need a third connection and could starve a small pool.
     */
    void pendingAfterCommit(UUID tenantId, UUID requesterEmployeeId, LocalDate weekStart, List<Pending> pending) {
        if (pending.isEmpty()) {
            return;
        }
        String requester = name(requesterEmployeeId);
        List<Map.Entry<UUID, Map<String, Object>>> notices = new ArrayList<>();
        for (Pending notice : pending) {
            notices.add(Map.entry(
                    notice.approverEmployeeId(),
                    Map.of(
                            "employee_name", name(notice.approverEmployeeId()),
                            "requester_name", requester,
                            "request_title", title(tenantId, notice.projectId(), weekStart))));
        }
        Runnable compose = () -> composePending(tenantId, notices);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    compose.run();
                }
            });
        } else {
            compose.run();
        }
    }

    /** Tells the employee their project line was decided. Call inside the transaction that records the outcome. */
    void decided(UUID tenantId, UUID employeeId, UUID projectId, LocalDate weekStart, boolean approved) {
        try {
            notificationService.compose(
                    NotificationEvent.APPROVAL_DECIDED,
                    employeeId,
                    Map.of(
                            "employee_name", name(employeeId),
                            "request_title", title(tenantId, projectId, weekStart),
                            "decision", approved ? "approved" : "rejected"));
        } catch (RuntimeException e) {
            log.warn("APPROVAL_DECIDED not composed for employee {} in tenant {}", employeeId, tenantId, e);
        }
    }

    private void composePending(UUID tenantId, List<Map.Entry<UUID, Map<String, Object>>> notices) {
        boolean boundHere = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            boundHere = true;
        }
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            for (Map.Entry<UUID, Map<String, Object>> notice : notices) {
                try {
                    tx.executeWithoutResult(status -> notificationService.compose(
                            NotificationEvent.APPROVAL_PENDING, notice.getKey(), notice.getValue()));
                } catch (RuntimeException e) {
                    log.warn(
                            "APPROVAL_PENDING not composed for approver {} in tenant {}", notice.getKey(), tenantId, e);
                }
            }
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }

    /** {@code Timesheet <project name>, week of <weekStartDate>}. */
    private String title(UUID tenantId, UUID projectId, LocalDate weekStart) {
        String projectName = "project";
        try {
            projectName = projectRepository
                    .findByIdAndTenantIdAndDeletedFalse(projectId, tenantId)
                    .map(Project::getName)
                    .orElse(projectName);
        } catch (RuntimeException e) {
            log.warn("Could not read project {} for a notification: {}", projectId, e.getMessage());
        }
        return "Timesheet " + projectName + ", week of " + weekStart;
    }

    private String name(UUID employeeId) {
        String name = "Employee";
        try {
            EmployeeResponse employee = employeeService.get(employeeId);
            if (employee != null) {
                String first = employee.firstName() != null ? employee.firstName() : "";
                String last = employee.lastName() != null ? employee.lastName() : "";
                String full = (first + " " + last).trim();
                if (!full.isBlank()) {
                    name = full;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not read employee {} for a notification: {}", employeeId, e.getMessage());
        }
        return name;
    }
}
