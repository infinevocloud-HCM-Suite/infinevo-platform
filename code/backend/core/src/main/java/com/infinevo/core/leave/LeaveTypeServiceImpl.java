package com.infinevo.core.leave;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link LeaveTypeService} (W-16.1, spec section 4).
 */
@Service
@Transactional
public class LeaveTypeServiceImpl implements LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeavePolicyEligibilityRepository eligibilityRepository;
    private final LeaveAllocationService leaveAllocationService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LeaveTypeServiceImpl(
            LeaveTypeRepository leaveTypeRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeavePolicyEligibilityRepository eligibilityRepository) {
        this(leaveTypeRepository, leavePolicyRepository, eligibilityRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveTypeServiceImpl(
            LeaveTypeRepository leaveTypeRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeavePolicyEligibilityRepository eligibilityRepository,
            LeaveAllocationService leaveAllocationService) {
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
        this.eligibilityRepository =
                Objects.requireNonNull(eligibilityRepository, "eligibilityRepository must not be null");
        this.leaveAllocationService = leaveAllocationService;
    }

    @Override
    public LeaveTypeResponse createLeaveType(UUID tenantId, LeaveTypeRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (request.unit() != LeaveUnit.DAYS) {
            throw new IllegalArgumentException("Unsupported leave unit. Only day-based leave (DAYS) is supported.");
        }
        requireTypeFields(request);
        String code = request.code().trim();
        if (leaveTypeRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("Leave type with code '" + code + "' already exists in this tenant");
        }

        boolean active = request.isActive() == null || request.isActive();
        LeaveType type = new LeaveType(
                tenantId,
                code,
                request.name().trim(),
                Boolean.TRUE.equals(request.isPaid()),
                request.unit(),
                Boolean.TRUE.equals(request.allowHalfDay()),
                request.validFrom(),
                request.validTo(),
                active);

        LeaveType savedType = leaveTypeRepository.save(type);

        LeavePolicyResponse policyResponse = null;
        if (request.policy() != null) {
            policyResponse = configurePolicy(tenantId, savedType.getId(), request.policy());
        }

        return LeaveTypeResponse.from(savedType, policyResponse);
    }

    @Override
    public LeaveTypeResponse updateLeaveType(UUID tenantId, UUID id, LeaveTypeRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(request, "request must not be null");

        LeaveType type = leaveTypeRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found with ID: " + id));

        requireTypeFields(request);
        String newCode = request.code().trim();
        if (!type.getCode().equalsIgnoreCase(newCode)
                && leaveTypeRepository.existsByTenantIdAndCode(tenantId, newCode)) {
            throw new IllegalArgumentException("Leave type with code '" + newCode + "' already exists in this tenant");
        }

        type.setCode(newCode);
        type.setName(request.name().trim());
        type.setPaid(Boolean.TRUE.equals(request.isPaid()));
        type.setUnit(request.unit() != null ? request.unit() : LeaveUnit.DAYS);
        type.setAllowHalfDay(Boolean.TRUE.equals(request.allowHalfDay()));
        type.setValidFrom(request.validFrom());
        type.setValidTo(request.validTo());
        if (request.isActive() != null) {
            type.setActive(request.isActive());
        }

        LeaveType updated = leaveTypeRepository.save(type);
        LeavePolicyResponse policyResponse =
                getEffectivePolicy(tenantId, id, LocalDate.now(ZoneOffset.UTC)).orElse(null);

        return LeaveTypeResponse.from(updated, policyResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getLeaveTypes(UUID tenantId, LocalDate activeOn) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        LocalDate evaluationDate = activeOn != null ? activeOn : LocalDate.now(ZoneOffset.UTC);
        List<LeaveType> types;
        if (activeOn != null) {
            types = leaveTypeRepository.findByTenantIdAndIsActiveTrueOrderByCodeAsc(tenantId).stream()
                    .filter(t -> !t.getValidFrom().isAfter(activeOn)
                            && (t.getValidTo() == null || !t.getValidTo().isBefore(activeOn)))
                    .toList();
        } else {
            types = leaveTypeRepository.findByTenantIdOrderByCodeAsc(tenantId);
        }

        List<LeaveTypeResponse> result = new ArrayList<>(types.size());
        for (LeaveType type : types) {
            LeavePolicyResponse policy =
                    getEffectivePolicy(tenantId, type.getId(), evaluationDate).orElse(null);
            result.add(LeaveTypeResponse.from(type, policy));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeaveTypeResponse> getLeaveType(UUID tenantId, UUID id, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        LocalDate evaluationDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        return leaveTypeRepository.findByTenantIdAndId(tenantId, id).map(type -> {
            LeavePolicyResponse policy =
                    getEffectivePolicy(tenantId, type.getId(), evaluationDate).orElse(null);
            return LeaveTypeResponse.from(type, policy);
        });
    }

    /** A missing code, name or start date is the caller's mistake: refuse it before it reaches the database. */
    private static void requireTypeFields(LeaveTypeRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Leave type code is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Leave type name is required");
        }
        if (request.validFrom() == null) {
            throw new IllegalArgumentException("Leave type validFrom is required");
        }
    }

    @Override
    public LeavePolicyResponse configurePolicy(UUID tenantId, UUID leaveTypeId, LeavePolicyRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        // Validate policy request
        LeavePolicyValidator.validate(request);

        // Verify leave type exists
        leaveTypeRepository
                .findByTenantIdAndId(tenantId, leaveTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found with ID: " + leaveTypeId));

        LeavePolicy policy = new LeavePolicy();
        policy.setTenantId(tenantId);
        policy.setLeaveTypeId(leaveTypeId);
        policy.setAnnualDays(request.annualDays());
        policy.setAccrualEnabled(request.accrualEnabled());
        policy.setAccrualFrequency(request.accrualFrequency());
        policy.setAccrualUnits(request.accrualUnits());
        policy.setResetEnabled(request.resetEnabled());
        policy.setResetFrequency(request.resetFrequency());
        policy.setCarryForwardEnabled(request.carryForwardEnabled());
        policy.setCarryForwardCap(request.carryForwardCap());
        policy.setCarryForwardExpiresAfterMonths(request.carryForwardExpiresAfterMonths());
        policy.setRequiresDocument(Boolean.TRUE.equals(request.requiresDocument()));
        policy.setPastBookingLimitDays(request.pastBookingLimitDays());
        policy.setFutureBookingLimitDays(request.futureBookingLimitDays());
        policy.setIncludeWeekend(request.includeWeekend());
        policy.setIncludeHoliday(request.includeHoliday());
        policy.setExceedBalanceMode(request.exceedBalanceMode());
        policy.setExceedBalanceLimitDays(request.exceedBalanceLimitDays());
        policy.setProRateEnabled(request.proRateEnabled());
        policy.setMaxDaysPerApplication(request.maxDaysPerApplication());
        policy.setGender(
                request.gender() != null && !request.gender().isBlank()
                        ? request.gender().trim()
                        : null);
        // Refuse a policy date before the current leave year (Spec § 6)
        int startMonth = LeaveDateUtils.getTenantLeaveYearStartMonth(jdbcTemplate, tenantId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate currentYearStart = LeaveDateUtils.getCurrentLeaveYearStartDate(today, startMonth);

        LocalDate effectiveDate = request.effectiveFrom() != null ? request.effectiveFrom() : currentYearStart;

        if (effectiveDate.isBefore(currentYearStart)) {
            throw new IllegalArgumentException(
                    "Policy effectiveFrom date cannot be before the current leave year start date (" + currentYearStart
                            + "): " + effectiveDate);
        }

        policy.setEffectiveFrom(effectiveDate);

        List<OverdrawnEmployee> overdrawn = List.of();
        if (leaveAllocationService != null && request.annualDays() != null) {
            overdrawn = leaveAllocationService.previewMidYearPolicyImpact(
                    tenantId, leaveTypeId, request.annualDays(), request.accrualEnabled(), effectiveDate);
        }

        LeavePolicy savedPolicy = leavePolicyRepository.save(policy);

        if (leaveAllocationService != null) {
            leaveAllocationService.applyMidYearPolicyChange(tenantId, leaveTypeId, savedPolicy, effectiveDate);
        }

        List<LeavePolicyEligibility> savedEligibilities = new ArrayList<>();
        if (request.eligibility() != null && !request.eligibility().isEmpty()) {
            for (LeavePolicyEligibilityRequest elReq : request.eligibility()) {
                LeavePolicyEligibility eligibility = new LeavePolicyEligibility(
                        tenantId, savedPolicy.getId(), elReq.dimension().trim().toLowerCase(), elReq.valueId());
                savedEligibilities.add(eligibilityRepository.save(eligibility));
            }
        }

        return LeavePolicyResponse.from(savedPolicy, savedEligibilities, overdrawn);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeavePolicyResponse> getEffectivePolicy(UUID tenantId, UUID leaveTypeId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");

        LocalDate evaluationDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        return leavePolicyRepository
                .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                        tenantId, leaveTypeId, evaluationDate)
                .map(policy -> {
                    List<LeavePolicyEligibility> eligibilities =
                            eligibilityRepository.findByTenantIdAndPolicyId(tenantId, policy.getId());
                    return LeavePolicyResponse.from(policy, eligibilities);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdrawnEmployee> previewPolicyChange(
            UUID tenantId, UUID leaveTypeId, BigDecimal newAnnualDays, LocalDate asOf) {
        return previewPolicyChange(tenantId, leaveTypeId, newAnnualDays, null, asOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdrawnEmployee> previewPolicyChange(
            UUID tenantId, UUID leaveTypeId, BigDecimal newAnnualDays, Boolean newAccrualEnabled, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        Objects.requireNonNull(newAnnualDays, "newAnnualDays must not be null");

        leaveTypeRepository
                .findByTenantIdAndId(tenantId, leaveTypeId)
                .orElseThrow(() -> new NoSuchElementException("Leave type not found with ID: " + leaveTypeId));

        LocalDate evaluationDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        if (leaveAllocationService != null) {
            return leaveAllocationService.previewMidYearPolicyImpact(
                    tenantId, leaveTypeId, newAnnualDays, newAccrualEnabled, evaluationDate);
        }
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdrawnEmployee> previewPolicyChange(UUID leaveTypeId, BigDecimal newAnnualDays, LocalDate asOf) {
        return previewPolicyChange(TenantContext.require(), leaveTypeId, newAnnualDays, asOf);
    }

    @Override
    public LeaveTypeResponse createLeaveType(LeaveTypeRequest request) {
        return createLeaveType(TenantContext.require(), request);
    }

    @Override
    public LeaveTypeResponse updateLeaveType(UUID id, LeaveTypeRequest request) {
        return updateLeaveType(TenantContext.require(), id, request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveTypeResponse> getLeaveTypes(LocalDate activeOn) {
        return getLeaveTypes(TenantContext.require(), activeOn);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeaveTypeResponse> getLeaveType(UUID id, LocalDate asOf) {
        return getLeaveType(TenantContext.require(), id, asOf);
    }

    @Override
    public LeavePolicyResponse configurePolicy(UUID leaveTypeId, LeavePolicyRequest request) {
        return configurePolicy(TenantContext.require(), leaveTypeId, request);
    }

    @Override
    public LeavePolicyResponse setPolicy(UUID leaveTypeId, LeavePolicyRequest request) {
        return configurePolicy(TenantContext.require(), leaveTypeId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LeavePolicyResponse> getEffectivePolicy(UUID leaveTypeId, LocalDate asOf) {
        return getEffectivePolicy(TenantContext.require(), leaveTypeId, asOf);
    }
}
