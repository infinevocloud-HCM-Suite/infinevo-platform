package com.itsdev.payroll.controller.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.EpfDTO;
import com.itsdev.payroll.service.statutorycomponents.EpfService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/epf")
public class EpfController {

    private final EpfService epfService;

    public EpfController(EpfService epfService) {
        this.epfService = epfService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getEpf(@RequestHeader("organizationId") String organizationId) {
        EpfDTO epfDto = epfService.getEpfByOrganizationId(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "EPF details fetched successfully");
        response.put("data", epfDto);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> createOrUpdateEpf(@RequestHeader("organizationId") String organizationId,
    		@Valid @RequestBody EpfDTO epfDto) {
        EpfDTO savedEpf = epfService.createOrUpdateEpf(organizationId, epfDto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "EPF details saved successfully");
        response.put("data", savedEpf);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disableEpf(@RequestHeader("organizationId") String organizationId) {
        EpfDTO disabled = epfService.disableEpf(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "EPF has been disabled for this organization");
        response.put("data", disabled);

        return ResponseEntity.ok(response);
    }
}
