package com.infinevo.hrms.portal;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placeholder controller for the employee timesheet endpoint (W-25, spec section 4; decision 2).
 *
 * <p>Mounted at {@code /api/v1/me/timesheet}, guarded by {@code @RequiresModule(PlatformModule.HRMS)}
 * and {@code @RequiresAction("hrms.timesheet.read_own")}. Replaced in place by HRMS timesheet ticket.
 */
@RestController
@RequestMapping("/api/v1/me/timesheet")
@RequiresModule(PlatformModule.HRMS)
public class MyTimesheetPlaceholderController {

    @GetMapping
    @RequiresAction("hrms.timesheet.read_own")
    public ResponseEntity<Map<String, Object>> getMyTimesheet() {
        return ResponseEntity.ok(Map.of(
                "status", "placeholder",
                "message", "Timesheet not built yet"));
    }
}
