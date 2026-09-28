package com.infinevo.payroll.fbp;

import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link FbpPlanService} (W-27.1).
 */
@Service
@Transactional
public class FbpPlanServiceImpl implements FbpPlanService {

    private static final int MAX_ACTOR_LEN = 100;

    private final FbpPlanRepository fbpPlanRepository;
    private final EarningRepository earningRepository;
    private final ReimbursementRepository reimbursementRepository;

    public FbpPlanServiceImpl(
            FbpPlanRepository fbpPlanRepository,
            EarningRepository earningRepository,
            ReimbursementRepository reimbursementRepository) {
        this.fbpPlanRepository = Objects.requireNonNull(fbpPlanRepository, "fbpPlanRepository must not be null");
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.reimbursementRepository =
                Objects.requireNonNull(reimbursementRepository, "reimbursementRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public FbpPlanResponse get() {
        UUID tenantId = TenantContext.require();
        return fbpPlanRepository
                .findByTenantId(tenantId)
                .map(FbpPlanResponse::fromEntity)
                .orElseGet(FbpPlanResponse::defaults);
    }

    @Override
    public FbpPlanResponse upsert(FbpPlanRequest request) {
        validateRequest(request);

        UUID tenantId = TenantContext.require();
        String actor = currentActor();

        FbpPlan plan = fbpPlanRepository.findByTenantId(tenantId).orElseGet(() -> new FbpPlan(tenantId, actor));

        boolean enabled = Boolean.TRUE.equals(request.isEnabled());
        plan.setEnabled(enabled);
        plan.setWindowOpensOn(request.windowOpensOn());
        plan.setWindowClosesOn(request.windowClosesOn());

        if (request.notifyOnRelease() != null) {
            plan.setNotifyOnRelease(request.notifyOnRelease());
        }
        if (request.notifyOnLock() != null) {
            plan.setNotifyOnLock(request.notifyOnLock());
        }
        if (request.reminderDaysBeforeClose() != null) {
            plan.setReminderDaysBeforeClose(request.reminderDaysBeforeClose());
        }

        plan.setUpdatedBy(actor);
        FbpPlan saved = fbpPlanRepository.save(plan);
        return FbpPlanResponse.fromEntity(saved);
    }

    @Override
    public FbpPlanResponse lock() {
        UUID tenantId = TenantContext.require();
        String actor = currentActor();

        FbpPlan plan = fbpPlanRepository.findByTenantId(tenantId).orElseGet(() -> new FbpPlan(tenantId, actor));

        plan.setLocked(true);
        plan.setLockedAt(Instant.now());
        plan.setUpdatedBy(actor);

        FbpPlan saved = fbpPlanRepository.save(plan);
        return FbpPlanResponse.fromEntity(saved);
    }

    @Override
    public FbpPlanResponse unlock() {
        UUID tenantId = TenantContext.require();
        String actor = currentActor();

        FbpPlan plan = fbpPlanRepository.findByTenantId(tenantId).orElseGet(() -> new FbpPlan(tenantId, actor));

        plan.setLocked(false);
        plan.setLockedAt(null);
        plan.setUpdatedBy(actor);

        FbpPlan saved = fbpPlanRepository.save(plan);
        return FbpPlanResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FbpComponentResponse> components() {
        UUID tenantId = TenantContext.require();

        List<Earning> earnings =
                earningRepository.findAllByTenantIdAndActiveAndDeletedFalseAndFbpComponentTrue(tenantId, true);
        List<Reimbursement> reimbursements =
                reimbursementRepository.findAllByTenantIdAndActiveAndDeletedFalseAndFbpComponentTrue(tenantId, true);

        List<FbpComponentResponse> result = new ArrayList<>(earnings.size() + reimbursements.size());
        for (Earning e : earnings) {
            result.add(new FbpComponentResponse("EARNING", e.getId(), e.getCode(), e.getName(), e.getMaxLimit()));
        }
        for (Reimbursement r : reimbursements) {
            result.add(new FbpComponentResponse("REIMBURSEMENT", r.getId(), r.getCode(), r.getName(), r.getMaxLimit()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWindowOpen(LocalDate today) {
        if (today == null) {
            return false;
        }
        UUID tenantId = TenantContext.require();
        return fbpPlanRepository
                .findByTenantId(tenantId)
                .map(plan -> isPlanWindowOpen(plan, today))
                .orElse(false);
    }

    public static boolean isPlanWindowOpen(FbpPlan plan, LocalDate today) {
        if (plan == null || today == null) {
            return false;
        }
        if (!plan.isEnabled() || plan.isLocked()) {
            return false;
        }
        LocalDate opens = plan.getWindowOpensOn();
        LocalDate closes = plan.getWindowClosesOn();
        if (opens == null || closes == null) {
            return false;
        }
        return !today.isBefore(opens) && !today.isAfter(closes);
    }

    public static void validateRequest(FbpPlanRequest request) {
        if (request == null) {
            throw new FbpValidationException("request", "Request body must not be null");
        }

        Map<String, String> errors = new LinkedHashMap<>();
        boolean enabled = Boolean.TRUE.equals(request.isEnabled());

        if (enabled) {
            if (request.windowOpensOn() == null) {
                errors.put("windowOpensOn", "Window open date is required when FBP is enabled");
            }
            if (request.windowClosesOn() == null) {
                errors.put("windowClosesOn", "Window close date is required when FBP is enabled");
            }
        }

        if (request.windowOpensOn() != null && request.windowClosesOn() != null) {
            if (request.windowClosesOn().isBefore(request.windowOpensOn())) {
                errors.put("windowClosesOn", "Window close date must be on or after window open date");
            }
        }

        if (request.reminderDaysBeforeClose() != null) {
            Set<Integer> seen = new HashSet<>();
            for (Integer day : request.reminderDaysBeforeClose()) {
                if (day == null || day < 1 || day > 60) {
                    errors.put("reminderDaysBeforeClose", "Every reminder day must be between 1 and 60");
                    break;
                }
                if (!seen.add(day)) {
                    errors.put("reminderDaysBeforeClose", "Reminder days must not contain duplicates");
                    break;
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new FbpValidationException(errors);
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            String name = auth.getName();
            return name.length() > MAX_ACTOR_LEN ? name.substring(0, MAX_ACTOR_LEN) : name;
        }
        return FbpPlan.ACTOR_SYSTEM;
    }
}
