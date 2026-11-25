package com.infinevo.payroll.form16;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.org.DesignationResponse;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PeriodTaxTotal;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.taxcalc.RegimeCalculators;
import com.infinevo.payroll.taxcalc.TaxInputAssembler;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRepository;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service implementation for generating the Form 16 statement (W-36.4).
 */
@Service
@Transactional(readOnly = true)
public class Form16ServiceImpl implements Form16Service {

    private static final Logger log = LoggerFactory.getLogger(Form16ServiceImpl.class);

    /** Reads the stored working; amounts stay exact. */
    private static final ObjectMapper WORKING_READER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);

    private final TaxDeductorService taxDeductorService;
    private final EmployeeTdsService employeeTdsService;
    private final TaxComputationRepository taxComputationRepository;
    private final EmployeePayRunLineRepository payRunLineRepository;
    private final PayRunRepository payRunRepository;
    private final EmployeeService employeeService;
    private final EmployeeIdentificationService employeeIdentificationService;
    private final PriorPayrollTaxQuery priorTax;
    private final TenantProfileService tenantProfileService;
    private final DesignationService designationService;
    private final TaxInputAssembler taxInputAssembler;
    private final RegimeCalculators regimeCalculators;
    /** The old-regime rerun's own read-only transaction, always rolled back: see {@link #oldRegimeIncomeFromSalary}. */
    private final TransactionTemplate rerunTransaction;

    public Form16ServiceImpl(
            TaxDeductorService taxDeductorService,
            EmployeeTdsService employeeTdsService,
            TaxComputationRepository taxComputationRepository,
            EmployeePayRunLineRepository payRunLineRepository,
            PayRunRepository payRunRepository,
            EmployeeService employeeService,
            EmployeeIdentificationService employeeIdentificationService,
            PriorPayrollTaxQuery priorTax,
            TenantProfileService tenantProfileService,
            DesignationService designationService,
            TaxInputAssembler taxInputAssembler,
            RegimeCalculators regimeCalculators,
            PlatformTransactionManager transactionManager) {
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
        this.tenantProfileService =
                Objects.requireNonNull(tenantProfileService, "tenantProfileService must not be null");
        this.designationService = Objects.requireNonNull(designationService, "designationService must not be null");
        this.taxInputAssembler = Objects.requireNonNull(taxInputAssembler, "taxInputAssembler must not be null");
        this.regimeCalculators = Objects.requireNonNull(regimeCalculators, "regimeCalculators must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.rerunTransaction = new TransactionTemplate(transactionManager);
        this.rerunTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.rerunTransaction.setReadOnly(true);
    }

    @Override
    public Form16Statement render(UUID employeeId, String financialYear) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        UUID tenantId = TenantContext.require();

        // 1. Verify deductor exists for tenant
        TaxDeductorResponse deductorResp = taxDeductorService.current().orElseThrow(DeductorNotSetException::new);

        // D-149: the deductor is the tenant, by name (W-36.4 section 4). Blank when the name cannot be read.
        String deductorName = tenantProfileService
                .branding()
                .map(TenantBranding::name)
                .filter(Objects::nonNull)
                .orElse("");

        DeductorDetails deductor = new DeductorDetails(
                deductorName,
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
        String designation = designationName(employeeResp.designationId());

        String pan = employeeIdentificationService
                .find(employeeId)
                .map(EmployeeIdentificationResponse::panNumber)
                .orElse("");

        EmployeeDetails employee = new EmployeeDetails(employeeName.trim(), pan, designation);

        // 3. Fetch latest tax computation breakdown
        TaxComputationRecord computation = taxComputationRepository
                .findFirstByTenantIdAndEmployeeIdAndFinancialYearOrderByComputedAtDesc(
                        tenantId, employeeId, financialYear)
                .orElse(null);

        // 4. Fetch active TDS record. D-150: none is not an error; the statement prints nil tax and says so.
        EmployeeTds tds = employeeTdsService.active(employeeId, financialYear).orElse(null);
        boolean noTdsRecord = tds == null;

        String regime;
        BigDecimal annualTax;
        if (noTdsRecord) {
            regime = computation != null ? computation.getRegime() : null;
            annualTax = BigDecimal.ZERO;
        } else {
            regime = tds.getRegime() != null ? tds.getRegime().name() : "NEW";
            annualTax = tds.getAnnualTax();
        }

        // 5. Query paid tax lines for periods in the financial year
        String[] parts = financialYear.split("-");
        String startPeriod = parts[0] + "-04";
        String endPeriod = parts[1] + "-03";

        Map<String, BigDecimal> periodMap =
                byPeriod(payRunLineRepository.sumTaxLinesByPeriod(tenantId, employeeId, startPeriod, endPeriod));
        // D-150: approved regular runs not yet paid, shown on their own line
        Map<String, BigDecimal> approvedNotPaidMap = byPeriod(
                payRunLineRepository.sumApprovedUnpaidTaxLinesByPeriod(tenantId, employeeId, startPeriod, endPeriod));

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
                new Form16Assembler.Extras(
                        approvedNotPaidMap, oldRegimeIncomeFromSalary(computation, employeeId, fy), noTdsRecord),
                Instant.now());
    }

    private static Map<String, BigDecimal> byPeriod(List<PeriodTaxTotal> totals) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (totals != null) {
            for (PeriodTaxTotal pt : totals) {
                if (pt.getPeriod() != null && pt.getAmount() != null) {
                    map.put(pt.getPeriod(), pt.getAmount());
                }
            }
        }
        return map;
    }

    /**
     * The old-regime income from salary as the tax engine had it (D-150), or null when it is not needed or
     * cannot be found. Needed only when the stored gross total income may have been floored at zero, as
     * working back from that zero overstates the salary.
     *
     * <p>Read first from the computation's {@code working} JSON. A working written since D-153 holds each amount
     * as a number; one written before holds it as an object without its value, so when it has no number the
     * engine is run again, read-only, on the
     * same inputs; its figure is used only if it reproduces the stored computation (same house property,
     * other income and annual tax, and a floored total). The rerun runs in its own read-only
     * {@code REQUIRES_NEW} transaction that is always rolled back: the input assembler calls
     * transactional services that throw and are caught (a month with no salary version), and each such
     * throw marks the transaction it joins for rollback. Kept apart, that mark never reaches the
     * statement's transaction, so a failure here falls back to the working-back instead of a 500 (D-150).
     */
    BigDecimal oldRegimeIncomeFromSalary(TaxComputationRecord computation, UUID employeeId, FinancialYear fy) {
        if (computation == null || !"OLD".equalsIgnoreCase(computation.getRegime())) {
            return null;
        }
        BigDecimal fromWorking = incomeFromSalaryInWorking(computation.getWorking());
        if (fromWorking != null) {
            return fromWorking;
        }
        BigDecimal stored = nz(computation.getGrossTotalIncome());
        BigDecimal otherHeads = nz(computation.getHousePropertyIncome()).add(nz(computation.getOtherIncome()));
        boolean mayBeFloored = stored.signum() == 0 && otherHeads.signum() < 0;
        if (!mayBeFloored) {
            return null;
        }
        try {
            TaxComputation again = rerunTransaction.execute(status -> {
                status.setRollbackOnly(); // read-only and always discarded; rolls back without a throw
                return regimeCalculators
                        .forRegime(TaxRegime.OLD)
                        .compute(taxInputAssembler.assemble(computation.getTenantId(), employeeId, fy), fy);
            });
            if (again == null) {
                return null;
            }
            boolean same = again.grossTotalIncome().isZero()
                    && again.houseProperty()
                                    .housePropertyIncome()
                                    .raw()
                                    .compareTo(nz(computation.getHousePropertyIncome()))
                            == 0
                    && again.otherIncome().raw().compareTo(nz(computation.getOtherIncome())) == 0
                    && again.annualTax().raw().compareTo(nz(computation.getAnnualTax())) == 0;
            return same ? again.incomeFromSalary().raw() : null;
        } catch (RuntimeException e) {
            log.warn("Form 16: could not recompute old-regime income from salary for the working: {}", e.getMessage());
            return null;
        }
    }

    /** {@code incomeFromSalary} from the working JSON, when it is stored as a number. */
    static BigDecimal incomeFromSalaryInWorking(String working) {
        if (working == null || working.isBlank()) {
            return null;
        }
        try {
            JsonNode node = WORKING_READER.readTree(working).path("incomeFromSalary");
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                return new BigDecimal(node.asText().trim());
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * The name of the designation, never its id (D-149). Blank when the employee has none or it is gone.
     * Read from the list, not {@code get}: a not-found thrown inside this read-only transaction would mark
     * it rollback-only and turn the whole statement into a 500.
     */
    private String designationName(UUID designationId) {
        if (designationId == null) {
            return "";
        }
        List<DesignationResponse> designations = designationService.list(false);
        if (designations == null) {
            return "";
        }
        return designations.stream()
                .filter(d -> designationId.equals(d.id()))
                .map(DesignationResponse::name)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }

    @Override
    public Form16Statement renderOwn(String financialYear) {
        EmployeeResponse current = employeeService
                .currentEmployee()
                .orElseThrow(() -> new EmployeeService.NotFoundException(UUID.randomUUID()));
        return render(current.id(), financialYear);
    }
}
