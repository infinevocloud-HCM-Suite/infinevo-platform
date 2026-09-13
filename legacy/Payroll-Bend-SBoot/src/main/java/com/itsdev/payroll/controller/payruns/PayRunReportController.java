// Create a new controller or add to existing PayRunController.java

package com.itsdev.payroll.controller.payruns;

import com.itsdev.payroll.service.ExcelReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/payruns")
public class PayRunReportController {

    private final ExcelReportService excelReportService;
    private static final Logger log = LoggerFactory.getLogger(PayRunReportController.class);

    public PayRunReportController(ExcelReportService excelReportService) {
        this.excelReportService = excelReportService;
    }

    @GetMapping("/report/excel")
    public ResponseEntity<byte[]> downloadPayRunReport(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        String method = "downloadPayRunReport";
        log.info("[{}] 📥 Request received for Excel report | orgId={} | from={} | to={}",
                method, organizationId, fromDate, toDate);

        try {
            // Generate Excel report
            byte[] excelData = excelReportService.generateAllPayRunsReport(organizationId, fromDate, toDate);

            // Create file name
            String fileName;
            if (fromDate != null && toDate != null) {
                fileName = String.format("PayRun_Report_%s_to_%s.xlsx",
                        fromDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                        toDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            } else {
                fileName = "PayRun_Complete_Report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                        + ".xlsx";
            }

            log.info("[{}] ✅ Excel report generated successfully | size={} bytes | fileName={}",
                    method, excelData.length, fileName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (IOException e) {
            log.error("[{}] ❌ Error generating Excel report: {}", method, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(("Error generating report: " + e.getMessage()).getBytes());
        } catch (Exception e) {
            log.error("[{}] ❌ Unexpected error: {}", method, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(("Unexpected error: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Single Pay Run Excel Report (for frontend to download specific month's pay
     * run)
     */
    @GetMapping("/{payrunId}/report/excel")
    public ResponseEntity<byte[]> downloadSinglePayRunReport(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String payrunId) {

        String method = "downloadSinglePayRunReport";
        log.info("[{}] 📥 Request received for single pay run Excel | orgId={} | payrunId={}",
                method, organizationId, payrunId);

        try {
            // You'll need to implement this method in ExcelReportService
            // For now, let's use the existing dummy test endpoint pattern
            byte[] excelData = excelReportService.generateSinglePayRunReport(organizationId, payrunId);

            String fileName = "PayRun_" + payrunId + "_"
                    + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

            log.info("[{}] ✅ Single pay run Excel generated successfully | size={} bytes",
                    method, excelData.length);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelData);

        } catch (Exception e) {
            log.error("[{}] ❌ Error generating single pay run Excel: {}", method, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(("Error: " + e.getMessage()).getBytes());
        }
    }
}