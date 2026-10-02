package com.infinevo.payroll.portal;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Placeholder controller for the employee payslips endpoint (W-25, spec section 4; decision 2).
 *
 * <p>Mounted at {@code /api/v1/me/payslips}, guarded by {@code @RequiresModule(PlatformModule.PAYROLL)}
 * and {@code @RequiresAction("payroll.payslip.read_own")}. Replaced in place by {@code W-36}.
 */
@RestController
@RequestMapping("/api/v1/me/payslips")
@RequiresModule(PlatformModule.PAYROLL)
public class MyPayslipsPlaceholderController {

    @GetMapping
    @RequiresAction("payroll.payslip.read_own")
    public ResponseEntity<Map<String, Object>> getMyPayslips() {
        return ResponseEntity.ok(Map.of(
                "status", "placeholder",
                "message", "Payslips not built yet"));
    }
}
