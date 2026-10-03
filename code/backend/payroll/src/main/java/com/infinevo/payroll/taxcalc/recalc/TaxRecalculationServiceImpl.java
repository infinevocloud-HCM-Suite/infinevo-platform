package com.infinevo.payroll.taxcalc.recalc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxcalc.TaxCalculationService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.TaxSummaryFiguresMapper;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclarationRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TaxRecalculationService} (W-33.3).
 */
@Service
@Transactional
public class TaxRecalculationServiceImpl implements TaxRecalculationService {

    private static final Logger log = LoggerFactory.getLogger(TaxRecalculationServiceImpl.class);

    private final TaxComputationRepository taxComputationRepository;
    private final EmployeeInvestmentDeclarationRepository declarationRepository;
    private final TaxDeclarationWindowService windowService;
    private final TaxCalculationService taxCalculationService;
    private final TaxSummaryService taxSummaryService;
    private final TaxSummaryFiguresMapper taxSummaryFiguresMapper;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;
    private final EmployeeTdsService employeeTdsService;

    public TaxRecalculationServiceImpl(
            TaxComputationRepository taxComputationRepository,
            EmployeeInvestmentDeclarationRepository declarationRepository,
            TaxDeclarationWindowService windowService,
            TaxCalculationService taxCalculationService,
            TaxSummaryService taxSummaryService,
            TaxSummaryFiguresMapper taxSummaryFiguresMapper,
            EmployeeService employeeService,
            ObjectMapper objectMapper,
            @Autowired(required = false) EmployeeTdsService employeeTdsService) {
        this.taxComputationRepository =
                Objects.requireNonNull(taxComputationRepository, "taxComputationRepository must not be null");
        this.declarationRepository =
                Objects.requireNonNull(declarationRepository, "declarationRepository must not be null");
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
        this.taxCalculationService =
                Objects.requireNonNull(taxCalculationService, "taxCalculationService must not be null");
        this.taxSummaryService = Objects.requireNonNull(taxSummaryService, "taxSummaryService must not be null");
        this.taxSummaryFiguresMapper =
                Objects.requireNonNull(taxSummaryFiguresMapper, "taxSummaryFiguresMapper must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.employeeTdsService = employeeTdsService;
    }

    @Override
    public TaxComputationRecord recalculate(UUID employeeId, String financialYear, TaxTrigger trigger) {
        return recalculate(employeeId, financialYear, trigger, null);
    }

    @Override
    public TaxComputationRecord recalculate(
            UUID employeeId, String financialYear, TaxTrigger trigger, UUID computedBy) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");

        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();

        Optional<EmployeeInvestmentDeclaration> declOpt =
                declarationRepository.findByTenantIdAndEmployeeIdAndFinancialYear(tenantId, employeeId, fy.label());

        TaxRegime regime;
        TaxTrigger effectiveTrigger;
        UUID declarationId = null;

        if (declOpt.isPresent()) {
            EmployeeInvestmentDeclaration decl = declOpt.get();
            declarationId = decl.getId();
            String regimeStr = decl.getTaxRegime();
            regime = (regimeStr != null && !regimeStr.isBlank())
                    ? TaxRegime.valueOf(regimeStr.toUpperCase())
                    : TaxRegime.NEW;
            effectiveTrigger = trigger;
        } else {
            IncomeTaxDeclarationWindow window = windowService.findOrCreateDefault(tenantId, fy.label());
            String defaultRegime =
                    (window != null && window.getDefaultTaxRegime() != null) ? window.getDefaultTaxRegime() : "NEW";
            regime = TaxRegime.valueOf(defaultRegime.toUpperCase());
            effectiveTrigger = TaxTrigger.SALARY_DEFAULT;
        }

        TaxComputation result = taxCalculationService.compute(employeeId, fy, regime);

        String workingJson;
        try {
            workingJson = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("Failed to serialize TaxComputation to JSON, storing empty JSON object: {}", e.getMessage());
            workingJson = "{}";
        }

        TaxComputationRecord record = new TaxComputationRecord(
                tenantId,
                employeeId,
                declarationId,
                fy.label(),
                regime.name(),
                effectiveTrigger,
                result.grossTotalIncome().raw(),
                result.hraExemption().raw(),
                result.standardDeduction().raw(),
                result.professionalTax().raw(),
                result.houseProperty().housePropertyIncome().raw(),
                result.otherIncome().raw(),
                result.chapterViaDeductions().raw(),
                result.taxableIncome().raw(),
                result.taxBeforeRebate().raw(),
                result.rebate().raw(),
                result.surcharge().raw(),
                result.cess().raw(),
                result.prevEmploymentTds().raw(),
                result.annualTax().raw(),
                workingJson,
                Instant.now(),
                computedBy,
                "system");

        record = taxComputationRepository.save(record);

        // Update tax summary record if declaration header exists (W-32.4)
        if (declarationId != null) {
            try {
                TaxSummaryFigures figures = taxSummaryFiguresMapper.toFigures(result, fy);
                taxSummaryService.record(declarationId, regime.name(), figures);
            } catch (Exception e) {
                log.error("Failed to record tax summary for declaration {}: {}", declarationId, e.getMessage(), e);
            }
        }

        // Publish to EmployeeTdsService if bean is present (W-36.1 hook)
        if (employeeTdsService != null) {
            try {
                // Rounded here, once: employee_tds is a stored amount and refuses a scale above 2. A refusal is
                // not just logged - it marks this transaction rollback-only and the tax_computation row is lost.
                EmployeeTdsService.TdsFigures tdsFigures = new EmployeeTdsService.TdsFigures(
                        regime.name(),
                        result.grossTotalIncome().toAmount(),
                        result.taxableIncome().toAmount(),
                        result.annualTax().toAmount(),
                        declarationId,
                        effectiveTrigger.name() + " " + record.getId());
                employeeTdsService.record(employeeId, fy.label(), tdsFigures);
            } catch (Exception e) {
                log.error("Failed to record employee TDS for employee {}: {}", employeeId, e.getMessage(), e);
            }
        }

        return record;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxComputationRecordResponse> history(UUID employeeId, String financialYear) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        FinancialYear parsedFy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        employeeService.get(employeeId);

        List<TaxComputationRecord> records =
                taxComputationRepository.findByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        tenantId, employeeId, parsedFy.label());

        return records.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxComputationRecordResponse> historyOwn(String financialYear) {
        EmployeeResponse current = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PermissionDeniedException("payroll.tax_declaration.read_own"));
        return history(current.id(), financialYear);
    }

    private TaxComputationRecordResponse toResponse(TaxComputationRecord r) {
        Object workingObj;
        try {
            workingObj = objectMapper.readTree(r.getWorking());
        } catch (Exception e) {
            workingObj = r.getWorking();
        }

        return new TaxComputationRecordResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getDeclarationId(),
                r.getFinancialYear(),
                r.getRegime(),
                r.getTrigger(),
                r.getGrossTotalIncome(),
                r.getHraExemption(),
                r.getStandardDeduction(),
                r.getProfessionalTax(),
                r.getHousePropertyIncome(),
                r.getOtherIncome(),
                r.getChapterVia(),
                r.getTaxableIncome(),
                r.getTaxBeforeRebate(),
                r.getRebate(),
                r.getSurcharge(),
                r.getCess(),
                r.getPrevEmployerTds(),
                r.getAnnualTax(),
                workingObj,
                r.getComputedAt(),
                r.getComputedBy());
    }
}
