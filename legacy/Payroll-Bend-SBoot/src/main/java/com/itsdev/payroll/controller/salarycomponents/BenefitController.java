package com.itsdev.payroll.controller.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.BenefitDTO;
import com.itsdev.payroll.service.salarycomponents.BenefitService;
import com.itsdev.payroll.util.BenefitUtility;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/benefits")
public class BenefitController {

    private final BenefitService benefitService;

    public BenefitController(BenefitService benefitService) {
        this.benefitService = benefitService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBenefit(@RequestHeader("organizationId") String orgId,
    		@Valid @RequestBody BenefitDTO dto) {
        BenefitDTO created = benefitService.createBenefit(orgId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.CREATED.value());
        response.put("message", "Benefit created successfully");
        response.put("data", created);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{benefitId}")
    public ResponseEntity<Map<String, Object>> updateBenefit(@RequestHeader("organizationId") String orgId,
                                                             @PathVariable String benefitId,
                                                             @Valid @RequestBody BenefitDTO dto) {
        BenefitDTO updated = benefitService.updateBenefit(orgId, benefitId, dto);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Benefit updated successfully");
        response.put("data", updated);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{benefitId}")
    public ResponseEntity<Map<String, Object>> deleteBenefit(@RequestHeader("organizationId") String orgId,
                                                             @PathVariable String benefitId) {
        benefitService.deleteBenefit(orgId, benefitId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.NO_CONTENT.value());
        response.put("message", "Benefit deleted successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{benefitId}")
    public ResponseEntity<Map<String, Object>> getBenefit(@RequestHeader("organizationId") String orgId,
                                                          @PathVariable String benefitId) {
        BenefitDTO benefit = benefitService.getBenefit(orgId, benefitId);

        Map<String, Object> data = new HashMap<>();
        data.put("benefit", benefit);
        data.put("benefitPlans",BenefitUtility.getBenefitPlans());
        data.put("section6aDetails", BenefitUtility.getSection6aDetails());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Benefit fetched successfully");
        response.put("data", data);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllBenefits(@RequestHeader("organizationId") String orgId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Benefits fetched successfully");
        response.put("data", benefitService.getAllBenefits(orgId));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/utility")
    public ResponseEntity<Map<String, Object>> getUtility() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Utility data fetched successfully");
        response.put("data", BenefitUtility.getUtilityData());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/inactive/{benefitId}")
    public ResponseEntity<Map<String, Object>> inactivateBenefit(@RequestHeader("organizationId") String orgId,
                                                                 @PathVariable String benefitId) {
        benefitService.inactivateBenefit(orgId, benefitId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Benefit inactivated successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/active/{benefitId}")
    public ResponseEntity<Map<String, Object>> reactivateBenefit(@RequestHeader("organizationId") String orgId,
                                                                 @PathVariable String benefitId) {
        benefitService.reactivateBenefit(orgId, benefitId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HttpStatus.OK.value());
        response.put("message", "Benefit reactivated successfully");

        return ResponseEntity.ok(response);
    }

}
