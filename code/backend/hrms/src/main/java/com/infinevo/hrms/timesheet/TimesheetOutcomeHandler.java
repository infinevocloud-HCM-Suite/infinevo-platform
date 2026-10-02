package com.infinevo.hrms.timesheet;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the outcome of a {@code TIMESHEET} approval onto the project line it was about (W-42.3 §4), then rolls the week
 * up and tells the employee. {@code core} calls it once, after the decision commits, with the tenant not necessarily
 * bound (the tenant binding and the idempotence follow {@code ReimbursementClaimOutcomeHandler}).
 *
 * <p>Each instance is about one project line, so each outcome changes one line. A late outcome from an instance that a
 * resubmit has since replaced changes nothing, and neither does a second call for the same outcome.
 */
@Component
public class TimesheetOutcomeHandler implements ApprovalOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(TimesheetOutcomeHandler.class);

    /** The column is {@code VARCHAR(1000)}. */
    static final int MAX_REASON = 1000;

    private final ApprovalInstanceRepository instanceRepository;
    private final TimesheetProjectEntryRepository entryRepository;
    private final TimesheetNotifier notifier;

    public TimesheetOutcomeHandler(
            ApprovalInstanceRepository instanceRepository,
            TimesheetProjectEntryRepository entryRepository,
            TimesheetNotifier notifier) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.entryRepository = Objects.requireNonNull(entryRepository, "entryRepository must not be null");
        this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.TIMESHEET;
    }

    @Override
    @Transactional
    public void onApproved(UUID instanceId, List<StepDecision> decisions) {
        apply(instanceId, decisions, true);
    }

    @Override
    @Transactional
    public void onRejected(UUID instanceId, List<StepDecision> decisions) {
        apply(instanceId, decisions, false);
    }

    private void apply(UUID instanceId, List<StepDecision> decisions, boolean approved) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        ApprovalInstance instance = instanceRepository
                .findById(instanceId)
                .orElseThrow(() -> new NoSuchElementException("Approval instance not found: " + instanceId));

        boolean boundHere = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(instance.getTenantId());
            boundHere = true;
        }
        try {
            UUID tenantId = instance.getTenantId();
            TimesheetProjectEntry entry = entryRepository
                    .findByTenantIdAndId(tenantId, instance.getSubjectId())
                    .orElseThrow(() -> new NoSuchElementException(
                            "Timesheet project entry not found: " + instance.getSubjectId()));

            if (entry.getStatus() != TimesheetStatus.SUBMITTED) {
                log.debug(
                        "Outcome of instance {} ignored: line {} is {}", instanceId, entry.getId(), entry.getStatus());
                return;
            }
            if (!isNewest(tenantId, instance)) {
                log.info(
                        "Outcome of instance {} ignored: line {} has since been resubmitted",
                        instanceId,
                        entry.getId());
                return;
            }

            entry.setStatus(approved ? TimesheetStatus.APPROVED : TimesheetStatus.REJECTED);
            entry.setRejectionReason(approved ? null : reasonOf(decisions));
            entry.touch(TimesheetRow.ACTOR_SYSTEM);

            Timesheet sheet = entry.getTimesheet();
            sheet.setStatus(TimesheetRollup.of(
                    sheet.getProjects().stream()
                            .map(TimesheetProjectEntry::getStatus)
                            .toList(),
                    sheet.getStatus()));
            sheet.touch(TimesheetRow.ACTOR_SYSTEM);
            entryRepository.save(entry);

            notifier.decided(tenantId, sheet.getEmployeeId(), entry.getProjectId(), sheet.getWeekStartDate(), approved);
            log.info(
                    "Timesheet line {} {} for week {} in tenant {}; week is now {}",
                    entry.getId(),
                    approved ? "approved" : "rejected",
                    sheet.getWeekStartDate(),
                    tenantId,
                    sheet.getStatus());
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }

    /** Whether this is the latest instance started for the line: an older one's outcome is stale. */
    private boolean isNewest(UUID tenantId, ApprovalInstance instance) {
        return instanceRepository
                .findByTenantIdAndSubjectTableAndSubjectId(
                        tenantId, TimesheetSubmitService.SUBJECT_TABLE, instance.getSubjectId())
                .stream()
                .max(Comparator.comparing(
                                ApprovalInstance::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                ApprovalInstance::getStartedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(newest -> newest.getId().equals(instance.getId()))
                .orElse(true);
    }

    /** The rejecting step's comment, cut to the column's length; none when it gave none. */
    private static String reasonOf(List<StepDecision> decisions) {
        if (decisions == null) {
            return null;
        }
        return decisions.stream()
                .filter(d -> "REJECTED".equals(d.decision())
                        && d.comment() != null
                        && !d.comment().isBlank())
                .map(StepDecision::comment)
                .findFirst()
                .map(c -> c.length() > MAX_REASON ? c.substring(0, MAX_REASON) : c)
                .orElse(null);
    }
}
