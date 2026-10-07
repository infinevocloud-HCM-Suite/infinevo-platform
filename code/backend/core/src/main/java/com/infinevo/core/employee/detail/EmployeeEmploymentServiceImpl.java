package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
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
 *
 * <p>D-41 tightened two fields the section took as given: {@code timeZone} must be a zone
 * {@link ZoneId#of} knows, and the two shift times must be whole minutes ({@code HH:mm}). D-40 added
 * the three employment terms, checked by {@link EmploymentTerms} — the probation date against the
 * employee's joining date, which is why that rule runs in {@link #validateAgainst}.
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
        String timeZone = optional(errors, "timeZone", request.timeZone(), MAX_TIME_ZONE);
        if (timeZone != null && !errors.containsKey("timeZone") && !isKnownZone(timeZone)) {
            errors.put("timeZone", "timeZone must be an IANA time zone such as Asia/Kolkata");
        }
        optional(errors, "note", request.note(), MAX_NOTE);
        checkWholeMinutes(errors, "shiftStartTime", request.shiftStartTime());
        checkWholeMinutes(errors, "shiftEndTime", request.shiftEndTime());
        checkShiftOrder(errors, request.shiftStartTime(), request.shiftEndTime());
        EmploymentTerms.check(errors, null, request.probationEndDate(), request.noticePeriodDays());
    }

    /** D-40: the probation end is measured from the joining date on the root record. */
    @Override
    protected void validateAgainst(EmployeeEmploymentRequest request, Employee employee, Map<String, String> errors) {
        EmploymentTerms.check(errors, employee.getDateOfJoining(), request.probationEndDate(), null);
    }

    /**
     * D-41: the zone is one {@link ZoneId#of} resolves, so the value can be used to compute a local
     * time later rather than only shown. {@code ZoneId.of} also accepts offsets such as
     * {@code +05:30}; those are real zones to it and are kept.
     */
    static boolean isKnownZone(String value) {
        try {
            ZoneId.of(value);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /**
     * D-41: a shift time is {@code HH:mm}. Jackson parses {@code "09:00:30"} into a {@link LocalTime}
     * as readily as {@code "09:00"}, so the shape is checked on the parsed value — no seconds and no
     * fraction.
     */
    static void checkWholeMinutes(Map<String, String> errors, String field, LocalTime value) {
        if (value != null && (value.getSecond() != 0 || value.getNano() != 0)) {
            errors.put(field, field + " must be HH:mm");
        }
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
                request.employmentType(),
                request.probationEndDate(),
                request.noticePeriodDays(),
                actor);
    }
}
