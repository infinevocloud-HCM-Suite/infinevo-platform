package com.infinevo.payroll.salary;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.payroll.component.Benefit;
import com.infinevo.payroll.component.BenefitRepository;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.payroll.fbp.EmployeeFbpComponent;
import com.infinevo.payroll.fbp.EmployeeFbpComponentRepository;
import com.infinevo.payroll.fbp.FbpDeclarationService;
import com.infinevo.payroll.fbp.FbpSummaryResponse;
import com.infinevo.payroll.statutory.lines.CtcEpfComponent;
import com.infinevo.payroll.statutory.lines.CtcEpfComponentRepository;
import com.infinevo.payroll.statutory.lines.CtcEsiComponent;
import com.infinevo.payroll.statutory.lines.CtcEsiComponentRepository;
import com.infinevo.payroll.statutory.lines.DerivedStatutoryLine;
import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import com.infinevo.payroll.statutory.lines.StatutoryComponentCode;
import com.infinevo.payroll.statutory.lines.StatutoryLineDeriver;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EpfSettingRepository;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.payroll.statutory.settings.EsiSettingRepository;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeSalaryService} (W-26.2, W-31.3).
 */
@Service
@Transactional
public class EmployeeSalaryServiceImpl implements EmployeeSalaryService {

    private static final int MAX_ACTOR_LEN = 100;

    private final CtcStructureRepository ctcStructureRepository;
    private final EmployeeEarningRepository employeeEarningRepository;
    private final EmployeeBenefitRepository employeeBenefitRepository;
    private final EmployeeReimbursementRepository employeeReimbursementRepository;
    private final EarningRepository earningRepository;
    private final BenefitRepository benefitRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final EmployeeService employeeService;
    private final EmployeeFbpComponentRepository employeeFbpComponentRepository;
    private final FbpDeclarationService fbpDeclarationService;
    private final EpfSettingRepository epfSettingRepository;
    private final EsiSettingRepository esiSettingRepository;
    private final EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository;
    private final EmployeePersonalService employeePersonalService;
    private final CtcEpfComponentRepository ctcEpfComponentRepository;
    private final CtcEsiComponentRepository ctcEsiComponentRepository;

