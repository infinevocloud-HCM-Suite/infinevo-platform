package com.itsdev.payroll.controller.dashboard;

import com.itsdev.payroll.dto.dashboard.DashboardResponse;
import com.itsdev.payroll.dto.dashboard.StatutorySummaryDTO;
import com.itsdev.payroll.dto.dashboard.TdsSummaryDTO;
import com.itsdev.payroll.service.dashboard.DashboardService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DashboardController
 * --------------------
 * Handles all API endpoints related to payroll dashboard summary and analytics.
 * Accepts `organizationId` in request header for multi-org context.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // ==========================================================
    // 1️⃣  Dashboard Summary Endpoint
    // ==========================================================
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary(
            @RequestHeader("organizationId") String organizationId) {

        String method = "getDashboardSummary";
        log.info("[{}] 📥 Request received | Fetching dashboard summary | orgId={}", method, organizationId);

        DashboardResponse dashboard = dashboardService.getDashboardSummary(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Dashboard summary retrieved successfully");
        response.put("data", dashboard);

        log.info("[{}] ✅ Dashboard summary fetched successfully | orgId={}", method, organizationId);

        return ResponseEntity.ok(response);
    }

    // ==========================================================
    // 2️⃣  Statutory Summary Endpoint (EPF / ESI)
    // ==========================================================

    @GetMapping("/statutory-summary")
    public ResponseEntity<Map<String, Object>> getStatutorySummary(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(value = "fromDate", required = false) String fromDateStr,
            @RequestParam(value = "toDate", required = false) String toDateStr) {

        String method = "getStatutorySummary";
        log.info("[{}] 📥 Request received | Fetching statutory summaries | orgId={}", method, organizationId);

        // ✅ Parse optional date params
        LocalDate fromDate = (fromDateStr != null && !fromDateStr.isEmpty()) ? LocalDate.parse(fromDateStr) : null;
        LocalDate toDate = (toDateStr != null && !toDateStr.isEmpty()) ? LocalDate.parse(toDateStr) : null;

        // ✅ Fetch both EPF and ESI summaries together
        Map<String, StatutorySummaryDTO> summaryMap = new LinkedHashMap<>();
        summaryMap.put("epf", dashboardService.getStatutorySummary(organizationId, "epf", fromDate, toDate));
        summaryMap.put("esi", dashboardService.getStatutorySummary(organizationId, "esi", fromDate, toDate));

        // ✅ Build standard API response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Statutory summaries retrieved successfully");
        response.put("data", summaryMap);

        log.info("[{}] ✅ Statutory summaries fetched successfully | orgId={}", method, organizationId);

        return ResponseEntity.ok(response);
    }


    // ==========================================================
    // 3️⃣  TDS Summary Endpoint
    // ==========================================================
    @GetMapping("/tds-summary")
    public ResponseEntity<Map<String, Object>> getTdsSummary(
            @RequestHeader("organizationId") String organizationId,
            @RequestParam(value = "fromDate", required = false) String fromDateStr,
            @RequestParam(value = "toDate", required = false) String toDateStr) {

        String method = "getTdsSummary";
        log.info("[{}] 📥 Request received | Fetching TDS summary | orgId={}", method, organizationId);

        LocalDate fromDate = (fromDateStr != null && !fromDateStr.isEmpty()) ? LocalDate.parse(fromDateStr) : null;
        LocalDate toDate = (toDateStr != null && !toDateStr.isEmpty()) ? LocalDate.parse(toDateStr) : null;

        TdsSummaryDTO tdsSummary = dashboardService.getTdsSummary(organizationId, fromDate, toDate);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "TDS summary retrieved successfully");
        response.put("data", tdsSummary);

        log.info("[{}] ✅ TDS summary fetched successfully | orgId={}", method, organizationId);

        return ResponseEntity.ok(response);
    }
}

