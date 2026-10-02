package com.infinevo.payroll.payslip;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs and verifies public payslip links (W-36.2 §4).
 *
 * <p><strong>Token shape:</strong> {@code {tenantId}.{employeePayrunId}.{expiresEpochSecond}.{signature}},
 * every part URL-safe. The signed message is the first three parts behind the fixed domain prefix
 * {@code infinevo:payslip-link:v1}.
 */
public interface PayslipLinkService {

    record SignedLink(String url, Instant expiresAt) {
        @Override
        public String toString() {
            return "SignedLink[url=<withheld>, expiresAt=" + expiresAt + "]";
        }
    }

    record PayslipClaims(UUID tenantId, UUID employeePayrunId, Instant expiresAt) {}

    SignedLink signedLink(UUID employeePayrunId, Duration ttl);

    Optional<PayslipClaims> verify(String token);
}
