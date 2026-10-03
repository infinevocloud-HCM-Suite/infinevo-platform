package com.infinevo.hrms.timesheet;

import static org.mockito.Mockito.reset;

import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.hrms.project.AssignmentRequest;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.hrms.project.Priority;
import com.infinevo.hrms.project.ProjectRequest;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared set-up for the W-42.3 tests, on top of {@link TimesheetItSupport}, with the approval engine real: a submit
 * starts real instances and a decision runs the real outcome handler. Only the notification service and the reporting
 * line lookup are stand-ins (the test application's mocks).
 *
 * <p>The pool is 6, not the tests' default of 2: a decision holds its own connection while the outcome handler runs in a
 * second, and the test application's employee stand-in opens a third.
 *
 * <p>Two projects, {@code A} managed by {@code m1} and {@code B} by {@code m2}; {@code empA} is assigned to both and
 * submits. The tokens: {@code subM1} and {@code subM2} hold {@code hrms.timesheet.approve} and
 * {@code core.approval.decide}, as {@code manager} will once W-40.2 grants the second; until then each test grants it.
 */
@SpringBootTest(classes = HrmsTestApp.class, properties = "spring.datasource.hikari.maximum-pool-size=6")
@AutoConfigureMockMvc
abstract class TimesheetApprovalSupport extends TimesheetItSupport {

    protected static final String SUBJECT_TABLE = TimesheetSubmitService.SUBJECT_TABLE;

    @Autowired
    protected TimesheetSubmitService submitService;

    @Autowired
    protected ApprovalService approvalService;

    @Autowired
    protected ApprovalInstanceRepository instances;

    @Autowired
    protected ApprovalStepRepository steps;

    @Autowired
    protected TimesheetRepository timesheetRepository;

    @Autowired
    protected TimesheetProjectEntryRepository entryRepository;

    @Autowired
    protected TimesheetOutcomeHandler outcomeHandler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    protected NotificationService notifications;

    @Autowired
    protected ReportingLineService reportingLineService;

    protected UUID m1;
    protected UUID m2;
    protected UUID subM1;
    protected UUID subM2;
    protected UUID projectA;
    protected UUID projectB;
    protected UUID taskA;
    protected UUID taskB;

    @BeforeEach
    void seedApproval() throws Exception {
        reset(notifications, reportingLineService);
        m1 = HrmsProjectTestSchema.insertEmployee(tenant, "AM1-" + UUID.randomUUID());
        m2 = HrmsProjectTestSchema.insertEmployee(tenant, "AM2-" + UUID.randomUUID());
        subM1 = UUID.randomUUID();
        subM2 = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subM1, "hrms.timesheet.approve", "core.approval.decide");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subM2, "hrms.timesheet.approve", "core.approval.decide");

        TenantContext.set(tenant);
        try {
            projectA = managedProject("Approval A " + UUID.randomUUID(), m1);
            projectB = managedProject("Approval B " + UUID.randomUUID(), m2);
            taskA = task(projectA, "Task A");
            taskB = task(projectB, "Task B");
            assignments.assign(projectA, new AssignmentRequest(empA, LocalDate.of(2026, 1, 1)));
            assignments.assign(projectB, new AssignmentRequest(empA, LocalDate.of(2026, 1, 1)));
        } finally {
            TenantContext.clear();
        }
        actAs(empA);
        inTenant();
    }

    @AfterEach
    void forgetMocks() {
        reset(notifications, reportingLineService);
    }

    protected UUID managedProject(String name, UUID manager) {
        return projects.create(new ProjectRequest(
                        name,
                        "Dev",
                        "Approval test",
                        LocalDate.of(2026, 1, 1),
                        null,
                        Priority.MEDIUM,
                        ProjectStatus.STARTED,
                        BigDecimal.valueOf(1000),
                        manager))
                .id();
    }

    /** A draft with 4 hours on project A and 3 on project B, on the Monday of the week, as the logged-in employee. */
    protected UUID draftWeek(LocalDate week) {
        return timesheets
                .create(new TimesheetRequest(
                        week,
                        List.of(
                                new TimesheetRequest.ProjectLine(
                                        projectA,
                                        List.of(new TimesheetRequest.TaskLine(taskA, List.of(day(week, "4"))))),
                                new TimesheetRequest.ProjectLine(
                                        projectB,
                                        List.of(new TimesheetRequest.TaskLine(taskB, List.of(day(week, "3"))))))))
                .id();
    }

    protected TimesheetStatus weekStatus(UUID id) throws Exception {
        return TimesheetStatus.valueOf(string("SELECT status FROM hrms.timesheet WHERE id = ?", id));
    }

    protected TimesheetStatus lineStatus(UUID sheet, UUID project) throws Exception {
        return TimesheetStatus.valueOf(string(
                "SELECT status FROM hrms.timesheet_project_entry WHERE timesheet_id = ? AND project_id = ?",
                sheet,
                project));
    }

    protected String lineReason(UUID sheet, UUID project) throws Exception {
        return string(
                "SELECT rejection_reason FROM hrms.timesheet_project_entry WHERE timesheet_id = ? AND project_id = ?",
                sheet,
                project);
    }

    protected UUID entryId(UUID sheet, UUID project) throws Exception {
        return HrmsProjectTestSchema.uuid(
                "SELECT id FROM hrms.timesheet_project_entry WHERE timesheet_id = ? AND project_id = ?",
                sheet,
                project);
    }

    /** Every approval instance started for the line, oldest first. */
    protected List<ApprovalInstance> instancesOf(UUID entry) {
        // The approval repositories take no transaction of their own, and the tenant binding needs one.
        return new TransactionTemplate(transactionManager)
                .execute(status ->
                        instances.findByTenantIdAndSubjectTableAndSubjectId(tenant, SUBJECT_TABLE, entry).stream()
                                .sorted(Comparator.comparing(ApprovalInstance::getCreatedAt))
                                .toList());
    }

    /** The single step of the newest instance for the line. */
    protected ApprovalStep newestStep(UUID entry) {
        List<ApprovalInstance> all = instancesOf(entry);
        ApprovalInstance newest = all.get(all.size() - 1);
        List<ApprovalStep> found = new TransactionTemplate(transactionManager)
                .execute(status -> steps.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenant, newest.getId()));
        if (found.size() != 1) {
            throw new AssertionError("expected one step, found " + found.size());
        }
        return found.get(0);
    }

    protected void decide(UUID decider, ApprovalStep step, ApprovalDecision decision, String comment) {
        actAs(decider);
        inTenant();
        approvalService.decide(step.getId(), new ApprovalDecideRequest(decision, comment));
    }

    private static String string(String sql, Object... params) throws Exception {
        try (var conn = HrmsProjectTestSchema.migrationConnection();
                var ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
