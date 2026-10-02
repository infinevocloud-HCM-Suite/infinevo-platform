package com.infinevo.payroll.form16;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing Form 16 Part B annual tax statement endpoints (W-36.4).
 */
@RestController
public class Form16Controller {

    private static final Logger log = LoggerFactory.getLogger(Form16Controller.class);

    private final Form16Service form16Service;

    public Form16Controller(Form16Service form16Service) {
        this.form16Service = Objects.requireNonNull(form16Service, "form16Service must not be null");
    }

    /**
     * Officer endpoint to read an employee's Form 16 statement for a financial year.
     */
    @GetMapping("/api/v1/payroll/employees/{employeeId}/form16/{fy}")
    @PreAuthorize("hasAuthority('payroll.statutory_report.read')")
    public Form16Response get(@PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        log.info("Generating Form 16 statement for employee {} and financial year {}", employeeId, financialYear);

        Form16Statement statement = form16Service.render(employeeId, financialYear);

        if (log.isDebugEnabled()
                && statement.employee() != null
                && statement.employee().pan() != null) {
            String masked = maskPan(statement.employee().pan());
            log.debug("Rendered Form 16 for employee {} (PAN: {})", employeeId, masked);
        }

        return Form16Response.ok("Form 16 statement retrieved successfully", statement);
    }

    /**
     * Employee self-service endpoint to read their own Form 16 statement for a financial year.
     * Never logs employee PAN.
     */
    @GetMapping("/api/v1/me/form16/{fy}")
    @PreAuthorize("hasAuthority('payroll.payslip.read_own')")
    public Form16Response getOwn(@PathVariable("fy") String financialYear) {
        log.info("Generating self-service Form 16 statement for financial year {}", financialYear);

        Form16Statement statement = form16Service.renderOwn(financialYear);

        return Form16Response.ok("Form 16 statement retrieved successfully", statement);
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return "**********";
        }
        return pan.substring(0, 5) + "****" + pan.substring(9);
    }
}
