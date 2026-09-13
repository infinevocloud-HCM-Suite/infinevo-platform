package com.itsdev.payroll.controller.payruns;

import com.itsdev.payroll.dto.payruns.PayslipResponseDTO;
import com.itsdev.payroll.entity.payruns.EmployeePayRun;
import com.itsdev.payroll.repository.payruns.EmployeePayRunRepository;
import com.itsdev.payroll.service.payruns.EmployeePayRunService;
import com.itsdev.payroll.service.payruns.PayslipTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/payslips")
public class PublicPayslipController {

    private final EmployeePayRunService employeeService;
    private final PayslipTokenService tokenService;
    private final EmployeePayRunRepository employeePayRunRepository;
    private static final Logger log = LoggerFactory.getLogger(PublicPayslipController.class);

    public PublicPayslipController(EmployeePayRunService employeeService, 
                                   PayslipTokenService tokenService,
                                   EmployeePayRunRepository employeePayRunRepository) {
        this.employeeService = employeeService;
        this.tokenService = tokenService;
        this.employeePayRunRepository = employeePayRunRepository;
    }

    @GetMapping("/{payrunId}/{employeeId}")
    public ResponseEntity<Map<String, Object>> getPublicPayslip(
            @PathVariable String payrunId,
            @PathVariable String employeeId,
            @RequestParam String orgId,
            @RequestParam String token) {

        String method = "getPublicPayslip";
        log.info("[{}] 📥 Public request to fetch payslip | payrunId={} | employeeId={} | orgId={} | token={}",
                method, payrunId, employeeId, orgId, token);

        // 1. Verify the secure HMAC-SHA256 token
        boolean isValid = tokenService.verifyToken(payrunId, employeeId, orgId, token);
        if (!isValid) {
            log.warn("[{}] ❌ Invalid or expired token for payrunId={} employeeId={}", method, payrunId, employeeId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired download link");
        }

        log.info("[{}] 🔑 Token verified successfully, loading payslip details", method);

        // 2. Fetch the payslip data
        PayslipResponseDTO payslipResponse = employeeService.getEmployeePayslip(orgId, employeeId, payrunId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 200);
        response.put("message", "Payslip retrieved successfully");
        response.put("data", payslipResponse);

        log.info("[{}] ✅ Public payslip fetched successfully for employeeId={} | payrunId={}",
                method, employeeId, payrunId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/generate-test-link")
    public ResponseEntity<Map<String, String>> generateTestLink(
            @RequestParam String payrunId,
            @RequestParam String employeeId,
            @RequestParam String orgId) {
        String token = tokenService.generateToken(payrunId, employeeId, orgId);
        String link = "http://localhost:3000/public/payslips/" + payrunId + "/" + employeeId + "?orgId=" + orgId + "&token=" + token;
        return ResponseEntity.ok(Map.of("link", link));
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestPayslipInfo() {
        return employeePayRunRepository.findAll().stream()
                .filter(epr -> epr.getEmployee() != null && epr.getEmployee().getOrganization() != null)
                .reduce((first, second) -> second) // Get the last element (latest created)
                .map(epr -> {
                    String orgId = epr.getEmployee().getOrganization().getOrganizationId();
                    String token = tokenService.generateToken(epr.getPayrunId(), epr.getEmployeeId(), orgId);
                    String link = "http://localhost:3000/public/payslips/" + epr.getPayrunId() + "/" + epr.getEmployeeId() + "?orgId=" + orgId + "&token=" + token;

                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("employeeName", epr.getFullName());
                    map.put("employeeNumber", epr.getEmployeeNumber());
                    map.put("payrunId", epr.getPayrunId());
                    map.put("employeeId", epr.getEmployeeId());
                    map.put("orgId", orgId);
                    map.put("testDownloadLink", link);
                    return ResponseEntity.ok(map);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No payrun records found in the database")));
    }
}
