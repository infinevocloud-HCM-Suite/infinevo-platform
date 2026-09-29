package com.infinevo.payroll.statutory.pt;

import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link ProfessionalTaxService} (W-31.2).
 */
@Service
public class ProfessionalTaxServiceImpl implements ProfessionalTaxService {

    private final WorkLocationService workLocationService;
    private final ReferenceStateRepository referenceStateRepository;
    private final PtStateRepository ptStateRepository;
    private final PtSlabRepository ptSlabRepository;
    private final OrgPtOverrideRepository orgPtOverrideRepository;
    private final OrgPtOverrideSlabRepository orgPtOverrideSlabRepository;
    private final PtHistoryRepository ptHistoryRepository;

    public ProfessionalTaxServiceImpl(
            WorkLocationService workLocationService,
            ReferenceStateRepository referenceStateRepository,
            PtStateRepository ptStateRepository,
            PtSlabRepository ptSlabRepository,
            OrgPtOverrideRepository orgPtOverrideRepository,
            OrgPtOverrideSlabRepository orgPtOverrideSlabRepository,
            PtHistoryRepository ptHistoryRepository) {
        this.workLocationService = Objects.requireNonNull(workLocationService, "workLocationService must not be null");
        this.referenceStateRepository =
                Objects.requireNonNull(referenceStateRepository, "referenceStateRepository must not be null");
        this.ptStateRepository = Objects.requireNonNull(ptStateRepository, "ptStateRepository must not be null");
        this.ptSlabRepository = Objects.requireNonNull(ptSlabRepository, "ptSlabRepository must not be null");
        this.orgPtOverrideRepository =
                Objects.requireNonNull(orgPtOverrideRepository, "orgPtOverrideRepository must not be null");
        this.orgPtOverrideSlabRepository =
                Objects.requireNonNull(orgPtOverrideSlabRepository, "orgPtOverrideSlabRepository must not be null");
        this.ptHistoryRepository = Objects.requireNonNull(ptHistoryRepository, "ptHistoryRepository must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtStateResponse> statesForTenant() {
        UUID tenantId = TenantContext.require();
        Set<String> activeStates = getActiveWorkLocationStateCodes();
        List<PtStateResponse> result = new ArrayList<>();
        LocalDate today = currentDate();

        for (String stateCode : activeStates) {
            result.add(buildStateResponse(tenantId, stateCode, today));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PtStateResponse getStateForTenant(String stateCode) {
        UUID tenantId = TenantContext.require();
        String normCode = normalizeStateCode(stateCode);
        Set<String> activeStates = getActiveWorkLocationStateCodes();
        if (!activeStates.contains(normCode)) {
            throw new PtNotFoundException("No active work location found in state " + stateCode);
        }
        return buildStateResponse(tenantId, normCode, currentDate());
    }

    @Override
    @Transactional
    public PtStateResponse setOverride(String stateCode, PtOverrideRequest request) {
        UUID tenantId = TenantContext.require();
        String normCode = normalizeStateCode(stateCode);

        if (!referenceStateRepository.existsById(normCode)) {
            throw new PtValidationException("Unknown state code: " + stateCode);
        }
        if (request == null) {
            throw new PtValidationException("Request body must not be null");
        }
        if (request.effectiveFrom() == null) {
            throw new PtValidationException("effective_from must not be null");
        }
        if (request.slabs() == null || request.slabs().isEmpty()) {
            throw new PtValidationException("At least one slab is required");
        }

        validateSlabs(request.slabs());

        String actorLabel = resolveActorLabel();
        UUID actorUserId = resolveActorUserId();

        Optional<OrgPtOverride> existingOpt = orgPtOverrideRepository.findByTenantIdAndStateCode(tenantId, normCode);
        List<PtSlabDto> beforeSlabs;
        OrgPtOverride override;

        if (existingOpt.isPresent()) {
            OrgPtOverride existing = existingOpt.get();
            beforeSlabs =
                    orgPtOverrideSlabRepository
                            .findByTenantIdAndOverrideIdOrderBySortOrderAsc(tenantId, existing.getId())
                            .stream()
                            .map(OrgPtOverrideSlab::toDto)
                            .toList();
            orgPtOverrideSlabRepository.deleteByTenantIdAndOverrideId(tenantId, existing.getId());
            existing.update(request.registrationNumber(), request.effectiveFrom(), actorLabel);
            override = orgPtOverrideRepository.save(existing);
        } else {
            beforeSlabs = ptSlabRepository.inForce(normCode, request.effectiveFrom()).stream()
                    .map(PtSlab::toDto)
                    .toList();
            override = new OrgPtOverride(
                    tenantId, normCode, request.registrationNumber(), request.effectiveFrom(), actorLabel);
            override = orgPtOverrideRepository.save(override);
        }

        List<PtSlabDto> afterSlabs = new ArrayList<>();
        short sortOrder = 1;
        for (PtSlabDto slabDto : request.slabs()) {
            OrgPtOverrideSlab slab = new OrgPtOverrideSlab(
                    tenantId,
                    override.getId(),
                    slabDto.fromAmount(),
                    slabDto.toAmount(),
                    slabDto.amount(),
                    slabDto.isFemaleExempt(),
                    PtSlabDto.formatMonths(slabDto.deductionMonths()),
                    sortOrder++,
                    actorLabel);
            orgPtOverrideSlabRepository.save(slab);
            afterSlabs.add(slabDto);
        }

        PtHistory history = new PtHistory(
                tenantId, normCode, PtHistoryOperation.OVERRIDE_SET, beforeSlabs, afterSlabs, actorUserId);
        ptHistoryRepository.save(history);

        String stateName = referenceStateRepository
                .findByCode(normCode)
                .map(ReferenceState::getName)
                .orElse(normCode);

        return new PtStateResponse(
                normCode,
                stateName,
                PtSource.OVERRIDE,
                override.getRegistrationNumber(),
                override.getEffectiveFrom(),
                afterSlabs);
    }

    @Override
    @Transactional
    public void resetOverride(String stateCode) {
        UUID tenantId = TenantContext.require();
        String normCode = normalizeStateCode(stateCode);

        OrgPtOverride override = orgPtOverrideRepository
                .findByTenantIdAndStateCode(tenantId, normCode)
                .orElseThrow(() -> new PtNotFoundException("No override found for state " + stateCode));

        List<PtSlabDto> beforeSlabs =
                orgPtOverrideSlabRepository
                        .findByTenantIdAndOverrideIdOrderBySortOrderAsc(tenantId, override.getId())
                        .stream()
                        .map(OrgPtOverrideSlab::toDto)
                        .toList();

        orgPtOverrideSlabRepository.deleteByTenantIdAndOverrideId(tenantId, override.getId());
        orgPtOverrideRepository.delete(override);

        PtHistory history = new PtHistory(
                tenantId, normCode, PtHistoryOperation.OVERRIDE_RESET, beforeSlabs, null, resolveActorUserId());
        ptHistoryRepository.save(history);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PtHistoryResponse> getHistory(String stateCode) {
        UUID tenantId = TenantContext.require();
        String normCode = normalizeStateCode(stateCode);
        return ptHistoryRepository.findByTenantIdAndStateCodeOrderByChangedAtDesc(tenantId, normCode).stream()
                .map(PtHistory::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Money resolve(UUID tenantId, String stateCode, Money gross, String gender, LocalDate periodEnd) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (stateCode == null || stateCode.isBlank() || gross == null || gross.isNegative()) {
            return Money.ZERO;
        }

        String normCode = normalizeStateCode(stateCode);
        Optional<OrgPtOverride> overrideOpt = orgPtOverrideRepository.findByTenantIdAndStateCode(tenantId, normCode);

        if (overrideOpt.isPresent() && !overrideOpt.get().getEffectiveFrom().isAfter(periodEnd)) {
            List<OrgPtOverrideSlab> overrideSlabs =
                    orgPtOverrideSlabRepository.findByTenantIdAndOverrideIdOrderBySortOrderAsc(
                            tenantId, overrideOpt.get().getId());
            List<PtSlabDto> dtoList =
                    overrideSlabs.stream().map(OrgPtOverrideSlab::toDto).toList();
            return PtSlabMatcher.match(gross, gender, periodEnd, dtoList);
        }

        List<PtSlab> referenceSlabs = ptSlabRepository.inForce(normCode, periodEnd);
        List<PtSlabDto> dtoList = referenceSlabs.stream().map(PtSlab::toDto).toList();
        return PtSlabMatcher.match(gross, gender, periodEnd, dtoList);
    }

    private PtStateResponse buildStateResponse(UUID tenantId, String stateCode, LocalDate today) {
        String stateName = referenceStateRepository
                .findByCode(stateCode)
                .map(ReferenceState::getName)
                .orElse(stateCode);

        Optional<OrgPtOverride> overrideOpt = orgPtOverrideRepository.findByTenantIdAndStateCode(tenantId, stateCode);
        if (overrideOpt.isPresent()) {
            OrgPtOverride override = overrideOpt.get();
            List<PtSlabDto> slabs =
                    orgPtOverrideSlabRepository
                            .findByTenantIdAndOverrideIdOrderBySortOrderAsc(tenantId, override.getId())
                            .stream()
                            .map(OrgPtOverrideSlab::toDto)
                            .toList();
            return new PtStateResponse(
                    stateCode,
                    stateName,
                    PtSource.OVERRIDE,
                    override.getRegistrationNumber(),
                    override.getEffectiveFrom(),
                    slabs);
        }

        Optional<PtState> ptStateOpt = ptStateRepository.findByStateCode(stateCode);
        if (ptStateOpt.isPresent() && ptStateOpt.get().isLeviesPt()) {
            List<PtSlabDto> slabs = ptSlabRepository.inForce(stateCode, today).stream()
                    .map(PtSlab::toDto)
                    .toList();
            return new PtStateResponse(stateCode, stateName, PtSource.REFERENCE, null, null, slabs);
        }

        return new PtStateResponse(stateCode, stateName, PtSource.NONE, null, null, Collections.emptyList());
    }

    private Set<String> getActiveWorkLocationStateCodes() {
        List<WorkLocationResponse> locations = workLocationService.list(true);
        if (locations == null) {
            return Collections.emptySet();
        }
        return locations.stream()
                .map(WorkLocationResponse::stateCode)
                .filter(Objects::nonNull)
                .map(this::normalizeStateCode)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateSlabs(List<PtSlabDto> slabs) {
        if (slabs == null || slabs.isEmpty()) {
            throw new PtValidationException("At least one slab is required");
        }
        for (PtSlabDto current : slabs) {
            if (current.fromAmount() == null || current.fromAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new PtValidationException("from_amount must be non-negative");
            }
            if (current.amount() == null || current.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new PtValidationException("amount must be non-negative");
            }
            if (current.deductionMonths() != null) {
                if (current.deductionMonths().isEmpty()) {
                    throw new PtValidationException("deduction_months cannot be empty if specified");
                }
                for (Integer m : current.deductionMonths()) {
                    if (m == null || m < 1 || m > 12) {
                        throw new PtValidationException("deduction_months values must be between 1 and 12");
                    }
                }
            }
            if (current.toAmount() != null && current.toAmount().compareTo(current.fromAmount()) <= 0) {
                throw new PtValidationException("to_amount must be greater than from_amount");
            }
        }

        // Validate per-month contiguity and non-overlapping (allowing month-split override slabs)
        for (int month = 1; month <= 12; month++) {
            final int m = month;
            List<PtSlabDto> monthSlabs = slabs.stream()
                    .filter(s -> s.deductionMonths() == null
                            || s.deductionMonths().isEmpty()
                            || s.deductionMonths().contains(m))
                    .sorted(Comparator.comparing(PtSlabDto::fromAmount, Comparator.nullsLast(BigDecimal::compareTo))
                            .thenComparing(s -> s.toAmount() == null ? 1 : 0)
                            .thenComparing(s -> s.toAmount() != null ? s.toAmount() : BigDecimal.ZERO))
                    .toList();

            if (monthSlabs.isEmpty()) {
                continue;
            }

            for (int i = 0; i < monthSlabs.size(); i++) {
                PtSlabDto current = monthSlabs.get(i);
                if (i < monthSlabs.size() - 1) {
                    if (current.toAmount() == null) {
                        throw new PtValidationException("Only the last slab may have to_amount null");
                    }
                    PtSlabDto next = monthSlabs.get(i + 1);
                    if (next.fromAmount().compareTo(current.toAmount()) < 0) {
                        throw new PtValidationException("Overlapping slabs: slab " + (i + 1) + " ends at "
                                + current.toAmount() + " but slab " + (i + 2) + " starts at " + next.fromAmount());
                    }
                    if (next.fromAmount().compareTo(current.toAmount()) > 0) {
                        throw new PtValidationException("Gap between slabs: slab " + (i + 1) + " ends at "
                                + current.toAmount() + " but slab " + (i + 2) + " starts at " + next.fromAmount());
                    }
                }
            }
        }
    }

    private String normalizeStateCode(String stateCode) {
        return stateCode != null ? stateCode.trim().toUpperCase(Locale.ROOT) : "";
    }

    private LocalDate currentDate() {
        return LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    private UUID resolveActorUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String subject = null;
            if (auth.getPrincipal() instanceof Jwt jwt) {
                subject = jwt.getSubject();
            }
            if (subject == null || subject.isBlank()) {
                subject = auth.getName();
            }
            if (subject != null) {
                try {
                    return UUID.fromString(subject);
                } catch (IllegalArgumentException e) {
                    // Not a UUID subject
                }
            }
        }
        return new UUID(0L, 0L);
    }

    private String resolveActorLabel() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String name = null;
            if (auth.getPrincipal() instanceof Jwt jwt) {
                name = jwt.getSubject();
            }
            if (name == null || name.isBlank()) {
                name = auth.getName();
            }
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "system";
    }
}
