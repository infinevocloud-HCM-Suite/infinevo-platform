package com.infinevo.core.org;

import com.infinevo.shared.authz.RequiresAction;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
}
