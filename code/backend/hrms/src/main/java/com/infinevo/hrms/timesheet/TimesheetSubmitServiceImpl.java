package com.infinevo.hrms.timesheet;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.ResourceNotFoundException;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.hrms.timesheet.TimesheetNotifier.Pending;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TimesheetSubmitService} (W-42.3).
 *
 * <p>Everything happens in one transaction: the status changes and every approval instance. If any instance cannot
 * start, the exception rolls the whole of it back and the week stays as it was. The notices to approvers are composed
 * after the commit ({@link TimesheetNotifier}), so a mail that fails cannot undo a submit.
 */
@Service
@Transactional
public class TimesheetSubmitServiceImpl implements TimesheetSubmitService {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSubmitServiceImpl.class);

    private final TimesheetRepository timesheetRepository;
    private final TimesheetValidator validator;
    private final ApprovalService approvalService;
    private final ApprovalStepRepository stepRepository;
    private final TimesheetNotifier notifier;
    private final EmployeeService employeeService;

    public TimesheetSubmitServiceImpl(
            TimesheetRepository timesheetRepository,
            TimesheetValidator validator,
            ApprovalService approvalService,
            ApprovalStepRepository stepRepository,
            TimesheetNotifier notifier,
            EmployeeService employeeService) {
        this.timesheetRepository = Objects.requireNonNull(timesheetRepository, "timesheetRepository must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    public TimesheetResponse submit(UUID id) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee();
        Timesheet sheet = owned(tenantId, me.id(), id);

        boolean allDraft = sheet.getProjects().stream().allMatch(p -> p.getStatus() == TimesheetStatus.DRAFT);
        if (sheet.getStatus() != TimesheetStatus.DRAFT || !allDraft) {
            throw new TimesheetConflictException(
                    "Only a draft timesheet can be submitted; this one is " + sheet.getStatus());
        }

        String actor = TimesheetLines.currentActor();
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        sheet.setSubmittedAt(Instant.now());
        sheet.touch(actor);
        for (TimesheetProjectEntry entry : sheet.getProjects()) {
            entry.setStatus(TimesheetStatus.SUBMITTED);
            entry.touch(actor);
        }
        sheet = timesheetRepository.saveAndFlush(sheet);

        List<Pending> pending = startApprovals(tenantId, me.id(), sheet.getProjects());
        notifier.pendingAfterCommit(tenantId, me.id(), sheet.getWeekStartDate(), pending);
        log.info(
                "Timesheet {} submitted in tenant {}: {} project line(s)",
                id,
                tenantId,
                sheet.getProjects().size());
        return TimesheetResponse.from(sheet);
    }

    @Override
    public TimesheetResponse resubmit(UUID id, TimesheetRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse me = currentEmployee();
        Timesheet sheet = owned(tenantId, me.id(), id);
        if (sheet.getStatus() != TimesheetStatus.REJECTED) {
            throw new TimesheetConflictException(
                    "Only a rejected timesheet can be resubmitted; this one is " + sheet.getStatus());
        }
        if (request != null
                && request.weekStartDate() != null
                && !request.weekStartDate().equals(sheet.getWeekStartDate())) {
            throw new ValidationException(
                    "week_start_date",
                    "week_start_date cannot be changed; it must be " + sheet.getWeekStartDate()
                            + " for this timesheet");
        }

        Map<UUID, TimesheetProjectEntry> rejected = new LinkedHashMap<>();
        sheet.getProjects().stream()
                .filter(p -> p.getStatus() == TimesheetStatus.REJECTED)
                .forEach(p -> rejected.put(p.getProjectId(), p));

        // A project that is not a rejected line of this week is a conflict, not something to skip, as legacy did.
        Set<UUID> sent = new HashSet<>();
        if (request != null && request.projects() != null) {
            for (ProjectLine line : request.projects()) {
                if (line != null && line.projectId() != null) {
                    sent.add(line.projectId());
                    if (!rejected.containsKey(line.projectId())) {
                        throw new TimesheetConflictException("Project " + line.projectId()
                                + " is not a rejected project of this timesheet, so it cannot be resubmitted");
                    }
                }
            }
        }

        // The 24 hours a day rule counts the whole week: what the lines not being replaced already hold counts too.
        List<TimesheetProjectEntry> kept = sheet.getProjects().stream()
                .filter(p -> !sent.contains(p.getProjectId()))
                .toList();
        Map<LocalDate, BigDecimal> carried = TimesheetLines.hoursByDate(kept);
        validator.validate(tenantId, me.id(), request, carried);

        String actor = TimesheetLines.currentActor();
        List<TimesheetProjectEntry> resubmitted = new ArrayList<>();
        for (ProjectLine line : request.projects()) {
            TimesheetProjectEntry entry = rejected.get(line.projectId());
            // Old lines go first, in their own flush, so a task that keeps its id does not meet its own unique index.
            entry.getTasks().clear();
            resubmitted.add(entry);
        }
        timesheetRepository.saveAndFlush(sheet);
        for (ProjectLine line : request.projects()) {
            TimesheetProjectEntry entry = rejected.get(line.projectId());
            TimesheetLines.addTasks(entry, line, actor);
            entry.setStatus(TimesheetStatus.SUBMITTED);
            entry.setRejectionReason(null);
            entry.touch(actor);
        }
        sheet.setStatus(TimesheetRollup.of(
                sheet.getProjects().stream()
                        .map(TimesheetProjectEntry::getStatus)
                        .toList(),
                sheet.getStatus()));
        sheet.touch(actor);
        sheet = timesheetRepository.saveAndFlush(sheet);

        List<Pending> pending = startApprovals(tenantId, me.id(), resubmitted);
        notifier.pendingAfterCommit(tenantId, me.id(), sheet.getWeekStartDate(), pending);
        log.info("Timesheet {} resubmitted in tenant {}: {} project line(s)", id, tenantId, resubmitted.size());
        return TimesheetResponse.from(sheet);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /**
     * Starts one approval instance for each line, the line being the subject and its project the item, which is what
     * routes the step to that project's manager (W-42.2). Returns who to tell. When an instance cannot start the
     * conflict is thrown, and the transaction takes the status changes back with it.
     */
    private List<Pending> startApprovals(UUID tenantId, UUID employeeId, List<TimesheetProjectEntry> entries) {
        List<Pending> pending = new ArrayList<>();
        for (TimesheetProjectEntry entry : entries) {
            UUID instanceId;
            try {
                instanceId = approvalService.start(
                        ApprovalFlowType.TIMESHEET,
                        new SubjectRef(SUBJECT_TABLE, entry.getId()),
                        employeeId,
                        List.of(entry.getProjectId().toString()));
            } catch (IllegalStateException e) {
                // No TIMESHEET definition in force, for one.
                throw new TimesheetConflictException(e.getMessage());
            }
            for (ApprovalStep step :
                    stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId)) {
                if (step.getAssigneeEmployeeId() != null) {
                    pending.add(new Pending(step.getAssigneeEmployeeId(), entry.getProjectId()));
                }
            }
        }
        return pending;
    }

    private EmployeeResponse currentEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new PermissionDeniedException(TimesheetServiceImpl.ACTION_SUBMIT));
    }

    private Timesheet owned(UUID tenantId, UUID employeeId, UUID id) {
        return timesheetRepository
                .findByIdAndTenantIdAndEmployeeId(id, tenantId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("No timesheet " + id));
    }
}
