package com.itsdev.payroll.controller.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.FBPDTO;
import com.itsdev.payroll.service.claimsanddeclarations.FBPService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fbp")
public class FBPController {

    private final FBPService fbpService;

    public FBPController(FBPService fbpService) {
        this.fbpService = fbpService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getFBP(@RequestHeader("organizationId") String organizationId) {
        FBPDTO fbp = fbpService.getFBP(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "FBP fetched successfully");
        response.put("data", fbp);

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateFBP(
            @RequestHeader("organizationId") String organizationId,
            @RequestBody FBPDTO dto) {

        FBPDTO updated = fbpService.updateFBP(organizationId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "FBP updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }
}
