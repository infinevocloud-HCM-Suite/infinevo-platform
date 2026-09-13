package com.itsdev.payroll.controller;

import com.itsdev.payroll.dto.OrgSetupStepsDTO;
import com.itsdev.payroll.service.OrgSetupStepsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/org-setup-steps")
public class OrgSetupStepsController {

    private final OrgSetupStepsService service;

    public OrgSetupStepsController(OrgSetupStepsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrgSetupSteps(
            @RequestHeader("organizationId") String organizationId) {

        OrgSetupStepsDTO steps = service.getOrgSetupSteps(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Organization setup steps fetched successfully");
        response.put("data", steps);

        return ResponseEntity.ok(response);
    }
}
