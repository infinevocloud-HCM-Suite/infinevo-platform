package com.infinevo.payroll.payslip;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous endpoint for viewing a payslip by signed link (W-36.2 §4).
 * Serves {@link PublicEndpoints#PAYSLIP_OPEN} without bearer authentication.
 * Every refusal returns 404. Nothing in this class logs a token or a signature.
 */
@RestController
public class PayslipOpenController {

    private static final Logger log = LoggerFactory.getLogger(PayslipOpenController.class);

    static final String NOT_A_LINK = "This link is not valid or has expired";

    private final PayslipLinkService linkService;
    private final PayslipService payslipService;

    public PayslipOpenController(PayslipLinkService linkService, PayslipService payslipService) {
        this.linkService = Objects.requireNonNull(linkService, "linkService must not be null");
        this.payslipService = Objects.requireNonNull(payslipService, "payslipService must not be null");
    }

    @GetMapping(PublicEndpoints.PAYSLIP_OPEN)
    public ResponseEntity<?> open(@RequestParam(value = "t", required = false) String tokenValue) {
        Optional<PayslipLinkService.PayslipClaims> claims = linkService.verify(tokenValue);
        if (claims.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, NOT_A_LINK);
        }

        UUID tenantId = claims.get().tenantId();
        UUID employeePayrunId = claims.get().employeePayrunId();
        TenantContext.set(tenantId);
        try {
            PayslipResponse payslip = payslipService.render(
                    tenantId, employeePayrunId, claims.get().expiresAt());
            log.info("Served payslip {} in tenant {} by signed link", employeePayrunId, tenantId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .cacheControl(CacheControl.noStore())
                    .header("Referrer-Policy", "no-referrer")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(PayslipApiResponse.ok("Payslip retrieved successfully", payslip));
        } catch (PayslipNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, NOT_A_LINK);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, NOT_A_LINK);
        } finally {
            TenantContext.clear();
        }
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(
                        code,
                        message,
                        traceId != null ? traceId : UUID.randomUUID().toString()));
    }
}
