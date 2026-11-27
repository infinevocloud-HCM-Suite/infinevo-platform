package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.detail.EmployeeBank;
import com.infinevo.core.employee.detail.EmployeeBankRepository;
import com.infinevo.core.employee.detail.PaymentMode;
import com.infinevo.core.report.CsvWriter;
import com.infinevo.core.report.ReportColumn;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BankFileService} (W-88).
 * Generates bank salary payment CSV files from APPROVED or PAID pay runs.
 */
@Service
@Transactional(readOnly = true)
public class BankFileServiceImpl implements BankFileService {

    private static final Logger log = LoggerFactory.getLogger(BankFileServiceImpl.class);

    private static final List<ReportColumn> COLUMNS = List.of(
            ReportColumn.text("sr_no", "Sr No"),
            ReportColumn.text("employee_number", "Employee Number"),
            ReportColumn.text("employee_name", "Employee Name"),
            ReportColumn.text("account_holder_name", "Account Holder Name"),
            ReportColumn.text("bank_account_number", "Bank Account Number"),
            ReportColumn.text("ifsc_code", "IFSC Code"),
            ReportColumn.text("bank_name", "Bank Name"),
            ReportColumn.text("account_type", "Account Type"),
            ReportColumn.text("net_pay", "Net Pay (INR)"),
            ReportColumn.text("narration", "Narration"));

    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeBankRepository employeeBankRepository;

    public BankFileServiceImpl(
            PayRunRepository payRunRepository,
            EmployeePayRunRepository employeePayRunRepository,
            EmployeeRepository employeeRepository,
            EmployeeBankRepository employeeBankRepository) {
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.employeePayRunRepository =
                Objects.requireNonNull(employeePayRunRepository, "employeePayRunRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.employeeBankRepository =
                Objects.requireNonNull(employeeBankRepository, "employeeBankRepository must not be null");
    }

    @Override
    public List<BankFileRow> getRows(UUID payrunId) {
        return run(payrunId, null).rows();
    }

    /**
     * Loads the run once and applies the omission rules (spec section 3.2). {@code generate}, {@code getRows} and
     * the template exports ({@code W-88.2}) all read this, so the run, its employee rows and the employees are
     * each queried once.
     */
    @Override
    public BankFileRun run(UUID payrunId, Set<UUID> selection) {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        UUID tenantId = TenantContext.require();

        PayRun payRun = payRunRepository
                .findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayRunNotFoundException(payrunId));
        if (payRun.getStatus() != PayRunStatus.APPROVED && payRun.getStatus() != PayRunStatus.PAID) {
            throw new BankFileInvalidStatusException(payRun.getStatus());
        }

        List<EmployeePayRun> runRows = employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                tenantId, payrunId, InclusionStatus.INCLUDED);
        BigDecimal runNetPay = sumNetPay(runRows);

        // W-88.2: a selection narrows the run; it never adds anyone. An id outside the run is refused.
        List<EmployeePayRun> includedPayRuns = runRows;
        if (selection != null && !selection.isEmpty()) {
            Set<UUID> inRun =
                    runRows.stream().map(EmployeePayRun::getEmployeeId).collect(Collectors.toSet());
            List<String> strangers = selection.stream()
                    .filter(id -> !inRun.contains(id))
                    .map(UUID::toString)
                    .sorted()
                    .toList();
            if (!strangers.isEmpty()) {
                throw new IllegalArgumentException(
                        "These employees are not included in this pay run: " + String.join(", ", strangers));
            }
            includedPayRuns = runRows.stream()
                    .filter(epr -> selection.contains(epr.getEmployeeId()))
                    .toList();
        }
        BigDecimal selectedNetPay = sumNetPay(includedPayRuns);
        if (includedPayRuns.isEmpty()) {
            return new BankFileRun(payRun, List.of(), Map.of(), List.of(), runNetPay, selectedNetPay);
        }