    public EmployeeSalaryServiceImpl(
            CtcStructureRepository ctcStructureRepository,
            EmployeeEarningRepository employeeEarningRepository,
            EmployeeBenefitRepository employeeBenefitRepository,
            EmployeeReimbursementRepository employeeReimbursementRepository,
            EarningRepository earningRepository,
            BenefitRepository benefitRepository,
            ReimbursementRepository reimbursementRepository,
            EmployeeService employeeService) {
        this(
                ctcStructureRepository,
                employeeEarningRepository,
                employeeBenefitRepository,
                employeeReimbursementRepository,
                earningRepository,
                benefitRepository,
                reimbursementRepository,
                employeeService,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public EmployeeSalaryServiceImpl(
            CtcStructureRepository ctcStructureRepository,
            EmployeeEarningRepository employeeEarningRepository,
            EmployeeBenefitRepository employeeBenefitRepository,
            EmployeeReimbursementRepository employeeReimbursementRepository,
            EarningRepository earningRepository,
            BenefitRepository benefitRepository,
            ReimbursementRepository reimbursementRepository,
            EmployeeService employeeService,
            EmployeeFbpComponentRepository employeeFbpComponentRepository,
            FbpDeclarationService fbpDeclarationService) {
        this(
                ctcStructureRepository,
                employeeEarningRepository,
                employeeBenefitRepository,
                employeeReimbursementRepository,
                earningRepository,
                benefitRepository,
                reimbursementRepository,
                employeeService,
                employeeFbpComponentRepository,
                fbpDeclarationService,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Autowired
    public EmployeeSalaryServiceImpl(
            CtcStructureRepository ctcStructureRepository,
            EmployeeEarningRepository employeeEarningRepository,
            EmployeeBenefitRepository employeeBenefitRepository,
            EmployeeReimbursementRepository employeeReimbursementRepository,
            EarningRepository earningRepository,
            BenefitRepository benefitRepository,
            ReimbursementRepository reimbursementRepository,
            EmployeeService employeeService,
            @Autowired(required = false) EmployeeFbpComponentRepository employeeFbpComponentRepository,
            @Autowired(required = false) FbpDeclarationService fbpDeclarationService,
            @Autowired(required = false) EpfSettingRepository epfSettingRepository,
            @Autowired(required = false) EsiSettingRepository esiSettingRepository,
            @Autowired(required = false) EmployeeStatutoryProfileRepository employeeStatutoryProfileRepository,
            @Autowired(required = false) EmployeePersonalService employeePersonalService,
            @Autowired(required = false) CtcEpfComponentRepository ctcEpfComponentRepository,
            @Autowired(required = false) CtcEsiComponentRepository ctcEsiComponentRepository) {
        this.ctcStructureRepository =
                Objects.requireNonNull(ctcStructureRepository, "ctcStructureRepository must not be null");
        this.employeeEarningRepository =
                Objects.requireNonNull(employeeEarningRepository, "employeeEarningRepository must not be null");
        this.employeeBenefitRepository =
                Objects.requireNonNull(employeeBenefitRepository, "employeeBenefitRepository must not be null");
        this.employeeReimbursementRepository = Objects.requireNonNull(
                employeeReimbursementRepository, "employeeReimbursementRepository must not be null");
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
        this.benefitRepository = Objects.requireNonNull(benefitRepository, "benefitRepository must not be null");
        this.reimbursementRepository =
                Objects.requireNonNull(reimbursementRepository, "reimbursementRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.employeeFbpComponentRepository = employeeFbpComponentRepository;
        this.fbpDeclarationService = fbpDeclarationService;
        this.epfSettingRepository = epfSettingRepository;
        this.esiSettingRepository = esiSettingRepository;
        this.employeeStatutoryProfileRepository = employeeStatutoryProfileRepository;
        this.employeePersonalService = employeePersonalService;
        this.ctcEpfComponentRepository = ctcEpfComponentRepository;
        this.ctcEsiComponentRepository = ctcEsiComponentRepository;
    }

    @Override
    public SalaryVersionResponse create(UUID employeeId, SalaryVersionRequest request) {
        validateRequest(request);
        UUID tenantId = TenantContext.require();
        validateEmployeeExists(employeeId);

        if (ctcStructureRepository.existsByTenantIdAndEmployeeId(tenantId, employeeId)) {
            throw new SalaryConflictException("A salary structure already exists for employee " + employeeId
                    + ". Use revisions endpoint to add a new version.");
        }

        return buildAndSaveVersion(tenantId, employeeId, request, null, false);
    }

    @Override
    public SalaryVersionResponse revise(UUID employeeId, SalaryVersionRequest request) {
        validateRequest(request);
        UUID tenantId = TenantContext.require();
        validateEmployeeExists(employeeId);

        if (ctcStructureRepository.existsByTenantIdAndEmployeeIdAndEffectiveFrom(
                tenantId, employeeId, request.effectiveFrom())) {
            throw new SalaryConflictException("A salary version for employee " + employeeId + " with effective_from "
                    + request.effectiveFrom() + " already exists");
        }

        CtcStructure previous = ctcStructureRepository
                .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        tenantId, employeeId, request.effectiveFrom())
                .orElse(null);

        SalaryVersionResponse created = buildAndSaveVersion(tenantId, employeeId, request, null, false);

        if (previous != null && fbpDeclarationService != null) {
            fbpDeclarationService.carryForward(previous.getId(), created.id(), tenantId, employeeId);
        }

        CtcStructure saved = ctcStructureRepository
                .findByIdAndTenantId(created.id(), tenantId)
                .orElseThrow(() -> new SalaryNotFoundException("Salary version not found: " + created.id()));
        return toResponse(saved, tenantId, created.changeInPercent());
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryVersionResponse getAsOf(UUID employeeId, LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        validateEmployeeExists(employeeId);
        LocalDate targetDate = asOf != null ? asOf : LocalDate.now();

        CtcStructure version = ctcStructureRepository
                .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        tenantId, employeeId, targetDate)
                .orElseThrow(() -> new SalaryNotFoundException(
                        "No active salary version in force as of " + targetDate + " for employee " + employeeId));

        return toResponse(version, tenantId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalaryVersionResponse> listVersions(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        validateEmployeeExists(employeeId);

        List<CtcStructure> list =
                ctcStructureRepository.findAllByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(tenantId, employeeId);
        List<SalaryVersionResponse> responses = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CtcStructure current = list.get(i);
            BigDecimal change = null;
            if (i < list.size() - 1) {
                CtcStructure prev = list.get(i + 1);
                change = computeChangePercent(current.getAnnualCtc(), prev.getAnnualCtc());
            }
            responses.add(toResponse(current, tenantId, change));
        }
        return responses;
    }

    @Override
    public SalaryVersionResponse update(UUID employeeId, UUID versionId, SalaryVersionRequest request) {
        validateRequest(request);
        UUID tenantId = TenantContext.require();
        validateEmployeeExists(employeeId);

        CtcStructure existing = ctcStructureRepository
                .findByIdAndTenantId(versionId, tenantId)
                .orElseThrow(() -> new SalaryNotFoundException("CtcStructure", versionId));

        if (!existing.getEmployeeId().equals(employeeId)) {
            throw new SalaryNotFoundException("Salary version does not belong to employee " + employeeId);
        }

        LocalDate today = LocalDate.now();
        if (!existing.getEffectiveFrom().isAfter(today)) {
            throw new SalaryConflictException("Cannot edit salary version with effective_from "
                    + existing.getEffectiveFrom() + " because it is already in force or past. Revisions must be used.");
        }

        if (ctcStructureRepository.existsByTenantIdAndEmployeeIdAndEffectiveFromAndIdNot(
                tenantId, employeeId, request.effectiveFrom(), versionId)) {
            throw new SalaryConflictException("A salary version for employee " + employeeId + " with effective_from "
                    + request.effectiveFrom() + " already exists");
        }

        return buildAndSaveVersion(tenantId, employeeId, request, existing, true);
    }

    @Override
    public void cancel(UUID employeeId, UUID versionId) {
        UUID tenantId = TenantContext.require();
        validateEmployeeExists(employeeId);

        CtcStructure existing = ctcStructureRepository
                .findByIdAndTenantId(versionId, tenantId)
                .orElseThrow(() -> new SalaryNotFoundException("CtcStructure", versionId));

        if (!existing.getEmployeeId().equals(employeeId)) {
            throw new SalaryNotFoundException("Salary version does not belong to employee " + employeeId);
        }

        LocalDate today = LocalDate.now();
        if (!existing.getEffectiveFrom().isAfter(today)) {
            throw new SalaryConflictException("Cannot cancel salary version with effective_from "
                    + existing.getEffectiveFrom() + " because it is already in force or past.");
        }

        existing.setCancelled(true);
        existing.setCancelledAt(Instant.now());
        existing.setUpdatedBy(currentActor());
        ctcStructureRepository.save(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryVersionResponse versionInForce(UUID tenantId, UUID employeeId, LocalDate date) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        LocalDate targetDate = date != null ? date : LocalDate.now();

        CtcStructure version = ctcStructureRepository
                .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        tenantId, employeeId, targetDate)
                .orElseThrow(() -> new SalaryNotFoundException(
                        "No active salary version in force as of " + targetDate + " for employee " + employeeId));

        return toResponse(version, tenantId, null);
    }

    private SalaryVersionResponse buildAndSaveVersion(
            UUID tenantId,
            UUID employeeId,
            SalaryVersionRequest request,
            CtcStructure existingVersion,
            boolean isUpdate) {

        String actor = currentActor();
        List<SalarySplitCalculator.ComponentInput> earningInputs = resolveEarningInputs(tenantId, request.earnings());
        List<SalarySplitCalculator.ComponentInput> benefitInputs = resolveBenefitInputs(tenantId, request.benefits());
        List<SalarySplitCalculator.ComponentInput> reimbursementInputs =
                resolveReimbursementInputs(tenantId, request.reimbursements());

        SalarySplitCalculator.SplitOutput split =
                SalarySplitCalculator.resolve(request.annualCtc(), earningInputs, benefitInputs, reimbursementInputs);

        // Derive statutory lines from version wages, profile, and tenant settings (W-31.3)
        SalarySplitCalculator.CalculatedResult basicResult = SalarySplitCalculator.findBasicEarning(split.earnings());
        Money basicMonthly = basicResult != null ? Money.of(basicResult.monthlyAmount()) : Money.ZERO;
        BigDecimal grossMonthlySum = split.earnings().stream()
                .map(SalarySplitCalculator.CalculatedResult::monthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Money grossMonthly = Money.of(grossMonthlySum);

        EmployeeStatutoryProfile profile = employeeStatutoryProfileRepository != null
                ? employeeStatutoryProfileRepository
                        .findByTenantIdAndEmployeeId(tenantId, employeeId)
                        .orElse(null)
                : null;
        EpfSetting epf = epfSettingRepository != null
                ? epfSettingRepository.findByTenantId(tenantId).orElse(null)
                : null;
        EsiSetting esi = esiSettingRepository != null
                ? esiSettingRepository.findByTenantId(tenantId).orElse(null)
                : null;

        LocalDate dob = null;
        if (employeePersonalService != null) {
            try {
                dob = employeePersonalService
                        .find(employeeId)
                        .map(EmployeePersonalResponse::dateOfBirth)
                        .orElse(null);
            } catch (Exception e) {
                dob = null;
            }
        }

        List<DerivedStatutoryLine> statutoryLines = StatutoryLineDeriver.derive(
                basicMonthly, grossMonthly, profile, epf, esi, dob, request.effectiveFrom());

        BigDecimal statutoryIncludedAnnual = BigDecimal.ZERO;
        for (DerivedStatutoryLine line : statutoryLines) {
            if (line.includedInCtc()) {
                statutoryIncludedAnnual =
                        statutoryIncludedAnnual.add(line.annualAmount().raw());
            }
        }

        SalarySplitCalculator.validateCtcBalance(request.annualCtc(), split, statutoryIncludedAnnual);

        CtcStructure ctcStructure =
                isUpdate ? existingVersion : new CtcStructure(tenantId, employeeId, request.effectiveFrom(), actor);
        ctcStructure.setEffectiveFrom(request.effectiveFrom());
        ctcStructure.setAnnualCtc(split.annualCtc());
        ctcStructure.setMonthlyCtc(split.monthlyCtc());
        ctcStructure.setNotes(request.notes());
        ctcStructure.setUpdatedBy(actor);

        if (isUpdate) {
            employeeEarningRepository.deleteAllByTenantIdAndCtcStructureId(tenantId, ctcStructure.getId());
            employeeBenefitRepository.deleteAllByTenantIdAndCtcStructureId(tenantId, ctcStructure.getId());
            employeeReimbursementRepository.deleteAllByTenantIdAndCtcStructureId(tenantId, ctcStructure.getId());
            if (ctcEpfComponentRepository != null) {
                ctcEpfComponentRepository.deleteByTenantIdAndCtcStructureId(tenantId, ctcStructure.getId());
            }
            if (ctcEsiComponentRepository != null) {
                ctcEsiComponentRepository.deleteByTenantIdAndCtcStructureId(tenantId, ctcStructure.getId());
            }
            ctcStructure.getEarnings().clear();
            ctcStructure.getBenefits().clear();
            ctcStructure.getReimbursements().clear();
        }

        CtcStructure saved = ctcStructureRepository.save(ctcStructure);

        List<EmployeeEarning> savedEarnings = new ArrayList<>();
        for (SalarySplitCalculator.CalculatedResult res : split.earnings()) {
            EmployeeEarning ee = new EmployeeEarning(tenantId, res.componentId(), saved, actor);
            ee.setCalculationType(res.calculationType());
            ee.setValue(res.value());
            ee.setPercentageOf(res.percentageOf());
            ee.setMonthlyAmount(res.monthlyAmount());
            ee.setAnnualAmount(res.annualAmount());
            ee.setEarningFrequency(res.earningFrequency());
            ee.setEnabled(true);
            savedEarnings.add(employeeEarningRepository.save(ee));
        }

        List<EmployeeBenefit> savedBenefits = new ArrayList<>();
        for (SalarySplitCalculator.CalculatedResult res : split.benefits()) {
            EmployeeBenefit eb = new EmployeeBenefit(tenantId, res.componentId(), saved, actor);
            eb.setCalculationType(res.calculationType());
            eb.setValue(res.value());
            eb.setPercentageOf(res.percentageOf());
            eb.setMonthlyAmount(res.monthlyAmount());
            eb.setAnnualAmount(res.annualAmount());
            eb.setEnabled(true);
            savedBenefits.add(employeeBenefitRepository.save(eb));
        }

        List<EmployeeReimbursement> savedReimbursements = new ArrayList<>();
        for (SalarySplitCalculator.CalculatedResult res : split.reimbursements()) {
            EmployeeReimbursement er = new EmployeeReimbursement(tenantId, res.componentId(), saved, actor);
            er.setCalculationType(res.calculationType());
            er.setValue(res.value());
            er.setPercentageOf(res.percentageOf());
            er.setMonthlyAmount(res.monthlyAmount());
            er.setAnnualAmount(res.annualAmount());
            er.setCarryForwardOption(res.carryForwardOption());
            er.setEnabled(true);
            savedReimbursements.add(employeeReimbursementRepository.save(er));
        }

        for (DerivedStatutoryLine line : statutoryLines) {
            if (line.code() == StatutoryComponentCode.ESI_EMPLOYEE
                    || line.code() == StatutoryComponentCode.ESI_EMPLOYER) {
                if (ctcEsiComponentRepository != null) {
                    CtcEsiComponent comp = new CtcEsiComponent(
                            tenantId,
                            saved.getId(),
                            line.code(),
                            line.share(),
                            line.wageBase().raw(),
                            line.rate(),
                            line.monthlyAmount().raw(),
                            line.annualAmount().raw(),
                            line.includedInCtc(),
                            actor);
                    ctcEsiComponentRepository.save(comp);
                }
            } else {
                if (ctcEpfComponentRepository != null) {
                    CtcEpfComponent comp = new CtcEpfComponent(
                            tenantId,
                            saved.getId(),
                            line.code(),
                            line.share(),
                            line.wageBase().raw(),
                            line.rate(),
                            line.monthlyAmount().raw(),
                            line.annualAmount().raw(),
                            line.includedInCtc(),
                            actor);
                    ctcEpfComponentRepository.save(comp);
                }
            }
        }

        saved.getEarnings().clear();
        saved.getEarnings().addAll(savedEarnings);
        saved.getBenefits().clear();
        saved.getBenefits().addAll(savedBenefits);
        saved.getReimbursements().clear();
        saved.getReimbursements().addAll(savedReimbursements);

        if (isUpdate && fbpDeclarationService != null) {
            fbpDeclarationService.recapAfterSalaryUpdate(saved.getId(), tenantId);
        }

        BigDecimal change =
                computeChangeFromPrevious(tenantId, employeeId, saved.getEffectiveFrom(), saved.getAnnualCtc());
        return toResponse(saved, tenantId, change);
    }

    private BigDecimal computeChangeFromPrevious(
            UUID tenantId, UUID employeeId, LocalDate effectiveFrom, BigDecimal currentAnnualCtc) {
        Optional<CtcStructure> prevOpt =
                ctcStructureRepository
                        .findFirstByTenantIdAndEmployeeIdAndCancelledFalseAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                tenantId, employeeId, effectiveFrom.minusDays(1));
        return prevOpt.map(prev -> computeChangePercent(currentAnnualCtc, prev.getAnnualCtc()))
                .orElse(null);
    }

    private static BigDecimal computeChangePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateRequest(SalaryVersionRequest request) {
        if (request == null) {
            throw new SalaryValidationException("request", "Request body must not be null");
        }
        if (request.annualCtc() == null || request.annualCtc().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SalaryValidationException("annualCtc", "Annual CTC must be greater than zero");
        }
        if (request.effectiveFrom() == null) {
            throw new SalaryValidationException("effectiveFrom", "Effective from date is required");
        }
    }

    private void validateEmployeeExists(UUID employeeId) {
        employeeService.get(employeeId);
    }

    private List<SalarySplitCalculator.ComponentInput> resolveEarningInputs(
            UUID tenantId, List<SalaryComponentItemRequest> items) {
        if (items == null) return List.of();
        List<SalarySplitCalculator.ComponentInput> list = new ArrayList<>();
        for (SalaryComponentItemRequest item : items) {
            Earning earning = earningRepository
                    .findByIdAndTenantIdAndDeletedFalse(item.componentId(), tenantId)
                    .orElseThrow(() -> new SalaryValidationException(
                            "earnings.componentId",
                            "Earning component " + item.componentId() + " does not exist in this tenant"));
            if (!earning.isActive()) {
                throw new SalaryValidationException(
                        "earnings.componentId", "Earning component " + earning.getCode() + " is inactive");
            }
            list.add(new SalarySplitCalculator.ComponentInput(
                    item.componentId(),
                    earning.getCode(),
                    earning.getName(),
                    item.calculationType(),
                    item.value(),
                    item.percentageOf(),
                    earning.isIncludedInCtc(),
                    earning.getEarningType(),
                    item.earningFrequency() != null ? item.earningFrequency() : earning.getEarningFrequency(),
                    null));
        }
        return list;
    }

    private List<SalarySplitCalculator.ComponentInput> resolveBenefitInputs(
            UUID tenantId, List<SalaryComponentItemRequest> items) {
        if (items == null) return List.of();
        List<SalarySplitCalculator.ComponentInput> list = new ArrayList<>();
        for (SalaryComponentItemRequest item : items) {
            Benefit benefit = benefitRepository
                    .findByIdAndTenantIdAndDeletedFalse(item.componentId(), tenantId)
                    .orElseThrow(() -> new SalaryValidationException(
                            "benefits.componentId",
                            "Benefit component " + item.componentId() + " does not exist in this tenant"));
            if (!benefit.isActive()) {
                throw new SalaryValidationException(
                        "benefits.componentId", "Benefit component " + benefit.getCode() + " is inactive");
            }
            list.add(new SalarySplitCalculator.ComponentInput(
                    item.componentId(),
                    benefit.getCode(),
                    benefit.getName(),
                    item.calculationType(),
                    item.value(),
                    item.percentageOf(),
                    benefit.isIncludedInCtc(),
                    null,
                    null,
                    null));
        }
        return list;
    }

    private List<SalarySplitCalculator.ComponentInput> resolveReimbursementInputs(
            UUID tenantId, List<SalaryComponentItemRequest> items) {
        if (items == null) return List.of();
        List<SalarySplitCalculator.ComponentInput> list = new ArrayList<>();
        for (SalaryComponentItemRequest item : items) {
            Reimbursement reimbursement = reimbursementRepository
                    .findByIdAndTenantIdAndDeletedFalse(item.componentId(), tenantId)
                    .orElseThrow(() -> new SalaryValidationException(
                            "reimbursements.componentId",
                            "Reimbursement component " + item.componentId() + " does not exist in this tenant"));
            if (!reimbursement.isActive()) {
                throw new SalaryValidationException(
                        "reimbursements.componentId",
                        "Reimbursement component " + reimbursement.getCode() + " is inactive");
            }
            list.add(new SalarySplitCalculator.ComponentInput(
                    item.componentId(),
                    reimbursement.getCode(),
                    reimbursement.getName(),
                    item.calculationType(),
                    item.value(),
                    item.percentageOf(),
                    reimbursement.isIncludedInCtc(),
                    null,
                    null,
                    item.carryForwardOption() != null
                            ? item.carryForwardOption()
                            : reimbursement.getCarryForwardOption()));
        }
        return list;
    }

    private SalaryVersionResponse toResponse(CtcStructure version, UUID tenantId, BigDecimal changePercent) {
        List<EmployeeEarning> earnings =
                employeeEarningRepository.findAllByTenantIdAndCtcStructureId(tenantId, version.getId());
        List<EmployeeBenefit> benefits =
                employeeBenefitRepository.findAllByTenantIdAndCtcStructureId(tenantId, version.getId());
        List<EmployeeReimbursement> reimbursements =
                employeeReimbursementRepository.findAllByTenantIdAndCtcStructureId(tenantId, version.getId());

        List<EmployeeFbpComponent> declarations = employeeFbpComponentRepository != null
                ? employeeFbpComponentRepository.findAllByTenantIdAndCtcStructureId(tenantId, version.getId())
                : Collections.emptyList();

        Map<UUID, EmployeeFbpComponent> earningDeclMap = new HashMap<>();
        Map<UUID, EmployeeFbpComponent> reimbDeclMap = new HashMap<>();
        for (EmployeeFbpComponent decl : declarations) {
            if (decl.getEarningId() != null) {
                earningDeclMap.put(decl.getEarningId(), decl);
            } else if (decl.getReimbursementId() != null) {
                reimbDeclMap.put(decl.getReimbursementId(), decl);
            }
        }

        List<SalaryComponentItemResponse> earningResponses = earnings.stream()
                .map(e -> {
                    Optional<Earning> def =
                            earningRepository.findByIdAndTenantIdAndDeletedFalse(e.getComponentId(), tenantId);
                    boolean isFbp = def.map(Earning::isFbpComponent).orElse(false);
                    BigDecimal declaredAnnual = null;
                    BigDecimal declaredMonthly = null;
                    if (isFbp) {
                        EmployeeFbpComponent decl = earningDeclMap.get(e.getComponentId());
                        declaredAnnual = decl != null ? decl.getAnnualAmount() : BigDecimal.ZERO.setScale(4);
                        declaredMonthly = decl != null ? decl.getMonthlyAmount() : BigDecimal.ZERO.setScale(4);
                    }
                    return new SalaryComponentItemResponse(
                            e.getId(),
                            e.getComponentId(),
                            def.map(Earning::getCode).orElse("UNKNOWN"),
                            def.map(Earning::getName).orElse("Unknown"),
                            e.getCalculationType(),
                            e.getValue(),
                            e.getPercentageOf(),
                            e.getMonthlyAmount(),
                            e.getAnnualAmount(),
                            e.isEnabled(),
                            def.map(Earning::isIncludedInCtc).orElse(false),
                            e.getEarningFrequency(),
                            null,
                            isFbp,
                            declaredAnnual,
                            declaredMonthly);
                })
                .toList();

        List<SalaryComponentItemResponse> benefitResponses = benefits.stream()
                .map(b -> {
                    Optional<Benefit> def =
                            benefitRepository.findByIdAndTenantIdAndDeletedFalse(b.getComponentId(), tenantId);
                    return new SalaryComponentItemResponse(
                            b.getId(),
                            b.getComponentId(),
                            def.map(Benefit::getCode).orElse("UNKNOWN"),
                            def.map(Benefit::getName).orElse("Unknown"),
                            b.getCalculationType(),
                            b.getValue(),
                            b.getPercentageOf(),
                            b.getMonthlyAmount(),
                            b.getAnnualAmount(),
                            b.isEnabled(),
                            def.map(Benefit::isIncludedInCtc).orElse(false),
                            null,
                            null);
                })
                .toList();

        List<SalaryComponentItemResponse> reimbursementResponses = reimbursements.stream()
                .map(r -> {
                    Optional<Reimbursement> def =
                            reimbursementRepository.findByIdAndTenantIdAndDeletedFalse(r.getComponentId(), tenantId);
                    boolean isFbp = def.map(Reimbursement::isFbpComponent).orElse(false);
                    BigDecimal declaredAnnual = null;
                    BigDecimal declaredMonthly = null;
                    if (isFbp) {
                        EmployeeFbpComponent decl = reimbDeclMap.get(r.getComponentId());
                        declaredAnnual = decl != null ? decl.getAnnualAmount() : BigDecimal.ZERO.setScale(4);
                        declaredMonthly = decl != null ? decl.getMonthlyAmount() : BigDecimal.ZERO.setScale(4);
                    }
                    return new SalaryComponentItemResponse(
                            r.getId(),
                            r.getComponentId(),
                            def.map(Reimbursement::getCode).orElse("UNKNOWN"),
                            def.map(Reimbursement::getName).orElse("Unknown"),
                            r.getCalculationType(),
                            r.getValue(),
                            r.getPercentageOf(),
                            r.getMonthlyAmount(),
                            r.getAnnualAmount(),
                            r.isEnabled(),
                            def.map(Reimbursement::isIncludedInCtc).orElse(false),
                            null,
                            r.getCarryForwardOption(),
                            isFbp,
                            declaredAnnual,
                            declaredMonthly);
                })
                .toList();

        List<SalaryStatutoryItemResponse> statutoryResponses = new ArrayList<>();
        if (ctcEpfComponentRepository != null) {
            List<CtcEpfComponent> epfLines =
                    ctcEpfComponentRepository.findByTenantIdAndCtcStructureId(tenantId, version.getId());
            for (CtcEpfComponent epf : epfLines) {
                statutoryResponses.add(SalaryStatutoryItemResponse.from(epf));
            }
        }
        if (ctcEsiComponentRepository != null) {
            List<CtcEsiComponent> esiLines =
                    ctcEsiComponentRepository.findByTenantIdAndCtcStructureId(tenantId, version.getId());
            for (CtcEsiComponent esi : esiLines) {
                statutoryResponses.add(SalaryStatutoryItemResponse.from(esi));
            }
        }
        statutoryResponses.sort(Comparator.comparing(item -> {
            try {
                return StatutoryComponentCode.valueOf(item.componentCode()).ordinal();
            } catch (Exception ex) {
                return 999;
            }
        }));

        FbpSummaryResponse fbpSummary =
                fbpDeclarationService != null ? fbpDeclarationService.summary(version.getId(), tenantId) : null;

        return new SalaryVersionResponse(
                version.getId(),
                version.getEmployeeId(),
                version.getEffectiveFrom(),
                version.getAnnualCtc(),
                version.getMonthlyCtc(),
                version.isCancelled(),
                version.getCancelledAt(),
                version.getNotes(),
                changePercent,
                earningResponses,
                benefitResponses,
                reimbursementResponses,
                statutoryResponses,
                fbpSummary);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return CtcStructure.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR_LEN ? name.substring(0, MAX_ACTOR_LEN) : name;
    }
}
