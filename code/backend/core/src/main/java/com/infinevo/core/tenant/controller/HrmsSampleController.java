package com.infinevo.core.tenant.controller;

import com.infinevo.shared.entitlement.ModuleCode;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hrms")
@RequiresModule(ModuleCode.HRMS)
public class HrmsSampleController {

    @GetMapping("/sample")
    public ResponseEntity<Map<String, String>> getHrmsSample() {
        return ResponseEntity.ok(Map.of("module", "HRMS", "status", "Access Granted"));
    }
}
