package com.itsdev.payroll.controller.employee.preview;

import com.itsdev.payroll.dto.employee.preview.OrgStatutoryConfigDTO;
import com.itsdev.payroll.service.employee.preview.OrgStatutoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/employees/statutory")
public class OrgStatutoryController {

    private final OrgStatutoryService orgStatutoryService;

    public OrgStatutoryController(OrgStatutoryService orgStatutoryService) {
        this.orgStatutoryService = orgStatutoryService;
    }

    // ---------- GET ORGANIZATION STATUTORY CONFIG (EPF + ESI + Contributions + Flags) ----------
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getOrgStatutoryConfig(
            @RequestHeader("organizationId") String organizationId) {

        OrgStatutoryConfigDTO dto = orgStatutoryService.getOrgStatutoryConfig(organizationId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("statutoryConfig", dto);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
