package com.itsdev.payroll.controller.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.EarningDTO;
import com.itsdev.payroll.service.salarycomponents.EarningService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/earnings")
public class EarningController {

    private final EarningService earningService;

    public EarningController(EarningService earningService) {
        this.earningService = earningService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createEarning(
            @RequestHeader("organizationId") String organizationId,
            @Valid @RequestBody EarningDTO dto) {

        EarningDTO created = earningService.create(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Earning created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{earningId}")
    public ResponseEntity<Map<String, Object>> updateEarning(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String earningId,
            @Valid @RequestBody EarningDTO dto) {

        EarningDTO updated = earningService.update(organizationId, earningId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Earning updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{earningId}")
    public ResponseEntity<Map<String, Object>> getEarning(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String earningId) {

        EarningDTO dto = earningService.get(organizationId, earningId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Earning fetched successfully");
        response.put("data", dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllEarnings(
            @RequestHeader("organizationId") String organizationId) {

        List<EarningDTO> list = earningService.getAllEarnings(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Earnings fetched successfully");
        response.put("data", list);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{earningId}")
    public ResponseEntity<Map<String, Object>> deleteEarning(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String earningId) {

        earningService.delete(organizationId, earningId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.NO_CONTENT.value());
        response.put("message", "Earning deleted successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/inactive/{earningId}")
    public ResponseEntity<Map<String, Object>> inactivateEarning(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String earningId) {

        earningService.inactivateEarning(organizationId, earningId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Earning inactivated successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/active/{earningId}")
    public ResponseEntity<Map<String, Object>> reactivateEarning(
            @RequestHeader("organizationId") String organizationId,
            @PathVariable String earningId) {

        earningService.reactivateEarning(organizationId, earningId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Earning activated successfully");

        return ResponseEntity.ok(response);
    }

}
