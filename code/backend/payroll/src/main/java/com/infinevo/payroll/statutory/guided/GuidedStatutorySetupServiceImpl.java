package com.infinevo.payroll.statutory.guided;

import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.statutory.pt.OrgPtOverride;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideRepository;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideSlab;
import com.infinevo.payroll.statutory.pt.OrgPtOverrideSlabRepository;
import com.infinevo.payroll.statutory.pt.PtHistory;
import com.infinevo.payroll.statutory.pt.PtHistoryOperation;
import com.infinevo.payroll.statutory.pt.PtHistoryRepository;
import com.infinevo.payroll.statutory.pt.PtSlab;
import com.infinevo.payroll.statutory.pt.PtSlabDto;
import com.infinevo.payroll.statutory.pt.PtSlabRepository;
import com.infinevo.payroll.statutory.pt.PtState;
import com.infinevo.payroll.statutory.pt.PtStateRepository;
import com.infinevo.payroll.statutory.pt.ReferenceState;
import com.infinevo.payroll.statutory.pt.ReferenceStateRepository;
import com.infinevo.payroll.statutory.settings.DeductionCycle;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EpfSettingRequest;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSettingRequest;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import com.infinevo.payroll.statutory.settings.StatutorySettingsValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Implementation of guided statutory setup wizard (W-93).
 */
@Service
public class GuidedStatutorySetupServiceImpl implements GuidedStatutorySetupService {

    private final StatutorySettingsService statutorySettingsService;
    private final EpfSettingRepository epfSettingRepository;
    private final EsiSettingRepository esiSettingRepository;
    private final WorkLocationService workLocationService;
    private final ReferenceStateRepository referenceStateRepository;
    private final PtStateRepository ptStateRepository;
    private final PtSlabRepository ptSlabRepository;
    private final OrgPtOverrideRepository orgPtOverrideRepository;
    private final OrgPtOverrideSlabRepository orgPtOverrideSlabRepository;
    private final PtHistoryRepository ptHistoryRepository;

