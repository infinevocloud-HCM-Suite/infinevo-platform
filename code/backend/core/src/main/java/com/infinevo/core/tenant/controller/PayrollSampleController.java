package com.infinevo.core.tenant.controller;

import com.infinevo.shared.entitlement.ModuleCode;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll")
@RequiresModule(ModuleCode.PAYROLL)
public class PayrollSampleController {

    @GetMapping("/sample")
    public ResponseEntity<Map<String, String>> getPayrollSample() {
        return ResponseEntity.ok(Map.of("module", "PAYROLL", "status", "Access Granted"));
    }
}