        Set<UUID> employeeIds =
                includedPayRuns.stream().map(EmployeePayRun::getEmployeeId).collect(Collectors.toSet());
        Map<UUID, Employee> employeeMap = employeeRepository.findByTenantIdAndIdIn(tenantId, employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (e1, e2) -> e1));
        // Bank rows for the whole run in one query.
        Map<UUID, EmployeeBank> bankMap =
                employeeBankRepository.findByTenantIdAndEmployeeIdIn(tenantId, employeeIds).stream()
                        .collect(Collectors.toMap(b -> b.getEmployee().getId(), b -> b, (b1, b2) -> b1));

        String narration = "Salary " + payRun.getPeriod();
        List<BankFileRow> rows = new ArrayList<>();
        List<BankFileOmission> omissions = new ArrayList<>();
        List<String> omittedWithReason = new ArrayList<>();
        BigDecimal omittedNetPay = BigDecimal.ZERO;

        for (EmployeePayRun epr : includedPayRuns) {
            UUID employeeId = epr.getEmployeeId();
            Employee emp = employeeMap.get(employeeId);
            String employeeNumber =
                    emp != null && emp.getEmployeeNumber() != null ? emp.getEmployeeNumber() : employeeId.toString();
            EmployeeBank bank = bankMap.get(employeeId);
            BigDecimal netPay = epr.getNetPay();

            String reason = null;
            if (bank == null) {
                reason = "missing bank details";
            } else if (bank.getPaymentMode() != PaymentMode.BANK_TRANSFER) {
                reason = "payment mode is " + bank.getPaymentMode();
            } else if (bank.getBankAccountNumber() == null
                    || bank.getBankAccountNumber().isBlank()) {
                reason = "blank bank account number";
            } else if (bank.getIfscCode() == null || bank.getIfscCode().isBlank()) {
                reason = "blank IFSC code";
            } else if (netPay == null || netPay.compareTo(BigDecimal.ZERO) <= 0) {
                reason = "zero or negative net pay: " + netPay;
            }
            if (reason != null) {
                omissions.add(new BankFileOmission(employeeNumber, reason));
                omittedWithReason.add(employeeNumber + " (" + reason + ")");
                if (netPay != null) {
                    omittedNetPay = omittedNetPay.add(netPay);
                }
                continue;
            }

            rows.add(new BankFileRow(
                    employeeId,
                    employeeNumber,
                    formatEmployeeName(emp),
                    // W-88.2: a blank holder name is filled with the employee's name, and IFSC is upper-cased, so
                    // the one-click file and the built-in template are the same file.
                    bank.getAccountHolderName() != null
                                    && !bank.getAccountHolderName().isBlank()
                            ? bank.getAccountHolderName()
                            : formatEmployeeName(emp),
                    bank.getBankAccountNumber().trim(),
                    bank.getIfscCode().trim().toUpperCase(Locale.ROOT),
                    bank.getBankName() != null ? bank.getBankName() : "",
                    bank.getBankAccountType() != null
                            ? bank.getBankAccountType().name()
                            : "",
                    bank.getPaymentMode().name(),
                    netPay,
                    narration,
                    bank.getTransferType() != null ? bank.getTransferType().name() : "",
                    bank.creditNarrationText()));
        }

        if (!omittedWithReason.isEmpty()) {
            // No account numbers here: employee numbers and reasons only.
            log.warn(
                    "Pay run {}: {} employee(s) omitted from bank salary file, net pay not in file {}: {}",
                    payrunId,
                    omittedWithReason.size(),
                    omittedNetPay.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    String.join(", ", omittedWithReason));
        }

        rows.sort(
                Comparator.comparing(BankFileRow::employeeNumber, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        omissions.sort(Comparator.comparing(BankFileOmission::employeeNumber, String.CASE_INSENSITIVE_ORDER));
        return new BankFileRun(
                payRun, List.copyOf(rows), employeeMap, List.copyOf(omissions), runNetPay, selectedNetPay);
    }

    private static BigDecimal sumNetPay(List<EmployeePayRun> rows) {
        return rows.stream()
                .map(EmployeePayRun::getNetPay)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BankFileResult generate(UUID payrunId) {
        BankFileRun computed = run(payrunId, null);
        List<BankFileRow> rows = computed.rows();
        BigDecimal fileTotal = rows.stream().map(BankFileRow::netPay).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> rowMaps = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            BankFileRow row = rows.get(i);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sr_no", String.valueOf(i + 1));
            map.put("employee_number", row.employeeNumber());
            map.put("employee_name", row.employeeName());
            map.put("account_holder_name", row.accountHolderName());
            map.put("bank_account_number", row.bankAccountNumber());
            map.put("ifsc_code", row.ifscCode());
            map.put("bank_name", row.bankName());
            map.put("account_type", row.accountType());
            map.put("net_pay", row.netPay().setScale(2, RoundingMode.HALF_UP).toPlainString());
            map.put("narration", row.narration());
            rowMaps.add(map);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvWriter writer = new CsvWriter();
        try {
            writer.write(COLUMNS, rowMaps.stream(), out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write bank file CSV", e);
        }

        String filename = "bank_salary_file_" + computed.payRun().getPeriod() + ".csv";
        int exportedEmployees = rows.size();
        List<String> omittedEmployeeNumbers = computed.omissions().stream()
                .map(BankFileOmission::employeeNumber)
                .toList();
        int omittedEmployees = omittedEmployeeNumbers.size();
        return new BankFileResult(
                out.toByteArray(),
                filename,
                exportedEmployees + omittedEmployees,
                exportedEmployees,
                omittedEmployees,
                fileTotal,
                computed.runNetPay(),
                omittedEmployeeNumbers);
    }

    static String formatEmployeeName(Employee emp) {
        if (emp == null) {
            return "";
        }
        return Stream.of(emp.getFirstName(), emp.getMiddleName(), emp.getLastName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
    }
}
