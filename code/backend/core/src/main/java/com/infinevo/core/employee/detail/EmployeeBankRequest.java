package com.infinevo.core.employee.detail;

/**
 * What a client may say about how an employee is paid (W-13.2, spec section 4).
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before this record is read.
 *
 * <p>This is a replace, not a patch. Every field omitted is written as null, which is how a value is
 * cleared.
 *
 * @param paymentMode required — the column is {@code NOT NULL}
 *     ({@code V019__employee_bank.sql}). A value outside {@link PaymentMode} fails during
 *     deserialisation, so it never reaches the service; {@code EmployeeDetailController} answers that
 *     with the same {@code 400} envelope as every other error.
 * @param bankAccountNumber a {@link String}, never a number — spec section 9. {@code 0012345678} is
 *     not {@code 12345678}, and a numeric type loses the leading zeros with nothing to recover them
 *     from.
 */
public record EmployeeBankRequest(
        PaymentMode paymentMode,
        String accountHolderName,
        String bankName,
        String ifscCode,
        String bankAccountNumber,
        BankAccountType bankAccountType) {}
