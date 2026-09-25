package com.infinevo.core.document;

import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and verifies document links (W-21) — ported from the payslip token, with the three things
 * that token got wrong put right.
 *
 * <p>The frozen mechanism is HMAC-SHA256 over the ids, Base64-URL encoded, verified by regeneration
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/PayslipTokenServiceImpl.java:19-39}).
 * That part is sound and is kept. What is not kept:
 *
 * <ul>
 *   <li><strong>It never expired.</strong> Here the expiry is part of the signed message, so it cannot
 *       be stripped or pushed back without breaking the signature (spec section 9).
 *   <li><strong>Its secret had a committed default</strong> —
 *       {@code ${app.payslip.download.secret-key:default-payslip-secret-key-2026-xyz}} ({@code :15}).
 *       Here there is none: no secret, or a short one, and the application does not start.
 *   <li><strong>It compared with {@code String.equals}</strong> ({@code :38}), which returns at the
 *       first differing character and so leaks, through timing, how much of a guess was right. Here
 *       the comparison is {@link MessageDigest#isEqual}, which takes the same time wherever the bytes
 *       differ.
 * </ul>
 *
 * <p>Nothing in this class logs a token, a signature or a URL — incident 3 was a log line at
 * {@code PublicPayslipController.java:43-44}, not a crypto flaw. {@code DocumentLoggingTest} holds
 * that.
 *
 * <p><strong>Token shape:</strong> {@code {tenantId}.{documentId}.{expiresEpochSecond}.{signature}},
 * every part URL-safe. The signed message is the first three parts behind a fixed domain prefix, so a
 * signature made by this key for any other purpose can never verify here.
 */
@Service
public class DocumentLinkServiceImpl implements DocumentLinkService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLinkServiceImpl.class);

    /**
     * The shortest secret accepted. {@code deploy.sh} generates 28 characters over a 70-character
     * alphabet — about 170 bits — for every platform secret; 24 leaves room for a hand-set local value
     * while refusing anything guessable.
     */
    static final int MIN_SECRET_LENGTH = 24;

    /**
     * Where a link points: {@link DocumentDownloadController}, on the {@code D-22} exception list
     * ({@code PublicEndpoints}). Relative, so it resolves against whatever host the client reached the
     * API on; {@code document.link.base-url} makes it absolute where an email needs one.
     */
    static final String DEFAULT_BASE_URL = PublicEndpoints.DOCUMENT_DOWNLOAD;

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "infinevo:document-link:v1";
    private static final String SEPARATOR = ".";

    private final DocumentRepository documents;
    private final SecretKeySpec key;
    private final String baseUrl;
    private final Clock clock;

    @Autowired
    public DocumentLinkServiceImpl(
            DocumentRepository documents,
            @Value("${document.link.secret:}") String secret,
            @Value("${document.link.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this(documents, secret, baseUrl, Clock.systemUTC());
    }

    DocumentLinkServiceImpl(DocumentRepository documents, String secret, String baseUrl, Clock clock) {
        this.documents = Objects.requireNonNull(documents, "documents must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("document.link.secret is not set. Set DOCUMENT_LINK_SECRET — in Azure it"
                    + " comes from Key Vault as document-link-secret. There is no default on purpose: the"
                    + " legacy payslip link shipped with one and every link it ever signed was forgeable.");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "document.link.secret must be at least " + MIN_SECRET_LENGTH + " characters long");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("document.link.base-url must not be blank");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.baseUrl = baseUrl.trim();
    }

    /**
     * Its own {@code @Transactional}: the call below is on {@code this}, so it never passes through
     * the proxy and the two-argument method's annotation does not apply to it.
     */
    @Override
    @Transactional(readOnly = true)
    public SignedLink signedLink(UUID documentId) {
        return signedLink(documentId, INTERACTIVE_TTL);
    }

    @Override
    @Transactional(readOnly = true)
    public SignedLink signedLink(UUID documentId, Duration ttl) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A document link needs a positive lifetime");
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException(
                    "A document link lives at most " + MAX_TTL.toDays() + " days; " + ttl + " was asked for");
        }
        UUID tenantId = TenantContext.require();
        documents
                .findByIdAndTenantIdAndDeletedFalse(documentId, tenantId)
                .orElseThrow(() -> new DocumentService.NotFoundException(documentId));

        Instant expiresAt = clock.instant().plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        long expires = expiresAt.getEpochSecond();
        String token = tenantId
                + SEPARATOR
                + documentId
                + SEPARATOR
                + expires
                + SEPARATOR
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(tenantId, documentId, expires));
        log.info("Issued a link to document {} in tenant {}, expiring {}", documentId, tenantId, expiresAt);
        return new SignedLink(baseUrl + "?t=" + token, expiresAt);
    }

    @Override
    public Optional<LinkClaims> verify(String token) {
        if (token == null || token.isBlank()) {
            return refused("empty");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4) {
            return refused("malformed");
        }

        UUID tenantId;
        UUID documentId;
        long expires;
        byte[] presented;
        try {
            tenantId = UUID.fromString(parts[0]);
            documentId = UUID.fromString(parts[1]);
            expires = Long.parseLong(parts[2]);
            presented = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return refused("malformed");
        }
        // Only the canonical spelling this service writes. UUID.fromString also accepts forms such as
        // "1-1-1-1-1", which would let one signed link be written several ways.
        if (!tenantId.toString().equals(parts[0])
                || !documentId.toString().equals(parts[1])
                || !Long.toString(expires).equals(parts[2])) {
            return refused("malformed");
        }

        if (!MessageDigest.isEqual(sign(tenantId, documentId, expires), presented)) {
            return refused("signature");
        }
        Instant expiresAt = Instant.ofEpochSecond(expires);
        if (!clock.instant().isBefore(expiresAt)) {
            return refused("expired");
        }
        return Optional.of(new LinkClaims(tenantId, documentId, expiresAt));
    }

    private byte[] sign(UUID tenantId, UUID documentId, long expires) {
        String message = DOMAIN + ":" + tenantId + ":" + documentId + ":" + expires;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by every Java runtime", e);
        }
    }

    /** Logs the reason and never the token. */
    private static Optional<LinkClaims> refused(String reason) {
        log.debug("Refused a document link: {}", reason);
        return Optional.empty();
    }
}
