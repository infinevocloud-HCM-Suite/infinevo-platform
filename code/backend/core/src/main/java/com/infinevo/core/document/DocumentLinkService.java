package com.infinevo.core.document;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Signed, expiring links to stored documents (W-21, spec section 4) — the only way to obtain a URL to
 * a file ({@code 12-core-contracts.md:107}).
 *
 * <p>Two lifetimes, both settled by the spec: {@link #INTERACTIVE_TTL} for a download from a screen,
 * and up to {@link #MAX_TTL} for a link placed in an email ({@code W-23.2}'s scheduled report,
 * {@code W-36}'s payslip). Anything longer is refused.
 *
 * <p>The link names the tenant, the document and the expiry, and all three are inside the signature:
 * a link cannot be re-pointed at another document, moved to another tenant, or extended.
 */
public interface DocumentLinkService {

    /** Fifteen minutes — an interactive download (spec section 13, decision 1). */
    Duration INTERACTIVE_TTL = Duration.ofMinutes(15);

    /** Seven days — the longest any link lives, for emailed links (contracts section 6, decision 8). */
    Duration MAX_TTL = Duration.ofDays(7);

    /** A link for an interactive download, valid for {@link #INTERACTIVE_TTL}. */
    SignedLink signedLink(UUID documentId);

    /**
     * A link valid for {@code ttl}, for a live document in the bound tenant.
     *
     * @throws IllegalArgumentException when {@code ttl} is not positive or exceeds {@link #MAX_TTL}
     * @throws DocumentService.NotFoundException when the document is not live in the bound tenant
     */
    SignedLink signedLink(UUID documentId, Duration ttl);

    /**
     * The claims of a token this service issued, if its signature holds and it has not expired.
     *
     * <p>Empty for every other token — malformed, tampered, signed with another secret, or expired —
     * and deliberately without saying which, so the answer teaches a caller nothing. Verification does
     * not bind a tenant or read the database; whoever serves the download does both, from the claims.
     */
    Optional<LinkClaims> verify(String token);

    /**
     * A link and when it stops working.
     *
     * <p>{@link #toString()} withholds the URL. The URL carries the signature, and a signature in a
     * log is incident 3 again ({@code legacy/Payroll-Bend-SBoot/.../PublicPayslipController.java:43-44});
     * a record's generated {@code toString} would print it the first time anyone logged a response.
     */
    record SignedLink(String url, Instant expiresAt) {
        @Override
        public String toString() {
            return "SignedLink[url=<withheld>, expiresAt=" + expiresAt + "]";
        }
    }

    /** What a verified token says. Carries no secret. */
    record LinkClaims(UUID tenantId, UUID documentId, Instant expiresAt) {}
}
