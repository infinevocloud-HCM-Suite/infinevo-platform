package com.itsdev.payroll.controller.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.EsiDTO;
import com.itsdev.payroll.service.statutorycomponents.EsiService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/esi")
public class EsiController {

    private final EsiService esiService;

    public EsiController(EsiService esiService) {
        this.esiService = esiService;
    }


    @GetMapping
    public ResponseEntity<Map<String, Object>> getEsi(@RequestHeader("organizationId") String organizationId) {
        EsiDTO esiDto = esiService.getEsiByOrganizationId(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "ESI details fetched successfully");
        response.put("data", esiDto);

        return ResponseEntity.ok(response);
    }


    @PutMapping
    public ResponseEntity<Map<String, Object>> updateEsi(@RequestHeader("organizationId") String organizationId,
    		@Valid @RequestBody EsiDTO esiDto) {
        EsiDTO updated = esiService.saveOrUpdateEsi(organizationId, esiDto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "ESI details updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disableEsi(@RequestHeader("organizationId") String organizationId) {
        EsiDTO disabled = esiService.disableEsi(organizationId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "ESI has been disabled for this organization");
        response.put("data", disabled);

        return ResponseEntity.ok(response);
    }
}
