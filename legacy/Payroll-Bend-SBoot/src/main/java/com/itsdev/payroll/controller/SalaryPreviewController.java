package com.itsdev.payroll.controller;

import com.itsdev.payroll.dto.employee.preview.EmployeePreviewDTO;
import com.itsdev.payroll.service.SalaryPreviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employees/salary/preview")
public class SalaryPreviewController {

    private final SalaryPreviewService previewService;

    public SalaryPreviewController(SalaryPreviewService previewService) {
        this.previewService = previewService;
    }

    // ---------------- GET SALARY PREVIEW ----------------
    @GetMapping("/{employeeId}")
    public ResponseEntity<Map<String, Object>> getEmployeePreview(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String employeeId) {

        EmployeePreviewDTO dto = previewService.getEmployeePreview(organizationId, employeeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0); // ✅ matches your earlier payload structure
        response.put("message", "success");
        response.put("employee", dto);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
