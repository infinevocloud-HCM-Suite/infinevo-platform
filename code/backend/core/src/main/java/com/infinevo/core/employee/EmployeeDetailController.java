package com.infinevo.core.employee;

import com.infinevo.core.employee.detail.EmployeeBankRequest;
import com.infinevo.core.employee.detail.EmployeeBankResponse;
import com.infinevo.core.employee.detail.EmployeeBankService;
import com.infinevo.core.employee.detail.EmployeeContactRequest;
import com.infinevo.core.employee.detail.EmployeeContactResponse;
import com.infinevo.core.employee.detail.EmployeeContactService;
import com.infinevo.core.employee.detail.EmployeeDetailService;
import com.infinevo.core.employee.detail.EmployeeEmploymentRequest;
import com.infinevo.core.employee.detail.EmployeeEmploymentResponse;
import com.infinevo.core.employee.detail.EmployeeEmploymentService;
import com.infinevo.core.employee.detail.EmployeeIdentificationRequest;
import com.infinevo.core.employee.detail.EmployeeIdentificationResponse;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.employee.detail.EmployeePersonalRequest;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/employees/{id}/{section}} — the five detail sections of one employee (W-13.2, spec
 * section 4).
 *
 * <p>Thin by rule: unpack, delegate, repack — {@code docs/CONVENTIONS.md} section 3. Every decision
 * about what a section may hold is in the five service implementations.
 *
 * <p><strong>{@code PUT} and not {@code POST}.</strong> A section is part of an employee, created on
 * first write — there is no separate creation step, and no way to have two of one section. A
 * {@code POST} would imply both.
 *
 * <p><strong>No endpoint here names a tenant.</strong> There is no tenant path segment, no
 * {@code organizationId} header and no tenant field on any request body. The tenant is bound by
 * {@code TenantContextFilter} from the verified token before the request reaches this class, and the
 * database enforces it again through row-level security on all five tables. This is the difference
 * from the frozen system, where 57 methods take the organisation as a caller-supplied parameter
 * (DEBT-022).
 *
 * <p>One controller for the five sections rather than five, because the shape is identical and the
 * path is the same resource: splitting it would put the same four exception handlers in five files.
 *
 * <p>The exception handlers are local rather than a {@code @RestControllerAdvice}, for the reason
 * {@link EmployeeController} gives: there is no global advice in the platform yet, and inventing one
 * here would quietly set the error contract for every future controller from inside a feature
 * branch. They return the shared {@link ApiErrorResponse} envelope, so the shape is already the
 * common one when that advice arrives.
 *
 * <p><strong>Guarded per section</strong> (W-11.2): personal, contact and employment by
 * {@code core.employee.read} / {@code .update}; identification and bank by their own codes, which
 * fewer roles hold. The self-service {@code core.employee.update_own} is not honoured here yet — it
 * needs ownership, which no service decides today.
 */
@RestController
@RequestMapping("/api/v1/employees/{id}")
public class EmployeeDetailController {

    private final EmployeePersonalService personalService;
    private final EmployeeContactService contactService;
    private final EmployeeIdentificationService identificationService;
    private final EmployeeEmploymentService employmentService;
    private final EmployeeBankService bankService;

    public EmployeeDetailController(
            EmployeePersonalService personalService,
            EmployeeContactService contactService,
            EmployeeIdentificationService identificationService,
            EmployeeEmploymentService employmentService,
            EmployeeBankService bankService) {
        this.personalService = Objects.requireNonNull(personalService, "personalService must not be null");
        this.contactService = Objects.requireNonNull(contactService, "contactService must not be null");
        this.identificationService =
                Objects.requireNonNull(identificationService, "identificationService must not be null");
        this.employmentService = Objects.requireNonNull(employmentService, "employmentService must not be null");
        this.bankService = Objects.requireNonNull(bankService, "bankService must not be null");
    }

    @GetMapping("/personal")
    @RequiresAction("core.employee.read")
    public EmployeePersonalResponse getPersonal(@PathVariable("id") UUID id) {
        return personalService.get(id);
    }

