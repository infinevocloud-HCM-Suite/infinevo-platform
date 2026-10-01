package com.infinevo.core.employee;

import com.infinevo.shared.authz.RequiresAction;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller serving the authenticated caller's employee profile (W-25, spec section 4).
 *
 * <p>Mounted at {@code /api/v1/me/employee} and gated by {@code core.employee.read_own}.
 */
@RestController
@RequestMapping("/api/v1/me/employee")
public class MyEmployeeController {

    private final EmployeeService employeeService;

    public MyEmployeeController(EmployeeService employeeService) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping
    @RequiresAction("core.employee.read_own")
    public ResponseEntity<EmployeeResponse> getMyEmployee() {
        return employeeService
                .currentEmployee()
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new EmployeeService.NotFoundException("No employee profile linked to current user"));
    }
}
