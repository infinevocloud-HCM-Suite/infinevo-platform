package com.infinevo.core.org;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for reporting lines, manager chains, and organizational chart read models (W-14.2, spec section 4).
 */
@RestController
public class ReportingLineController {

    private final ReportingLineService reportingLineService;
    private final OrgChartService orgChartService;

    public ReportingLineController(ReportingLineService reportingLineService, OrgChartService orgChartService) {
        this.reportingLineService =
                Objects.requireNonNull(reportingLineService, "reportingLineService must not be null");
        this.orgChartService = Objects.requireNonNull(orgChartService, "orgChartService must not be null");
    }

    @PutMapping("/api/v1/employees/{id}/reporting-line")
    @RequiresAction("core.reporting_line.manage")
    public ReportingLineResponse putReportingLine(
            @PathVariable("id") UUID employeeId, @RequestBody ReportingLineRequest request) {
        return reportingLineService.putReportingLine(employeeId, request);
    }

    @GetMapping("/api/v1/employees/{id}/reporting-line")
    @RequiresAction("core.org.read")
    public List<ReportingLineResponse> getReportingLines(
            @PathVariable("id") UUID employeeId,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return reportingLineService.getReportingLines(employeeId, asOf);
    }

    @GetMapping("/api/v1/employees/{id}/manager-chain")
    @RequiresAction("core.org.read")
    public List<ReportingLineResponse> getManagerChain(
            @PathVariable("id") UUID employeeId,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return reportingLineService.getManagerChain(employeeId, asOf);
    }

    @GetMapping("/api/v1/org-chart")
    @RequiresAction("core.org.read")
    public OrgChartNodeResponse getOrgChart(
            @RequestParam(name = "rootEmployeeId") UUID rootEmployeeId,
            @RequestParam(name = "depth", required = false) Integer depth) {
        return orgChartService.getOrgChart(rootEmployeeId, depth);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