    @PutMapping("/personal")
    @RequiresAction(value = "core.employee.update", anyOf = "core.employee.update_own")
    public EmployeePersonalResponse putPersonal(
            @PathVariable("id") UUID id, @RequestBody EmployeePersonalRequest request) {
        return personalService.put(id, request);
    }

    @GetMapping("/contact")
    @RequiresAction("core.employee.read")
    public EmployeeContactResponse getContact(@PathVariable("id") UUID id) {
        return contactService.get(id);
    }

    @PutMapping("/contact")
    @RequiresAction(value = "core.employee.update", anyOf = "core.employee.update_own")
    public EmployeeContactResponse putContact(
            @PathVariable("id") UUID id, @RequestBody EmployeeContactRequest request) {
        return contactService.put(id, request);
    }

    @GetMapping("/identification")
    @RequiresAction("core.employee_identification.read")
    public EmployeeIdentificationResponse getIdentification(@PathVariable("id") UUID id) {
        return identificationService.get(id);
    }

    @PutMapping("/identification")
    @RequiresAction("core.employee_identification.update")
    public EmployeeIdentificationResponse putIdentification(
            @PathVariable("id") UUID id, @RequestBody EmployeeIdentificationRequest request) {
        return identificationService.put(id, request);
    }

    @GetMapping("/employment")
    @RequiresAction("core.employee.read")
    public EmployeeEmploymentResponse getEmployment(@PathVariable("id") UUID id) {
        return employmentService.get(id);
    }

    @PutMapping("/employment")
    @RequiresAction("core.employee.update")
    public EmployeeEmploymentResponse putEmployment(
            @PathVariable("id") UUID id, @RequestBody EmployeeEmploymentRequest request) {
        return employmentService.put(id, request);
    }

    @GetMapping("/bank")
    @RequiresAction("core.employee_bank.read")
    public EmployeeBankResponse getBank(@PathVariable("id") UUID id) {
        return bankService.get(id);
    }

    @PutMapping("/bank")
    @RequiresAction("core.employee_bank.update")
    public EmployeeBankResponse putBank(@PathVariable("id") UUID id, @RequestBody EmployeeBankRequest request) {
        return bankService.put(id, request);
    }

    /**
     * {@code 404} — no such employee in the bound tenant, or no such section on it.
     *
     * <p>Both read the same to a caller by design. Distinguishing them would let one enumerate
     * another tenant's employee ids by watching which sentence comes back.
     */
    @ExceptionHandler(EmployeeDetailService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EmployeeDetailService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeDetailService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(EmployeeDetailService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    /**
     * {@code 409}, never a constraint-violation stack trace. Two writers created the same section at
     * the same moment and the unique index on {@code (tenant_id, employee_id)} refused the second —
     * which is the backstop that makes "never a second row per section per employee" true.
     */
    @ExceptionHandler(EmployeeDetailService.ConcurrentWriteException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrentWrite(EmployeeDetailService.ConcurrentWriteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    /**
     * A body Jackson could not read at all — malformed JSON, a date or time in the wrong form, or a
     * {@code paymentMode} or {@code bankAccountType} outside its vocabulary, all of which fail during
     * deserialisation and so never reach the service's validation.
     *
     * <p>Without this the request still fails safely with a {@code 400} and nothing reaches the
     * database, but it comes back in Spring's default body while every other error here uses
     * {@link ApiErrorResponse}. One endpoint answering in two shapes is the kind of thing a client
     * writes a special case for and never removes.
     *
     * <p>The exception's own message is not echoed: it carries the offending JSON and the internal
     * type names, and neither belongs in a response.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed, that dates"
                                + " are yyyy-MM-dd and times are HH:mm, that paymentMode is one of"
                                + " BANK_TRANSFER, CASH or CHEQUE, and that bankAccountType is one of"
                                + " SAVINGS, CURRENT or SALARY.",
                        traceId()));
    }

    /** The correlation id the logging filter put on this request, so a client can quote it. */
    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
