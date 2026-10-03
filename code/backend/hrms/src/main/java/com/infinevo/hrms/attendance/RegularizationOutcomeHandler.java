package com.infinevo.hrms.attendance;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the outcome of a {@code REGULARIZATION} approval (W-40.4 §4, "The handler").
 *
 * <p>Approved: every non-voided session of the employee and date is voided ({@code REGULARIZED}), one closed session
 * with the requested times is inserted ({@code origin = REGULARIZATION}), the day is derived again through
 * {@link ClockDayService#rederive}, and the request turns {@code APPROVED}. Rejected: the request turns
 * {@code REJECTED}; sessions are untouched. A request that is no longer {@code PENDING} is left alone, so a second
 * dispatch of the same instance changes nothing.
 *
 * <p>The dispatcher may call with no tenant bound (the sweep); the tenant is bound from the instance, as
 * {@code payroll/.../reimbursement/ReimbursementClaimOutcomeHandler.java:58-62,116-120}.
 */
@Component
public class RegularizationOutcomeHandler implements ApprovalOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(RegularizationOutcomeHandler.class);
    private static final String ACTOR = "system";

    private final ApprovalInstanceRepository instanceRepository;
    private final AttendanceRegularizationRepository regularizations;
    private final ClockSessionRepository clockSessions;
    private final ClockDayService clockDayService;

    public RegularizationOutcomeHandler(
            ApprovalInstanceRepository instanceRepository,
            AttendanceRegularizationRepository regularizations,
            ClockSessionRepository clockSessions,
            ClockDayService clockDayService) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.regularizations = Objects.requireNonNull(regularizations, "regularizations must not be null");
        this.clockSessions = Objects.requireNonNull(clockSessions, "clockSessions must not be null");
        this.clockDayService = Objects.requireNonNull(clockDayService, "clockDayService must not be null");
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.REGULARIZATION;
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
            AttendanceRegularization request = regularizations
                    .findByTenantIdAndId(tenantId, instance.getSubjectId())
                    .orElseThrow(
                            () -> new NoSuchElementException("Regularization not found: " + instance.getSubjectId()));

            // 1. The dispatcher may call twice: only a PENDING request is decided.
            if (request.getStatus() != RegularizationStatus.PENDING) {
                log.debug(
                        "Outcome of instance {} ignored: regularization {} is {}",
                        instanceId,
                        request.getId(),
                        request.getStatus());
                return;
            }

            Instant decidedAt = instance.getCompletedAt() != null ? instance.getCompletedAt() : Instant.now();
            StepDecision last = lastDecision(decisions);
            UUID decidedBy = last != null ? last.deciderId() : null;
            String comment = last != null ? last.comment() : null;

            if (approved) {
                // 2. Void every non-voided session of the employee and date, open ones included.
                Instant now = Instant.now();
                List<ClockSession> sessions =
                        clockSessions.findByTenantIdAndEmployeeIdAndAttendanceDateOrderByClockInAtAsc(
                                tenantId, request.getEmployeeId(), request.getAttendanceDate());
                for (ClockSession session : sessions) {
                    if (session.getVoidedAt() == null) {
                        session.voidSession(VoidReason.REGULARIZED, now, ACTOR);
                        clockSessions.saveAndFlush(session);
                    }
                }
                // 3. One closed session with the requested times.
                clockSessions.saveAndFlush(ClockSession.regularized(
                        tenantId,
                        request.getEmployeeId(),
                        request.getAttendanceDate(),
                        request.getRequestedInAt(),
                        request.getRequestedOutAt(),
                        request.getId(),
                        ACTOR));
                // 4. Derive the day again; an ADMIN day is left as the administrator set it.
                ClockDayResponse day = clockDayService.rederive(request.getEmployeeId(), request.getAttendanceDate());
                // 5. The request is decided.
                request.decide(RegularizationStatus.APPROVED, decidedAt, decidedBy, comment);
                regularizations.save(request);
                log.info(
                        "Regularization {} approved for {} in tenant {}; day is {} (written: {})",
                        request.getId(),
                        request.getAttendanceDate(),
                        tenantId,
                        day.status(),
                        day.writtenToAttendance());
            } else {
                request.decide(RegularizationStatus.REJECTED, decidedAt, decidedBy, comment);
                regularizations.save(request);
                log.info("Regularization {} rejected in tenant {}", request.getId(), tenantId);
            }
        } finally {
            if (boundHere) {
                TenantContext.clear();
            }
        }
    }

    /** The last step that carries a decision, or {@code null} when none does. */
    private static StepDecision lastDecision(List<StepDecision> decisions) {
        if (decisions == null) {
            return null;
        }
        for (int i = decisions.size() - 1; i >= 0; i--) {
            StepDecision d = decisions.get(i);
            if (d != null && d.decision() != null) {
                return d;
            }
        }
        return null;
    }
}
