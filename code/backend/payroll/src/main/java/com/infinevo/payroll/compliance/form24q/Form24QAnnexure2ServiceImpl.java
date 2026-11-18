package com.infinevo.payroll.compliance.form24q;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.form16.Form16Service;
import com.infinevo.payroll.form16.Form16Statement;
import com.infinevo.payroll.form16.TaxBreakdown;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link Form24QAnnexure2Service} (W-89.3).
 */
@Service
@Transactional(readOnly = true)
public class Form24QAnnexure2ServiceImpl implements Form24QAnnexure2Service {

    private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    private final TaxDeductorService taxDeductorService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeIdentificationService employeeIdentificationService;
    private final Form16Service form16Service;
    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeePayRunLineRepository payRunLineRepository;

    public Form24QAnnexure2ServiceImpl(
            TaxDeductorService taxDeductorService,
            EmployeeRepository employeeRepository,
            EmployeeIdentificationService employeeIdentificationService,
            Form16Service form16Service,
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeePayRunLineRepository payRunLineRepository) {
        this.taxDeductorService = Objects.requireNonNull(taxDeductorService, "taxDeductorService must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.employeeIdentificationService =
                Objects.requireNonNull(employeeIdentificationService, "employeeIdentificationService must not be null");
        this.form16Service = Objects.requireNonNull(form16Service, "form16Service must not be null");
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeePayRunRepository =
                Objects.requireNonNull(employeePayRunRepository, "employeePayRunRepository must not be null");
        this.payRunLineRepository =
                Objects.requireNonNull(payRunLineRepository, "payRunLineRepository must not be null");
    }

    @Override
    public Annexure2PreviewResponse previewAnnexure2(String financialYear) {
        UUID tenantId = TenantContext.require();
        FinancialYear fy = parseFinancialYear(financialYear);
        String assessmentYear = (fy.startYear() + 1) + "-" + (fy.endYear() + 1);

        // 1. Fetch Deductor
        TaxDeductorResponse deductor = taxDeductorService.current().orElseThrow(DeductorNotSetException::new);

        // 2. Resolve all employees employed during the FY
        List<Employee> employed = employeeRepository.findEmployedBetween(
                tenantId, fy.start(), fy.end(), EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED);

        // 3. Resolve closed pay runs for the 12 periods of the FY
        List<String> allPeriods = fy.months().stream().map(YearMonth::toString).toList();
        List<PayRun> payRuns = payRunRepository.findByTenantIdAndPeriodInAndStatusIn(
                tenantId, allPeriods, List.of(PayRunStatus.APPROVED, PayRunStatus.PAID));

        List<UUID> payRunIds = payRuns.stream().map(PayRun::getId).toList();

        List<EmployeePayRun> employeeRuns = payRunIds.isEmpty()
                ? Collections.emptyList()
                : employeePayRunRepository.findByTenantIdAndPayrunIdInAndInclusionStatus(
                        tenantId, payRunIds, InclusionStatus.INCLUDED);

        List<EmployeePayRunLine> taxLines = payRunIds.isEmpty()
                ? Collections.emptyList()
                : payRunLineRepository.findTaxLinesByPayrunIds(tenantId, payRunIds);

        Map<UUID, BigDecimal> taxByEmployeeRun = new HashMap<>();
        for (EmployeePayRunLine l : taxLines) {
            taxByEmployeeRun.merge(l.getEmployeePayrunId(), l.getAmount(), BigDecimal::add);
        }

        Map<UUID, BigDecimal> employeeGrossMap = new HashMap<>();
        Map<UUID, BigDecimal> employeeTdsMap = new HashMap<>();
        for (EmployeePayRun er : employeeRuns) {
            UUID empId = er.getEmployeeId();
            employeeGrossMap.merge(empId, er.getGrossEarnings(), BigDecimal::add);
            BigDecimal tds = taxByEmployeeRun.getOrDefault(er.getId(), BigDecimal.ZERO);
            employeeTdsMap.merge(empId, tds, BigDecimal::add);
        }

        // Distinct employees to report in Annexure II
        Set<UUID> employeeIds = new LinkedHashSet<>();
        Map<UUID, Employee> employeeEntityMap = new HashMap<>();
        for (Employee emp : employed) {
            employeeIds.add(emp.getId());
            employeeEntityMap.put(emp.getId(), emp);
        }
        for (UUID empId : employeeGrossMap.keySet()) {
            employeeIds.add(empId);
        }

        List<SalaryDetailRecord> records = new ArrayList<>();
        int serialNo = 1;

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalTds = BigDecimal.ZERO;
        BigDecimal totalShortfall = BigDecimal.ZERO;

        for (UUID empId : employeeIds) {
            Employee emp = employeeEntityMap.get(empId);
            if (emp == null) {
                emp = employeeRepository
                        .findByIdAndTenantIdAndDeletedFalse(empId, tenantId)
                        .orElse(null);
                if (emp != null) {
                    employeeEntityMap.put(empId, emp);
                }
            }

            String empNumber = emp != null && emp.getEmployeeNumber() != null ? emp.getEmployeeNumber() : "";
            String empName = emp != null
                    ? ((emp.getFirstName() != null ? emp.getFirstName() : "")
                                    + " "
                                    + (emp.getLastName() != null ? emp.getLastName() : ""))
                            .trim()
                    : "Employee";

            // PAN lookup
            String pan = "PANNOTAVBL";
            Optional<EmployeeIdentificationResponse> idOpt = employeeIdentificationService.find(empId);
            if (idOpt.isPresent() && idOpt.get().panNumber() != null) {
                String candidate = idOpt.get().panNumber().trim().toUpperCase();
                if (PAN_PATTERN.matcher(candidate).matches()) {
                    pan = candidate;
                }
            }

            LocalDate empFrom = (emp != null
                            && emp.getDateOfJoining() != null
                            && emp.getDateOfJoining().isAfter(fy.start()))
                    ? emp.getDateOfJoining()
                    : fy.start();
            LocalDate empTo = (emp != null
                            && emp.getTerminationDate() != null
                            && emp.getTerminationDate().isBefore(fy.end()))
                    ? emp.getTerminationDate()
                    : fy.end();

            // Try rendering Form 16 statement for statutory figures
            SalaryDetailRecord record;
            try {
                Form16Statement form16 = form16Service.render(empId, fy.label());
                TaxBreakdown b = form16.breakdown();

                BigDecimal gross =
                        b != null ? b.grossTotalIncome() : employeeGrossMap.getOrDefault(empId, BigDecimal.ZERO);
                BigDecimal sec10 = b != null ? b.hraExemption() : BigDecimal.ZERO;
                BigDecimal stdDed = b != null ? b.standardDeduction() : BigDecimal.ZERO;
                BigDecimal pt = b != null ? b.professionalTax() : BigDecimal.ZERO;
                BigDecimal sec16 = stdDed.add(pt);
                BigDecimal salaryIncome = gross.subtract(sec10).subtract(sec16).max(BigDecimal.ZERO);
                BigDecimal hpIncome = b != null ? b.housePropertyIncome() : BigDecimal.ZERO;
                BigDecimal otherInc = b != null ? b.otherIncome() : BigDecimal.ZERO;
                BigDecimal gti = salaryIncome.add(hpIncome).add(otherInc).max(BigDecimal.ZERO);
                BigDecimal via = b != null ? b.chapterVia() : BigDecimal.ZERO;
                BigDecimal taxable =
                        b != null ? b.taxableIncome() : gti.subtract(via).max(BigDecimal.ZERO);
                BigDecimal taxOnInc = b != null ? b.taxBeforeRebate() : BigDecimal.ZERO;
                BigDecimal rebate = b != null ? b.rebate() : BigDecimal.ZERO;
                BigDecimal surcharge = b != null ? b.surcharge() : BigDecimal.ZERO;
                BigDecimal cess = b != null ? b.cess() : BigDecimal.ZERO;
                BigDecimal annualTax = form16.annualTax();
                BigDecimal tdsCurr = form16.totalDeducted();
                BigDecimal tdsPrev = b != null ? b.prevEmployerTds() : BigDecimal.ZERO;
                BigDecimal balance = form16.balance();

                record = new SalaryDetailRecord(
                        empId,
                        serialNo++,
                        empNumber,
                        empName,
                        pan,
                        form16.regime(),
                        empFrom,
                        empTo,
                        gross,
                        sec10,
                        stdDed,
                        pt,
                        sec16,
                        salaryIncome,
                        hpIncome,
                        otherInc,
                        gti,
                        via,
                        taxable,
                        taxOnInc,
                        rebate,
                        surcharge,
                        cess,
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        annualTax,
                        tdsCurr,
                        tdsPrev,
                        balance);
            } catch (Exception e) {
                // Fallback from pay runs if Form 16 / TDS record not computed
                BigDecimal gross = employeeGrossMap.getOrDefault(empId, BigDecimal.ZERO);
                BigDecimal tdsCurr = employeeTdsMap.getOrDefault(empId, BigDecimal.ZERO);

                record = new SalaryDetailRecord(
                        empId,
                        serialNo++,
                        empNumber,
                        empName,
                        pan,
                        "NEW",
                        empFrom,
                        empTo,
                        gross.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        gross.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        gross.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        gross.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        tdsCurr.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }

            totalGross = totalGross.add(record.grossSalary());
            totalTaxable = totalTaxable.add(record.totalTaxableIncome());
            totalTax = totalTax.add(record.totalTaxPayable());
            totalTds = totalTds.add(record.totalTdsDeducted());
            if (record.shortfallOrExcess().compareTo(BigDecimal.ZERO) > 0) {
                totalShortfall = totalShortfall.add(record.shortfallOrExcess());
            }

            records.add(record);
        }

        return new Annexure2PreviewResponse(
                fy.label(),
                assessmentYear,
                deductor.tan(),
                deductor.pan(),
                deductor.signatoryName(),
                records.size(),
                totalGross.setScale(2, RoundingMode.HALF_UP),
                totalTaxable.setScale(2, RoundingMode.HALF_UP),
                totalTax.setScale(2, RoundingMode.HALF_UP),
                totalTds.setScale(2, RoundingMode.HALF_UP),
                totalShortfall.setScale(2, RoundingMode.HALF_UP),
                records);
    }

    @Override
    public byte[] exportAnnexure2(String financialYear, String format) {
        Annexure2PreviewResponse preview = previewAnnexure2(financialYear);
        String f = format != null ? format.trim().toUpperCase() : "CSV";

        return switch (f) {
            case "CSV" -> Annexure2CsvFormatter.format(preview);
            case "TXT" -> NsdlFvuTextFormatter.formatAnnexure2(preview);
            default ->
                throw new Form24QValidationException("Unsupported export format: " + format + " (expected CSV or TXT)");
        };
    }

    private FinancialYear parseFinancialYear(String fy) {
        try {
            return FinancialYear.parse(fy);
        } catch (Exception e) {
            throw new Form24QValidationException("Invalid financial year format: " + fy + " (expected YYYY-YYYY)");
        }
    }
}
