package com.infinevo.shared.entitlement;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller carrying {@code @RequiresModule(HRMS)} for entitlement verification (W-12.2, spec section 7).
 *
 * <p>Lives on the test classpath only.
 */
@RestController
@RequestMapping("/api/v1/test/hrms-guard")
@RequiresModule(PlatformModule.HRMS)
public class ModuleGuardTestController {

    @GetMapping
    public String get() {
        return "ok";
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String post() {
        return "created";
    }
}
