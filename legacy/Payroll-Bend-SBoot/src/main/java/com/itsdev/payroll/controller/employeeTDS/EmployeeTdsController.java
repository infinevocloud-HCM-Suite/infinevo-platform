package com.itsdev.payroll.controller.employeeTDS;

import com.itsdev.payroll.service.employeeTDS.TdsSalaryRevisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/employee-tds")
public class EmployeeTdsController {

    private static final Logger log =
            LoggerFactory.getLogger(EmployeeTdsController.class);

    private final TdsSalaryRevisionService tdsSalaryRevisionService;

    public EmployeeTdsController(
            TdsSalaryRevisionService tdsSalaryRevisionService
    ) {
        this.tdsSalaryRevisionService = tdsSalaryRevisionService;
    }

    /**
     * Trigger TDS recalculation after salary revision.
     *
     * This API is intentionally explicit.
     * It is meant to be called AFTER salary revision is saved.
     */
    @PostMapping("/salary-revision")
    public ResponseEntity<Map<String, Object>> recalculateTdsAfterSalaryRevision(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody Map<String, Object> request
    ) {

        final String method = "recalculateTdsAfterSalaryRevision";

        String employeeId = (String) request.get("employeeId");
        Integer fiscalYear = (Integer) request.get("fiscalYear");

        log.info(
                "[{}] ▶ Request received | orgId={} empId={} fy={}",
                method, organizationId, employeeId, fiscalYear
        );

        if (employeeId == null || fiscalYear == null) {
            throw new IllegalArgumentException(
                    "employeeId and fiscalYear are required"
            );
        }

        // 🔥 CORE LOGIC
        tdsSalaryRevisionService.handleSalaryRevisionTds(
                organizationId,
                employeeId,
                fiscalYear
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "TDS recalculated successfully after salary revision");

        log.info(
                "[{}] ✅ TDS recalculation completed | empId={} fy={}",
                method, employeeId, fiscalYear
        );

        return ResponseEntity.ok(response);
    }
}
