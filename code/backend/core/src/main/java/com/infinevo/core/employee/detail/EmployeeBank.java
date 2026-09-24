package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * How one employee is paid (W-13.2) — {@code core.employee_bank},
 * {@code migration/src/main/resources/db/migration/core/V019__employee_bank.sql}.
 *
 * <p>The schema is named on the table: {@code code/backend/app/src/main/resources/application.yml}
 * sets no {@code default_schema}, deliberately, so every entity declares its own.
 *
 * <p>Payroll is the whole shape here —
 * {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/EmployeeBankDetail.java:16-34}.
 * HRMS models no bank detail at all (spec section 1).
 *
 * <p><strong>{@code bankAccountNumber} is a {@link String} and must never be numeric</strong> — spec
 * section 9, and it is irreversible if wrong. An account number is a string of digits, not a number:
 * {@code 0012345678} is not {@code 12345678}, and a numeric column loses the leading zeros on the way
 * in with nothing to recover them from. It is not money either, so
 * {@code docs/CONVENTIONS.md} section 2 has nothing to say about it — this table creates no money
 * column at all.
 *
 * <p>{@code paymentMode} and {@code bankAccountType} are enumerations ({@link PaymentMode},
 * {@link BankAccountType}) over {@code varchar} columns, mapped {@link EnumType#STRING} so Hibernate
 * writes the name. The frozen columns are free text ({@code EmployeeBankDetail.java:18}, {@code :34}).
 * There is deliberately no {@code CHECK} constraint — see {@link PaymentMode} for what that costs and
 * why W-67 owns it.
 *
 * <p><strong>{@link Audited}, and this table is why the redaction fix had to land first</strong>
 * (spec section 2, the founder's addition). W-22.1 shipped the capture mechanism and it had recorded
 * nothing, because no production class carried the annotation; annotating this one before fixing the
 * multi-column fallback would have written an account number into {@code core.audit_log} in clear.
 * {@code bank_account_number} and {@code ifsc_code} are both matched by
 * {@code AuditWriter.REDACTED_FRAGMENTS} — <strong>renaming either column silently un-redacts it</strong>,
 * so do not rename them without changing that list.
 */
@Entity
@Table(
        name = "employee_bank",
        schema = "core",
        indexes = {
            @Index(name = "idx_employee_bank_tenant_employee", columnList = "tenant_id, employee_id", unique = true)
        })
@Audited
public class EmployeeBank extends EmployeeDetail {

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 32)
    private PaymentMode paymentMode;

    @Column(name = "account_holder_name", length = 100)
    private String accountHolderName;

    @Column(name = "bank_name", length = 128)
    private String bankName;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(name = "bank_account_number", length = 64)
    private String bankAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_account_type", length = 32)
    private BankAccountType bankAccountType;

    protected EmployeeBank() {}

    EmployeeBank(UUID tenantId, Employee employee, String actor) {
        super(tenantId, employee, actor);
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public String getBankName() {
        return bankName;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public BankAccountType getBankAccountType() {
        return bankAccountType;
    }

    /**
     * Copies the mutable fields in and stamps the row.
     *
     * <p>Package-private, and called only by {@link EmployeeBankServiceImpl} once it has validated
     * the request. Neither the id, the tenant nor the employee is reachable from here.
     */
    void apply(
            PaymentMode paymentMode,
            String accountHolderName,
            String bankName,
            String ifscCode,
            String bankAccountNumber,
            BankAccountType bankAccountType,
            String actor) {
        this.paymentMode = paymentMode;
        this.accountHolderName = accountHolderName;
        this.bankName = bankName;
        this.ifscCode = ifscCode;
        this.bankAccountNumber = bankAccountNumber;
        this.bankAccountType = bankAccountType;
        stamp(actor);
    }
}
