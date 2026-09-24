package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The bank section (W-13.2) — {@code core.employee_bank}, {@code V019__employee_bank.sql}.
 *
 * <p>The read / write flow, the tenant, the employee check and the create-or-replace rule are all on
 * {@link AbstractEmployeeDetailServiceImpl}. What is here is what only this section has: its column
 * widths and the one required field.
 *
 * <p>{@code paymentMode} is required because the column is {@code NOT NULL}. A value outside
 * {@link PaymentMode} never reaches this class — Jackson fails on it during deserialisation, and
 * {@code EmployeeDetailController} answers that with the same {@code 400} envelope.
 *
 * <p><strong>There is no validation of the IFSC against a real directory</strong> — spec section 2,
 * Out of scope. The column width is checked and nothing more; an IFSC that is well formed but names
 * no bank is a problem for the payment file, not for this endpoint.
 */
@Service
public class EmployeeBankServiceImpl
        extends AbstractEmployeeDetailServiceImpl<EmployeeBank, EmployeeBankRequest, EmployeeBankResponse>
        implements EmployeeBankService {

    // Every width is the one in V019__employee_bank.sql.
    private static final int MAX_ACCOUNT_HOLDER_NAME = 100;
    private static final int MAX_BANK_NAME = 128;
    private static final int MAX_IFSC = 20;
    private static final int MAX_ACCOUNT_NUMBER = 64;

    public EmployeeBankServiceImpl(EmployeeBankRepository repository, EmployeeRepository employees) {
        super(repository, employees);
    }

    @Override
    protected String kind() {
        return "bank";
    }

    @Override
    protected String uniqueEmployeeIndexName() {
        return "idx_employee_bank_tenant_employee";
    }

    @Override
    protected EmployeeBank newEntity(UUID tenantId, Employee employee, String actor) {
        return new EmployeeBank(tenantId, employee, actor);
    }

    @Override
    protected EmployeeBankResponse toResponse(EmployeeBank entity) {
        return EmployeeBankResponse.from(entity);
    }

    @Override
    protected void validate(EmployeeBankRequest request, Map<String, String> errors) {
        if (request.paymentMode() == null) {
            errors.put("paymentMode", "paymentMode is required");
        }
        optional(errors, "accountHolderName", request.accountHolderName(), MAX_ACCOUNT_HOLDER_NAME);
        optional(errors, "bankName", request.bankName(), MAX_BANK_NAME);
        optional(errors, "ifscCode", request.ifscCode(), MAX_IFSC);
        // A string, checked as a string. Nothing here parses it as a number: 0012345678 is not
        // 12345678, and the moment it becomes one the leading zeros are gone for good.
        optional(errors, "bankAccountNumber", request.bankAccountNumber(), MAX_ACCOUNT_NUMBER);
    }

    @Override
    protected void applyTo(EmployeeBank entity, EmployeeBankRequest request, String actor) {
        entity.apply(
                request.paymentMode(),
                trimToNull(request.accountHolderName()),
                trimToNull(request.bankName()),
                trimToNull(request.ifscCode()),
                trimToNull(request.bankAccountNumber()),
                request.bankAccountType(),
                actor);
    }
}
