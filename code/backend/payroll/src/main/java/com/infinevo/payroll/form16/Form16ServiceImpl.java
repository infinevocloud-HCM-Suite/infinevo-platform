package com.infinevo.payroll.form16;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PeriodTaxTotal;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for generating the Form 16 statement (W-36.4).
 */
@Service
@Transactional(readOnly = true)
public class Form16ServiceImpl implements Form16Service {

    private final TaxDeductorService taxDeductorService;
    private final EmployeeTdsService employeeTdsService;
    private final TaxComputationRepository taxComputationRepository;
    private final EmployeePayRunLineRepository payRunLineRepository;
    private final PayRunRepository payRunRepository;
    private final EmployeeService employeeService;
    private final EmployeeIdentificationService employeeIdentificationService;
    private final PriorPayrollTaxQuery priorTax;

    public Form16ServiceImpl(
            TaxDeductorService taxDeductorService,
            EmployeeTdsService employeeTdsService,
            TaxComputationRepository taxComputationRepository,
            EmployeePayRunLineRepository payRunLineRepository,
            PayRunRepository payRunRepository,
            EmployeeService employeeService,
            EmployeeIdentificationService employeeIdentificationService,
            PriorPayrollTaxQuery priorTax) {
        this.taxDeductorService = Objects.requireNonNull(taxDeductorService, "taxDeductorService must not be null");
        this.employeeTdsService = Objects.requireNonNull(employeeTdsService, "employeeTdsService must not be null");
        this.taxComputationRepository =
                Objects.requireNonNull(taxComputationRepository, "taxComputationRepository must not be null");
        this.payRunLineRepository =
                Objects.requireNonNull(payRunLineRepository, "payRunLineRepository must not be null");
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.employeeIdentificationService =
                Objects.requireNonNull(employeeIdentificationService, "employeeIdentificationService must not be null");
        this.priorTax = Objects.requireNonNull(priorTax, "priorTax must not be null");
    }

    @Override
    public Form16Statement render(UUID employeeId, String financialYear) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        UUID tenantId = TenantContext.require();

        // 1. Verify deductor exists for tenant
        TaxDeductorResponse deductorResp = taxDeductorService.current().orElseThrow(DeductorNotSetException::new);

        DeductorDetails deductor = new DeductorDetails(
                "Company",
                deductorResp.tan(),
                deductorResp.pan(),
                deductorResp.tdsCircle(),
                deductorResp.signatoryName(),
                deductorResp.signatoryParentName(),
                deductorResp.signatoryDesignation());

        // 2. Fetch employee and identification (PAN)
        EmployeeResponse employeeResp = employeeService.get(employeeId);
        if (employeeResp == null) {
            throw new EmployeeService.NotFoundException(employeeId);
        }

        String employeeName = (employeeResp.firstName() != null ? employeeResp.firstName() : "")
                + (employeeResp.lastName() != null && !employeeResp.lastName().isBlank()
                        ? " " + employeeResp.lastName()
                        : "");
        String designation = employeeResp.designationId() != null
                ? employeeResp.designationId().toString()
                : "";

        String pan = employeeIdentificationService
                .find(employeeId)
                .map(EmployeeIdentificationResponse::panNumber)
                .orElse("");

        EmployeeDetails employee = new EmployeeDetails(employeeName.trim(), pan, designation);

        // 3. Fetch active TDS record
        EmployeeTds tds = employeeTdsService
                .active(employeeId, financialYear)
                .orElseThrow(() -> new EmployeeTdsNotFoundException(employeeId, financialYear));

        String regime = tds.getRegime() != null ? tds.getRegime().name() : "NEW";
        BigDecimal annualTax = tds.getAnnualTax();

        // 4. Fetch latest tax computation breakdown
        TaxComputationRecord computation = taxComputationRepository
                .findFirstByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        tenantId, employeeId, financialYear)
                .orElse(null);

        // 5. Query paid tax lines for periods in the financial year
        String[] parts = financialYear.split("-");
        String startPeriod = parts[0] + "-04";
        String endPeriod = parts[1] + "-03";

        List<PeriodTaxTotal> lineTotals =
                payRunLineRepository.sumTaxLinesByPeriod(tenantId, employeeId, startPeriod, endPeriod);
        Map<String, BigDecimal> periodMap = new HashMap<>();
        if (lineTotals != null) {
            for (PeriodTaxTotal pt : lineTotals) {
                if (pt.getPeriod() != null && pt.getAmount() != null) {
                    periodMap.put(pt.getPeriod(), pt.getAmount());
                }
            }
        }

        // 6. Imported months (W-38.1): their TDS joins its quarter, and they count as covered (W-38.2 §3)
        FinancialYear fy = FinancialYear.parse(financialYear);
        Map<String, BigDecimal> importedMap = priorTax.byPeriod(tenantId, employeeId, fy);
        Set<String> importedPeriods = priorTax.importedPeriods(tenantId, fy);

        // 7. Periods with a paid regular pay run
        Set<String> paidPeriods =
                new HashSet<>(payRunRepository.findPaidRegularPeriods(tenantId, startPeriod, endPeriod));

        // 8. Assemble statement
        return Form16Assembler.assemble(
                financialYear,
                deductor,
                employee,
                regime,
                annualTax,
                periodMap,
                importedMap,
                computation,
                paidPeriods,
                importedPeriods,
                Instant.now());
    }

    @Override
    public Form16Statement renderOwn(String financialYear) {
        EmployeeResponse current = employeeService
                .currentEmployee()
                .orElseThrow(() -> new EmployeeService.NotFoundException(UUID.randomUUID()));
        return render(current.id(), financialYear);
    }
}