    public GuidedStatutorySetupServiceImpl(
            StatutorySettingsService statutorySettingsService,
            EpfSettingRepository epfSettingRepository,
            EsiSettingRepository esiSettingRepository,
            WorkLocationService workLocationService,
            ReferenceStateRepository referenceStateRepository,
            PtStateRepository ptStateRepository,
            PtSlabRepository ptSlabRepository,
            OrgPtOverrideRepository orgPtOverrideRepository,
            OrgPtOverrideSlabRepository orgPtOverrideSlabRepository,
            PtHistoryRepository ptHistoryRepository) {
        this.statutorySettingsService =
                Objects.requireNonNull(statutorySettingsService, "statutorySettingsService must not be null");
        this.epfSettingRepository =
                Objects.requireNonNull(epfSettingRepository, "epfSettingRepository must not be null");
        this.esiSettingRepository =
                Objects.requireNonNull(esiSettingRepository, "esiSettingRepository must not be null");
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
    public GuidedStatutoryReadinessResponse getReadiness() {
        UUID tenantId = TenantContext.require();

        Optional<EpfSetting> epfOpt = epfSettingRepository.findByTenantId(tenantId);
        boolean epfConfigured =
                epfOpt.isPresent() && !"TEMPLATE".equals(epfOpt.get().getUpdatedBy());
        EpfReadiness epfReadiness = epfOpt.map(e -> new EpfReadiness(
                        e.isEnabled(),
                        e.getRegistrationNumber(),
                        e.getRegistrationDate(),
                        e.isRestrictEmployeeToCeiling(),
                        e.isIncludeEmployerInCtc(),
                        e.isIncludeEdliInCtc(),
                        e.isIncludeAdminInCtc()))
                .orElseGet(() -> new EpfReadiness(false, null, null, false, false, false, false));

        Optional<EsiSetting> esiOpt = esiSettingRepository.findByTenantId(tenantId);
        boolean esiConfigured =
                esiOpt.isPresent() && !"TEMPLATE".equals(esiOpt.get().getUpdatedBy());
        EsiReadiness esiReadiness = esiOpt.map(e -> new EsiReadiness(
                        e.isEnabled(), e.getRegistrationNumber(), e.getRegistrationDate(), e.isIncludeEmployerInCtc()))
                .orElseGet(() -> new EsiReadiness(false, null, null, false));

        Set<String> activeStateCodes = getActiveWorkLocationStateCodes();
        List<PtStateReadiness> operatingStates = new ArrayList<>();
        for (String stateCode : activeStateCodes) {
            String stateName = referenceStateRepository
                    .findByCode(stateCode)
                    .map(ReferenceState::getName)
                    .orElse(stateCode);
            boolean leviesPt = ptStateRepository
                    .findByStateCode(stateCode)
                    .map(PtState::isLeviesPt)
                    .orElse(false);

            Optional<OrgPtOverride> overrideOpt =
                    orgPtOverrideRepository.findByTenantIdAndStateCode(tenantId, stateCode);
            boolean configured = overrideOpt.isPresent() || !leviesPt;
            String regNo = overrideOpt.map(OrgPtOverride::getRegistrationNumber).orElse(null);

            operatingStates.add(new PtStateReadiness(stateCode, stateName, leviesPt, regNo, configured));
        }

        return new GuidedStatutoryReadinessResponse(
                epfConfigured,
                epfReadiness,
                esiConfigured,
                esiReadiness,
                operatingStates,
                StatutoryDefaults.standard());
    }

    @Override
    @Transactional
    public GuidedStatutorySetupResponse applySetup(GuidedStatutorySetupRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID tenantId = TenantContext.require();

        validateRequest(request);

        EpfSettingResponse epfResp;
        if (request.epf() != null) {
            boolean registered = Boolean.TRUE.equals(request.epf().registered());
            boolean restrict = Boolean.TRUE.equals(request.epf().restrictToWageCeiling());
            boolean includeCtc = Boolean.TRUE.equals(request.epf().includeEmployerInCtc());
            boolean includeEdli = Boolean.TRUE.equals(request.epf().includeEdliInCtc());
            boolean includeAdmin = Boolean.TRUE.equals(request.epf().includeAdminInCtc());

            Optional<EpfSetting> existingEpf = epfSettingRepository.findByTenantId(tenantId);
            EpfSetting existing = existingEpf.orElse(null);

            LocalDate regDate = request.epf().registrationDate() != null
                    ? request.epf().registrationDate()
                    : (existing != null ? existing.getRegistrationDate() : null);

            DeductionCycle deductionCycle = existing != null ? existing.getDeductionCycle() : DeductionCycle.MONTHLY;
            BigDecimal employeeRate = existing != null ? existing.getEmployeeRate() : new BigDecimal("12.0000");
            BigDecimal employerRate = existing != null ? existing.getEmployerRate() : new BigDecimal("12.0000");
            BigDecimal epsRate = existing != null ? existing.getEpsRate() : new BigDecimal("8.3300");
            BigDecimal edliRate = existing != null ? existing.getEdliRate() : new BigDecimal("0.5000");
            BigDecimal adminChargeRate = existing != null ? existing.getAdminChargeRate() : new BigDecimal("0.5000");
            BigDecimal wageCeiling = existing != null ? existing.getWageCeiling() : new BigDecimal("15000.0000");
            boolean prorateRestrictedWage = existing != null ? existing.isProrateRestrictedWage() : restrict;
            boolean considerEarnedWage = existing != null ? existing.isConsiderEarnedWage() : restrict;
            int epsSeniorAge = existing != null ? existing.getEpsSeniorAge() : 58;
            boolean inStructure = existing != null ? existing.isIncludeEmployerInStructure() : includeCtc;
            boolean edliInStructure = existing != null ? existing.isIncludeEdliInStructure() : includeEdli;
            boolean adminInStructure = existing != null ? existing.isIncludeAdminInStructure() : includeAdmin;
            boolean abry = existing != null ? existing.isAbryScheme() : false;

            EpfSettingRequest epfReq = new EpfSettingRequest(
                    registered,
                    registered ? request.epf().registrationNumber() : null,
                    registered ? regDate : null,
                    deductionCycle,
                    employeeRate,
                    employerRate,
                    epsRate,
                    edliRate,
                    adminChargeRate,
                    wageCeiling,
                    restrict,
                    restrict,
                    prorateRestrictedWage,
                    considerEarnedWage,
                    epsSeniorAge,
                    includeCtc,
                    includeEdli,
                    includeAdmin,
                    inStructure,
                    edliInStructure,
                    adminInStructure,
                    abry);
            epfResp = statutorySettingsService.saveEpf(epfReq);
        } else {
            epfResp = statutorySettingsService.epf(tenantId);
        }

        EsiSettingResponse esiResp;
        if (request.esi() != null) {
            boolean registered = Boolean.TRUE.equals(request.esi().registered());
            boolean includeCtc = Boolean.TRUE.equals(request.esi().includeEmployerInCtc());

            Optional<EsiSetting> existingEsi = esiSettingRepository.findByTenantId(tenantId);
            EsiSetting existing = existingEsi.orElse(null);

            LocalDate regDate = request.esi().registrationDate() != null
                    ? request.esi().registrationDate()
                    : (existing != null ? existing.getRegistrationDate() : null);

            DeductionCycle deductionCycle = existing != null ? existing.getDeductionCycle() : DeductionCycle.MONTHLY;
            BigDecimal employeeRate = existing != null ? existing.getEmployeeRate() : new BigDecimal("0.7500");
            BigDecimal employerRate = existing != null ? existing.getEmployerRate() : new BigDecimal("3.2500");
            BigDecimal wageCeiling = existing != null ? existing.getWageCeiling() : new BigDecimal("21000.0000");
            boolean includeInStructure = existing != null ? existing.isIncludeInStructure() : includeCtc;

            EsiSettingRequest esiReq = new EsiSettingRequest(
                    registered,
                    registered ? request.esi().registrationNumber() : null,
                    registered ? regDate : null,
                    deductionCycle,
                    employeeRate,
                    employerRate,
                    wageCeiling,
                    includeCtc,
                    includeInStructure);
            esiResp = statutorySettingsService.saveEsi(esiReq);
        } else {
            esiResp = statutorySettingsService.esi(tenantId);
        }

        String actorLabel = resolveActorLabel();
        UUID actorUserId = resolveActorUserId();
        List<PtStateResult> ptResults = new ArrayList<>();

        if (request.ptStates() != null) {
            for (PtStateSetupRequest ptReq : request.ptStates()) {
                if (ptReq.stateCode() == null || ptReq.stateCode().isBlank()) {
                    continue;
                }
                String normState = ptReq.stateCode().trim().toUpperCase(Locale.ROOT);
                LocalDate effFrom = ptReq.effectiveFrom() != null ? ptReq.effectiveFrom() : LocalDate.now();
                String regNo = ptReq.registrationNumber() != null
                        ? ptReq.registrationNumber().trim()
                        : null;
                String stateName = referenceStateRepository
                        .findByCode(normState)
                        .map(ReferenceState::getName)
                        .orElse(normState);

                if (regNo != null && !regNo.isEmpty()) {
                    Optional<OrgPtOverride> existingOpt =
                            orgPtOverrideRepository.findByTenantIdAndStateCode(tenantId, normState);
                    OrgPtOverride override;
                    List<PtSlabDto> beforeSlabs;
                    if (existingOpt.isPresent()) {
                        OrgPtOverride existing = existingOpt.get();
                        beforeSlabs =
                                orgPtOverrideSlabRepository
                                        .findByTenantIdAndOverrideIdOrderBySortOrderAsc(tenantId, existing.getId())
                                        .stream()
                                        .map(OrgPtOverrideSlab::toDto)
                                        .toList();
                        existing.update(regNo, effFrom, actorLabel);
                        override = orgPtOverrideRepository.save(existing);
                    } else {
                        beforeSlabs = ptSlabRepository.inForce(normState, effFrom).stream()
                                .map(PtSlab::toDto)
                                .toList();
                        override = new OrgPtOverride(tenantId, normState, regNo, effFrom, actorLabel);
                        override = orgPtOverrideRepository.save(override);

                        List<PtSlab> refSlabs = ptSlabRepository.inForce(normState, effFrom);
                        if (refSlabs != null && !refSlabs.isEmpty()) {
                            short sortOrder = 1;
                            for (PtSlab slab : refSlabs) {
                                OrgPtOverrideSlab overrideSlab = new OrgPtOverrideSlab(
                                        tenantId,
                                        override.getId(),
                                        slab.getFromAmount(),
                                        slab.getToAmount(),
                                        slab.getAmount(),
                                        slab.isFemaleExempt(),
                                        slab.getDeductionMonths(),
                                        sortOrder++,
                                        actorLabel);
                                orgPtOverrideSlabRepository.save(overrideSlab);
                            }
                        }
                    }

                    List<PtSlabDto> afterSlabs =
                            orgPtOverrideSlabRepository
                                    .findByTenantIdAndOverrideIdOrderBySortOrderAsc(tenantId, override.getId())
                                    .stream()
                                    .map(OrgPtOverrideSlab::toDto)
                                    .toList();

                    PtHistory history = new PtHistory(
                            tenantId, normState, PtHistoryOperation.OVERRIDE_SET, beforeSlabs, afterSlabs, actorUserId);
                    ptHistoryRepository.save(history);

                    ptResults.add(new PtStateResult(normState, stateName, regNo, "OVERRIDE"));
                } else {
                    boolean leviesPt = ptStateRepository
                            .findByStateCode(normState)
                            .map(PtState::isLeviesPt)
                            .orElse(false);
                    ptResults.add(new PtStateResult(normState, stateName, null, leviesPt ? "REFERENCE" : "EXEMPT"));
                }
            }
        }

        return new GuidedStatutorySetupResponse(epfResp, esiResp, ptResults, true);
    }

    private void validateRequest(GuidedStatutorySetupRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (request.epf() != null && Boolean.TRUE.equals(request.epf().registered())) {
            String regNo = request.epf().registrationNumber();
            if (regNo == null || regNo.isBlank()) {
                errors.put("epf.registrationNumber", "Registration number is required when EPF is registered");
            } else if (regNo.length() > 32) {
                errors.put("epf.registrationNumber", "Registration number must not exceed 32 characters");
            }
        }

        if (request.esi() != null && Boolean.TRUE.equals(request.esi().registered())) {
            String regNo = request.esi().registrationNumber();
            if (regNo == null || regNo.isBlank()) {
                errors.put("esi.registrationNumber", "Registration number is required when ESI is registered");
            } else if (regNo.length() > 32) {
                errors.put("esi.registrationNumber", "Registration number must not exceed 32 characters");
            }
        }

        if (request.ptStates() != null) {
            for (int i = 0; i < request.ptStates().size(); i++) {
                PtStateSetupRequest ptReq = request.ptStates().get(i);
                if (ptReq.stateCode() == null || ptReq.stateCode().isBlank()) {
                    errors.put("ptStates[" + i + "].stateCode", "State code is required");
                }
                if (ptReq.registrationNumber() != null
                        && ptReq.registrationNumber().length() > 32) {
                    errors.put(
                            "ptStates[" + i + "].registrationNumber",
                            "Registration number must not exceed 32 characters");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new StatutorySettingsValidationException(errors);
        }
    }

    private Set<String> getActiveWorkLocationStateCodes() {
        List<WorkLocationResponse> locations = workLocationService.list(true);
        if (locations == null) {
            return Collections.emptySet();
        }
        return locations.stream()
                .map(WorkLocationResponse::stateCode)
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
                    // Not a UUID
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
