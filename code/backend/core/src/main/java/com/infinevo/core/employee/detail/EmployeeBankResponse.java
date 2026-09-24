package com.infinevo.core.employee.detail;

import java.time.Instant;
import java.util.UUID;

/**
 * How an employee is paid, as the API returns it (W-13.2, spec section 4).
 *
 * <p>The account number is returned in full, for the reason
 * {@link EmployeeIdentificationResponse} gives: redaction applies to {@code core.audit_log}, not to
 * the record the section exists to hold.
 */
public record EmployeeBankResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        PaymentMode paymentMode,
        String accountHolderName,
        String bankName,
        String ifscCode,
        String bankAccountNumber,
        BankAccountType bankAccountType,
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted section. The only way one of these is built. */
    public static EmployeeBankResponse from(EmployeeBank bank) {
        return new EmployeeBankResponse(
                bank.getId(),
                bank.getTenantId(),
                bank.getEmployee().getId(),
                bank.getPaymentMode(),
                bank.getAccountHolderName(),
                bank.getBankName(),
                bank.getIfscCode(),
                bank.getBankAccountNumber(),
                bank.getBankAccountType(),
                bank.getCreatedAt(),
                bank.getUpdatedAt());
    }
}
