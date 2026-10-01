package com.infinevo.payroll.payslip;

import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Implementation of {@link PayslipLinkService} (W-36.2 §4).
 * Signs and verifies public payslip links using HMAC-SHA256 with constant-time comparison.
 * Nothing in this class logs a token or a signature.
 */
@Service
public class PayslipLinkServiceImpl implements PayslipLinkService {

    private static final Logger log = LoggerFactory.getLogger(PayslipLinkServiceImpl.class);

    static final int MIN_SECRET_LENGTH = 24;
    static final Duration MAX_TTL = Duration.ofDays(7);
    static final String DEFAULT_BASE_URL = "/public/payslips";

    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "infinevo:payslip-link:v1";
    private static final String SEPARATOR = ".";

    private final EmployeePayRunRepository employeePayRuns;
    private final SecretKeySpec key;
    private final String baseUrl;
    private final Clock clock;

    @Autowired
    public PayslipLinkServiceImpl(
            EmployeePayRunRepository employeePayRuns,
            @Value("${document.link.secret:}") String secret,
            @Value("${payslip.link.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this(employeePayRuns, secret, baseUrl, Clock.systemUTC());
    }

    PayslipLinkServiceImpl(EmployeePayRunRepository employeePayRuns, String secret, String baseUrl, Clock clock) {
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
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
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public SignedLink signedLink(UUID employeePayrunId, Duration ttl) {
        Objects.requireNonNull(employeePayrunId, "employeePayrunId must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("A payslip link needs a positive lifetime");
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException(
                    "A payslip link lives at most " + MAX_TTL.toDays() + " days; " + ttl + " was asked for");
        }
        UUID tenantId = TenantContext.require();
        employeePayRuns
                .findByIdAndTenantId(employeePayrunId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Employee payrun not found: " + employeePayrunId));

        Instant expiresAt = clock.instant().plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        long expires = expiresAt.getEpochSecond();
        String encodedSignature =
                Base64.getUrlEncoder().withoutPadding().encodeToString(sign(tenantId, employeePayrunId, expires));
        String linkToken = tenantId + SEPARATOR + employeePayrunId + SEPARATOR + expires + SEPARATOR + encodedSignature;
        log.info(
                "Issued a link to employee payrun {} in tenant {}, expiring {}", employeePayrunId, tenantId, expiresAt);
        return new SignedLink(baseUrl + "?t=" + linkToken, expiresAt);
    }

    @Override
    public Optional<PayslipClaims> verify(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        String[] parts = tokenValue.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }

        UUID tenantId;
        UUID employeePayrunId;
        long expires;
        byte[] presented;
        try {
            tenantId = UUID.fromString(parts[0]);
            employeePayrunId = UUID.fromString(parts[1]);
            expires = Long.parseLong(parts[2]);
            presented = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (!tenantId.toString().equals(parts[0])
                || !employeePayrunId.toString().equals(parts[1])
                || !Long.toString(expires).equals(parts[2])) {
            return Optional.empty();
        }

        if (!MessageDigest.isEqual(sign(tenantId, employeePayrunId, expires), presented)) {
            return Optional.empty();
        }
        Instant expiresAt = Instant.ofEpochSecond(expires);
        if (!clock.instant().isBefore(expiresAt)) {
            return Optional.empty();
        }
        return Optional.of(new PayslipClaims(tenantId, employeePayrunId, expiresAt));
    }

    private byte[] sign(UUID tenantId, UUID employeePayrunId, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            mac.update(DOMAIN.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) ':');
            mac.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(employeePayrunId.toString().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(Long.toString(expires).getBytes(StandardCharsets.UTF_8));
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }
}
