package com.infinevo.payroll.payrun.bankfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.detail.BankAccountType;
import com.infinevo.core.employee.detail.EmployeeBank;
import com.infinevo.core.employee.detail.EmployeeBankRepository;
import com.infinevo.core.employee.detail.PaymentMode;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BankFileServiceImpl} (W-88).
 * Validates extraction, batch loading, filtering, sorting, formatting and RFC 4180 CSV export.
 */
class BankFileServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String HEADER =
            "Sr No,Employee Number,Employee Name,Account Holder Name,Bank Account Number,IFSC Code,Bank Name,Account Type,Net Pay (INR),Narration";

    private PayRunRepository payRunRepository;
    private EmployeePayRunRepository employeePayRunRepository;
    private EmployeeRepository employeeRepository;
    private EmployeeBankRepository employeeBankRepository;
    private BankFileServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        payRunRepository = mock(PayRunRepository.class);
        employeePayRunRepository = mock(EmployeePayRunRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        employeeBankRepository = mock(EmployeeBankRepository.class);

        service = new BankFileServiceImpl(
                payRunRepository, employeePayRunRepository, employeeRepository, employeeBankRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("1. Happy path: generate bank file for APPROVED pay run with 3 employees")
    void generate_happyPath_returnsValidCsv() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.APPROVED, "2026-11");

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID emp3Id = UUID.randomUUID();

        Employee emp1 = mockEmployee(emp1Id, "EMP001", "Rajesh", "Sharma");
        Employee emp2 = mockEmployee(emp2Id, "EMP002", "Priya", "Patel");
        Employee emp3 = mockEmployee(emp3Id, "EMP003", "Amit", "Kumar");

        EmployeeBank bank1 = mockBank(
                emp1Id,
                PaymentMode.BANK_TRANSFER,
                "Rajesh Sharma",
                "001234567890",
                "HDFC0001234",
                "HDFC Bank",
                BankAccountType.SALARY);
        EmployeeBank bank2 = mockBank(
                emp2Id,
                PaymentMode.BANK_TRANSFER,
                "Priya Patel",
                "987654321012",
                "ICIC0000456",
                "ICICI Bank",
                BankAccountType.SAVINGS);
        EmployeeBank bank3 = mockBank(
                emp3Id,
                PaymentMode.BANK_TRANSFER,
                "Amit Kumar",
                "112233445566",
                "SBIN0000789",
                "SBI",
                BankAccountType.SALARY);

        EmployeePayRun epr1 = mockEmployeePayRun(emp1Id, new BigDecimal("65000.0000"));
        EmployeePayRun epr2 = mockEmployeePayRun(emp2Id, new BigDecimal("78500.5000"));
        EmployeePayRun epr3 = mockEmployeePayRun(emp3Id, new BigDecimal("52340.2500"));

        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, payrunId, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2, epr3));
        when(employeeRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(emp1, emp2, emp3));
        when(employeeBankRepository.findByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(bank1, bank2, bank3));

        BankFileResult result = service.generate(payrunId);
        String csvContent = new String(result.content(), StandardCharsets.UTF_8);

        assertThat(csvContent.charAt(0)).isEqualTo('\uFEFF'); // UTF-8 BOM
        String[] lines = csvContent.substring(1).split("\r\n");

        assertThat(lines).hasSize(4);
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines[1])
                .isEqualTo(
                        "1,EMP001,Rajesh Sharma,Rajesh Sharma,001234567890,HDFC0001234,HDFC Bank,SALARY,65000.00,Salary 2026-11");
        assertThat(lines[2])
                .isEqualTo(
                        "2,EMP002,Priya Patel,Priya Patel,987654321012,ICIC0000456,ICICI Bank,SAVINGS,78500.50,Salary 2026-11");
        assertThat(lines[3])
                .isEqualTo(
                        "3,EMP003,Amit Kumar,Amit Kumar,112233445566,SBIN0000789,SBI,SALARY,52340.25,Salary 2026-11");

        assertThat(result.totalEmployees()).isEqualTo(3);
        assertThat(result.exportedEmployees()).isEqualTo(3);
        assertThat(result.omittedEmployees()).isEqualTo(0);
        assertThat(result.fileTotal()).isEqualByComparingTo(new BigDecimal("195840.75"));
    }

    @Test
    @DisplayName("2. Terminal status: generate bank file for PAID pay run works")
    void generate_paidStatus_works() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.PAID, "2026-10");

        UUID empId = UUID.randomUUID();
        Employee emp = mockEmployee(empId, "EMP001", "Rajesh", "Sharma");
        EmployeeBank bank = mockBank(
                empId,
                PaymentMode.BANK_TRANSFER,
                "Rajesh Sharma",
                "123456789",
                "HDFC0001234",
                "HDFC",
                BankAccountType.SALARY);
        EmployeePayRun epr = mockEmployeePayRun(empId, new BigDecimal("50000.0000"));

        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, payrunId, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr));
        when(employeeRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(emp));
        when(employeeBankRepository.findByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(bank));

        List<BankFileRow> rows = service.getRows(payrunId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).employeeNumber()).isEqualTo("EMP001");
        assertThat(rows.get(0).narration()).isEqualTo("Salary 2026-10");
    }

    @Test
    @DisplayName("3. Non-finalized pay run status throws BankFileInvalidStatusException")
    void getRows_draftStatus_throwsBankFileInvalidStatusException() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.DRAFT, "2026-11");
        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));

        assertThatThrownBy(() -> service.getRows(payrunId))
                .isInstanceOf(BankFileInvalidStatusException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("4. COMPUTED pay run status throws BankFileInvalidStatusException")
    void getRows_computedStatus_throwsBankFileInvalidStatusException() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.COMPUTED, "2026-11");
        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));

        assertThatThrownBy(() -> service.getRows(payrunId))
                .isInstanceOf(BankFileInvalidStatusException.class)
                .hasMessageContaining("COMPUTED");
    }

    @Test
    @DisplayName("5. Excludes employees with CASH or CHEQUE payment modes")
    void getRows_excludesCashAndCheque() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.APPROVED, "2026-11");

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID emp3Id = UUID.randomUUID();

        Employee emp1 = mockEmployee(emp1Id, "EMP001", "Bank", "User");
        Employee emp2 = mockEmployee(emp2Id, "EMP002", "Cash", "User");
        Employee emp3 = mockEmployee(emp3Id, "EMP003", "Cheque", "User");

        EmployeeBank bank1 = mockBank(
                emp1Id, PaymentMode.BANK_TRANSFER, "Bank User", "111", "HDFC0001", "HDFC", BankAccountType.SALARY);
        EmployeeBank bank2 = mockBank(emp2Id, PaymentMode.CASH, null, null, null, null, null);
        EmployeeBank bank3 = mockBank(emp3Id, PaymentMode.CHEQUE, null, null, null, null, null);

        EmployeePayRun epr1 = mockEmployeePayRun(emp1Id, new BigDecimal("50000"));
        EmployeePayRun epr2 = mockEmployeePayRun(emp2Id, new BigDecimal("25000"));
        EmployeePayRun epr3 = mockEmployeePayRun(emp3Id, new BigDecimal("30000"));

        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, payrunId, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2, epr3));
        when(employeeRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(emp1, emp2, emp3));
        when(employeeBankRepository.findByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(bank1, bank2, bank3));

        List<BankFileRow> rows = service.getRows(payrunId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).employeeNumber()).isEqualTo("EMP001");
    }

    @Test
    @DisplayName("6. Excludes employees with blank account number, blank IFSC, or zero net pay (Points 5 & 6)")
    void getRows_excludesBlankBankDetailsOrZeroNetPay() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.APPROVED, "2026-11");

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID emp3Id = UUID.randomUUID();

        Employee emp1 = mockEmployee(emp1Id, "EMP001", "Valid", "User");
        Employee emp2 = mockEmployee(emp2Id, "EMP002", "Blank", "Account");
        Employee emp3 = mockEmployee(emp3Id, "EMP003", "Zero", "Pay");

        EmployeeBank bank1 = mockBank(
                emp1Id, PaymentMode.BANK_TRANSFER, "Valid User", "123456", "HDFC0001", "HDFC", BankAccountType.SALARY);
        EmployeeBank bank2 = mockBank(
                emp2Id, PaymentMode.BANK_TRANSFER, "Blank Account", "   ", "HDFC0001", "HDFC", BankAccountType.SALARY);
        EmployeeBank bank3 = mockBank(
                emp3Id, PaymentMode.BANK_TRANSFER, "Zero Pay", "654321", "HDFC0001", "HDFC", BankAccountType.SALARY);

        EmployeePayRun epr1 = mockEmployeePayRun(emp1Id, new BigDecimal("50000"));
        EmployeePayRun epr2 = mockEmployeePayRun(emp2Id, new BigDecimal("40000"));
        EmployeePayRun epr3 = mockEmployeePayRun(emp3Id, BigDecimal.ZERO);

        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, payrunId, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2, epr3));
        when(employeeRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(emp1, emp2, emp3));
        when(employeeBankRepository.findByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(bank1, bank2, bank3));

        BankFileResult result = service.generate(payrunId);
        assertThat(result.exportedEmployees()).isEqualTo(1);
        assertThat(result.omittedEmployees()).isEqualTo(2);
        assertThat(result.fileTotal()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.runNetPay()).isEqualByComparingTo(new BigDecimal("90000.00"));
    }

    @Test
    @DisplayName("7. Empty pay run returns empty list and CSV header only")
    void generate_emptyPayRun_returnsHeaderOnly() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.APPROVED, "2026-11");

        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, payrunId, InclusionStatus.INCLUDED))
                .thenReturn(List.of());

        BankFileResult result = service.generate(payrunId);
        String csvContent = new String(result.content(), StandardCharsets.UTF_8);

        assertThat(csvContent.charAt(0)).isEqualTo('\uFEFF');
        String[] lines = csvContent.substring(1).split("\r\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).isEqualTo(HEADER);
    }

    @Test
    @DisplayName("8. Sorts rows ascending by employee number case-insensitively")
    void getRows_sortsByEmployeeNumber() {
        UUID payrunId = UUID.randomUUID();
        PayRun payRun = mockPayRun(payrunId, PayRunStatus.APPROVED, "2026-11");

        UUID emp1Id = UUID.randomUUID();
        UUID emp2Id = UUID.randomUUID();
        UUID emp3Id = UUID.randomUUID();

        Employee emp1 = mockEmployee(emp1Id, "EMP100", "A", "A");
        Employee emp2 = mockEmployee(emp2Id, "EMP005", "B", "B");
        Employee emp3 = mockEmployee(emp3Id, "EMP020", "C", "C");

        EmployeeBank bank1 =
                mockBank(emp1Id, PaymentMode.BANK_TRANSFER, "A", "1", "HDFC01", "HDFC", BankAccountType.SALARY);
        EmployeeBank bank2 =
                mockBank(emp2Id, PaymentMode.BANK_TRANSFER, "B", "2", "HDFC01", "HDFC", BankAccountType.SALARY);
        EmployeeBank bank3 =
                mockBank(emp3Id, PaymentMode.BANK_TRANSFER, "C", "3", "HDFC01", "HDFC", BankAccountType.SALARY);

        EmployeePayRun epr1 = mockEmployeePayRun(emp1Id, new BigDecimal("10000"));
        EmployeePayRun epr2 = mockEmployeePayRun(emp2Id, new BigDecimal("20000"));
        EmployeePayRun epr3 = mockEmployeePayRun(emp3Id, new BigDecimal("30000"));

        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.of(payRun));
        when(employeePayRunRepository.findAllByTenantIdAndPayrunIdAndInclusionStatus(
                        TENANT_ID, payrunId, InclusionStatus.INCLUDED))
                .thenReturn(List.of(epr1, epr2, epr3));
        when(employeeRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(emp1, emp2, emp3));
        when(employeeBankRepository.findByTenantIdAndEmployeeIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(bank1, bank2, bank3));

        List<BankFileRow> rows = service.getRows(payrunId);
        assertThat(rows).extracting(BankFileRow::employeeNumber).containsExactly("EMP005", "EMP020", "EMP100");
    }

    @Test
    @DisplayName("9. PayRun not found throws PayRunNotFoundException")
    void getRows_payRunNotFound_throws() {
        UUID payrunId = UUID.randomUUID();
        when(payRunRepository.findByIdAndTenantId(payrunId, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRows(payrunId)).isInstanceOf(PayRunNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PayRun mockPayRun(UUID id, PayRunStatus status, String period) {
        PayRun run = mock(PayRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getStatus()).thenReturn(status);
        when(run.getPeriod()).thenReturn(java.time.YearMonth.parse(period));
        return run;
    }

    private Employee mockEmployee(UUID id, String empNumber, String firstName, String lastName) {
        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(id);
        when(emp.getEmployeeNumber()).thenReturn(empNumber);
        when(emp.getFirstName()).thenReturn(firstName);
        when(emp.getLastName()).thenReturn(lastName);
        return emp;
    }

    private EmployeeBank mockBank(
            UUID empId,
            PaymentMode mode,
            String holder,
            String accountNum,
            String ifsc,
            String bankName,
            BankAccountType type) {
        EmployeeBank bank = mock(EmployeeBank.class);
        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(empId);
        when(bank.getEmployee()).thenReturn(emp);
        when(bank.getPaymentMode()).thenReturn(mode);
        when(bank.getAccountHolderName()).thenReturn(holder);
        when(bank.getBankAccountNumber()).thenReturn(accountNum);
        when(bank.getIfscCode()).thenReturn(ifsc);
        when(bank.getBankName()).thenReturn(bankName);
        when(bank.getBankAccountType()).thenReturn(type);
        return bank;
    }

    private EmployeePayRun mockEmployeePayRun(UUID empId, BigDecimal netPay) {
        EmployeePayRun epr = mock(EmployeePayRun.class);
        when(epr.getEmployeeId()).thenReturn(empId);
        when(epr.getNetPay()).thenReturn(netPay);
        when(epr.getInclusionStatus()).thenReturn(InclusionStatus.INCLUDED);
        return epr;
    }
}
