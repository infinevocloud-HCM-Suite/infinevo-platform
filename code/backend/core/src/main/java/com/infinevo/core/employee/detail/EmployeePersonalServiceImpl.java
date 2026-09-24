package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The personal section (W-13.2) — {@code core.employee_personal}, {@code V015__employee_personal.sql}.
 *
 * <p>The read / write flow, the tenant, the employee check and the create-or-replace rule are all on
 * {@link AbstractEmployeeDetailServiceImpl}. What is here is what only this section has: its column
 * widths and how its request lands on its entity.
 */
@Service
public class EmployeePersonalServiceImpl
        extends AbstractEmployeeDetailServiceImpl<EmployeePersonal, EmployeePersonalRequest, EmployeePersonalResponse>
        implements EmployeePersonalService {

    // Every width is the one in V015__employee_personal.sql. They are named here rather than read
    // from the entity because the entity's @Column length is the same fact stated twice: if one is
    // changed without the other, the validation message stops matching the column.
    private static final int MAX_MARITAL_STATUS = 32;
    private static final int MAX_NATIONALITY = 64;
    private static final int MAX_ETHNICITY = 64;
    private static final int MAX_FATHER_NAME = 100;
    private static final int MAX_DIFFERENTLY_ABLED_TYPE = 64;

    public EmployeePersonalServiceImpl(EmployeePersonalRepository repository, EmployeeRepository employees) {
        super(repository, employees);
    }

    @Override
    protected String kind() {
        return "personal";
    }

    @Override
    protected String uniqueEmployeeIndexName() {
        return "idx_employee_personal_tenant_employee";
    }

    @Override
    protected EmployeePersonal newEntity(UUID tenantId, Employee employee, String actor) {
        return new EmployeePersonal(tenantId, employee, actor);
    }

    @Override
    protected EmployeePersonalResponse toResponse(EmployeePersonal entity) {
        return EmployeePersonalResponse.from(entity);
    }

    @Override
    protected void validate(EmployeePersonalRequest request, Map<String, String> errors) {
        optional(errors, "maritalStatus", request.maritalStatus(), MAX_MARITAL_STATUS);
        optional(errors, "nationality", request.nationality(), MAX_NATIONALITY);
        optional(errors, "ethnicity", request.ethnicity(), MAX_ETHNICITY);
        optional(errors, "fatherName", request.fatherName(), MAX_FATHER_NAME);
        optional(errors, "differentlyAbledType", request.differentlyAbledType(), MAX_DIFFERENTLY_ABLED_TYPE);
    }

    @Override
    protected void applyTo(EmployeePersonal entity, EmployeePersonalRequest request, String actor) {
        entity.apply(
                request.dateOfBirth(),
                trimToNull(request.maritalStatus()),
                trimToNull(request.nationality()),
                trimToNull(request.ethnicity()),
                trimToNull(request.fatherName()),
                trimToNull(request.differentlyAbledType()),
                // The column is NOT NULL DEFAULT false, so an omitted flag is false and not an error.
                request.eligibleForFullTaxExemption() != null && request.eligibleForFullTaxExemption(),
                actor);
    }
}
