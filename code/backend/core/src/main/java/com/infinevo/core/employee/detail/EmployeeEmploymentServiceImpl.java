package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The employment section (W-13.2) — {@code core.employee_employment},
 * {@code V018__employee_employment.sql}.
 *
 * <p>The read / write flow, the tenant, the employee check and the create-or-replace rule are all on
 * {@link AbstractEmployeeDetailServiceImpl}. What is here is what only this section has: its column
 * widths and the shift-order rule.
 *
 * <p>Department, designation, joining date and termination date are not written here. They are on
 * the root record, and a second copy of a joining date is how two answers to "when did this person
 * start" get written — {@link EmployeeEmployment} and {@code V018__employee_employment.sql} name each
 * of the four and where it lives.
 */
@Service
public class EmployeeEmploymentServiceImpl
        extends AbstractEmployeeDetailServiceImpl<
                EmployeeEmployment, EmployeeEmploymentRequest, EmployeeEmploymentResponse>
        implements EmployeeEmploymentService {

    // Every width is the one in V018__employee_employment.sql.
    private static final int MAX_PAY_GRADE = 64;
    private static final int MAX_WORKSTATION_ID = 64;
    private static final int MAX_TIME_ZONE = 64;
    private static final int MAX_NOTE = 1000;

    public EmployeeEmploymentServiceImpl(EmployeeEmploymentRepository repository, EmployeeRepository employees) {
        super(repository, employees);
    }

    @Override
    protected String kind() {
        return "employment";
    }

    @Override
    protected String uniqueEmployeeIndexName() {
        return "idx_employee_employment_tenant_employee";
    }

    @Override
    protected EmployeeEmployment newEntity(UUID tenantId, Employee employee, String actor) {
        return new EmployeeEmployment(tenantId, employee, actor);
    }

    @Override
    protected EmployeeEmploymentResponse toResponse(EmployeeEmployment entity) {
        return EmployeeEmploymentResponse.from(entity);
    }

    @Override
    protected void validate(EmployeeEmploymentRequest request, Map<String, String> errors) {
        optional(errors, "payGrade", request.payGrade(), MAX_PAY_GRADE);
        optional(errors, "workstationId", request.workstationId(), MAX_WORKSTATION_ID);
        optional(errors, "timeZone", request.timeZone(), MAX_TIME_ZONE);
        optional(errors, "note", request.note(), MAX_NOTE);
        checkShiftOrder(errors, request.shiftStartTime(), request.shiftEndTime());
    }

    @Override
    protected void applyTo(EmployeeEmployment entity, EmployeeEmploymentRequest request, String actor) {
        entity.apply(
                trimToNull(request.payGrade()),
                trimToNull(request.workstationId()),
                trimToNull(request.timeZone()),
                request.shiftStartTime(),
                request.shiftEndTime(),
                trimToNull(request.note()),
                actor);
    }
}
