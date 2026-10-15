package com.infinevo.payroll.payrun;

import com.infinevo.core.report.ReportColumn;
import com.infinevo.core.report.XlsxStreamingWriter;
import com.infinevo.payroll.payslip.PayRunPaymentRequest;
import com.infinevo.payroll.schedule.NoPayScheduleException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-29.1 §4, W-29.2 §4 and W-29.4 §4. Create, read, list, lock, cancel, compute — queued, answered
 * with {@code 202} — and one employee's lines. For both run types; W-30.2 adds the {@code runType}
 * filter here and the two off-cycle endpoints in {@link OffCyclePayRunController}. Not ported: the legacy free {@code PUT} that copied
 * {@code status} from the body, {@code DELETE} (cancel instead — the row stays) and
 * {@code GET /completed} (a {@code status} filter).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/payruns")
public class PayRunController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PayRunService payRunService;

    public PayRunController(PayRunService payRunService) {
        this.payRunService = Objects.requireNonNull(payRunService, "payRunService must not be null");
    }

    @PostMapping
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> create(@RequestBody CreatePayRunRequest request) {
        PayRunResponse created = payRunService.create(parsePeriod(request == null ? null : request.period()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PayRunApiResponse.created("Pay run created successfully", created));
    }

    @GetMapping
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<Page<PayRunResponse>>> list(
            @RequestParam(name = "status", required = false) PayRunStatus status,
            @RequestParam(name = "runType", required = false) PayRunType runType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "period").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(
                PayRunApiResponse.ok("Pay runs retrieved successfully", payRunService.list(status, runType, pageable)));
    }

    @GetMapping("/{id}")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run retrieved successfully", payRunService.get(id)));
    }

    @GetMapping("/{id}/employees")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<Page<EmployeePayRunResponse>>> employees(
            @PathVariable("id") UUID id,
            @RequestParam(name = "inclusion", required = false) InclusionStatus inclusion,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        PageRequest pageable =
                PageRequest.of(Math.max(0, page), clampSize(size), Sort.by(Sort.Direction.ASC, "createdAt", "id"));
        return ResponseEntity.ok(PayRunApiResponse.ok(
                "Pay run employees retrieved successfully", payRunService.employees(id, inclusion, pageable)));
    }

    @GetMapping("/{id}/export")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<byte[]> export(@PathVariable("id") UUID id) throws IOException {
        PayRunResponse run = payRunService.get(id);
        Page<EmployeePayRunResponse> employees = payRunService.employees(id, null, PageRequest.of(0, 5000));

        List<ReportColumn> columns = List.of(
                ReportColumn.text("employee_number", "Employee Number"),
                ReportColumn.text("employee_name", "Employee Name"),
                ReportColumn.text("inclusion_status", "Inclusion Status"),
                ReportColumn.text("skip_reason", "Skip Reason"),
                ReportColumn.text("paid_days", "Paid Days"),
                ReportColumn.text("lop_days", "LOP Days"),
                ReportColumn.text("gross_earnings", "Gross Earnings"),
                ReportColumn.text("total_deductions", "Total Deductions"),
                ReportColumn.text("total_reimbursements", "Total Reimbursements"),
                ReportColumn.text("net_pay", "Net Pay"));

        Stream<Map<String, Object>> rows = employees.getContent().stream().map(emp -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("employee_number", emp.employeeNumber() != null ? emp.employeeNumber() : "");
            map.put("employee_name", emp.employeeName() != null ? emp.employeeName() : "");
            map.put(
                    "inclusion_status",
                    emp.inclusionStatus() != null ? emp.inclusionStatus().name() : "");
            map.put("skip_reason", emp.skipReason() != null ? emp.skipReason().name() : "");
            map.put("paid_days", emp.paidDays() != null ? emp.paidDays().toPlainString() : "0");
            map.put("lop_days", emp.lopDays() != null ? emp.lopDays().toPlainString() : "0");
            map.put(
                    "gross_earnings",
                    emp.grossEarnings() != null ? emp.grossEarnings().toPlainString() : "0.00");
            map.put(
                    "total_deductions",
                    emp.totalDeductions() != null ? emp.totalDeductions().toPlainString() : "0.00");
            map.put(
                    "total_reimbursements",
                    emp.totalReimbursements() != null
                            ? emp.totalReimbursements().toPlainString()
                            : "0.00");
            map.put("net_pay", emp.netPay() != null ? emp.netPay().toPlainString() : "0.00");
            return map;
        });

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new XlsxStreamingWriter().write(columns, rows, baos);

        String filename = "payrun-" + run.period() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(baos.toByteArray());
    }

    @PostMapping("/{id}/lock")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> lock(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run locked successfully", payRunService.lock(id)));
    }

    @PostMapping("/{id}/cancel")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> cancel(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run cancelled successfully", payRunService.cancel(id)));
    }

    @PostMapping("/{id}/compute")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<ComputeAcceptedResponse>> compute(@PathVariable("id") UUID id) {
        ComputeAcceptedResponse accepted = payRunService.compute(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(PayRunApiResponse.accepted("Pay run computation queued", accepted));
    }

    @GetMapping("/{id}/employees/{employeeId}/lines")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<EmployeePayRunLinesResponse>> lines(
            @PathVariable("id") UUID id, @PathVariable("employeeId") UUID employeeId) {
        return ResponseEntity.ok(
                PayRunApiResponse.ok("Pay run lines retrieved successfully", payRunService.lines(id, employeeId)));
    }

    @PostMapping("/{id}/approve")
    @RequiresAction("payroll.run.approve")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> approve(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(PayRunApiResponse.ok("Pay run approved successfully", payRunService.approve(id)));
    }

    @PostMapping("/{id}/pay")
    @RequiresAction("payroll.payslip.publish")
    public ResponseEntity<PayRunApiResponse<PayRunResponse>> pay(
            @PathVariable("id") UUID id, @RequestBody PayRunPaymentRequest request) {
        if (request == null || request.paidOn() == null) {
            throw new IllegalArgumentException("paid_on is required, as YYYY-MM-DD");
        }
        return ResponseEntity.ok(
                PayRunApiResponse.ok("Pay run paid successfully", payRunService.pay(id, request.paidOn())));
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayRunNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
        DuplicatePayRunException.class,
        IllegalPayRunTransitionException.class,
        PayRunComputeInProgressException.class,
        com.infinevo.payroll.priorpayroll.PriorPayrollExistsException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PayRunEnqueueException.class)
    public ResponseEntity<ApiErrorResponse> handleEnqueue(PayRunEnqueueException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, ex.getMessage());
    }

    @ExceptionHandler(NoPayScheduleException.class)
    public ResponseEntity<ApiErrorResponse> handleNoSchedule(NoPayScheduleException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
    }

    private static YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period is required, as YYYY-MM");
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("period must be YYYY-MM, was " + period, e);
        }
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(
                        code,
                        message,
                        traceId != null ? traceId : UUID.randomUUID().toString()));
    }
}
