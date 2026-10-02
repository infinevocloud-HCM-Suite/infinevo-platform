package com.infinevo.payroll.payslip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PayslipLinkServiceImpl} (W-36.2 §7).
 * Covers token creation, verification, tampering, TTL bounds, domain isolation, and secret requirements.
 */
class PayslipLinkServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SECRET = "unit-test-document-link-secret-0123456789";
    private static final String BASE_URL = "https://app.example.test/public/payslips";
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private EmployeePayRunRepository employeePayRuns;
    private UUID employeePayrunId;
    private PayslipLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        employeePayRuns = mock(EmployeePayRunRepository.class);
        employeePayrunId = UUID.randomUUID();
        when(employeePayRuns.findByIdAndTenantId(employeePayrunId, TENANT))
                .thenReturn(Optional.of(mock(EmployeePayRun.class)));
        service = at(NOW);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The link points at the base URL and carries the token as ?t=")
    void linkShape() {
        PayslipLinkService.SignedLink link = service.signedLink(employeePayrunId, Duration.ofDays(7));

        assertThat(link.url()).startsWith(BASE_URL + "?t=");
        assertThat(token(link)).startsWith(TENANT + "." + employeePayrunId + ".");
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("Seven days is accepted; a second more is refused")
    void sevenDaysIsTheCeiling() {
        assertThat(service.signedLink(employeePayrunId, Duration.ofDays(7)).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));

        assertThatThrownBy(() ->
                        service.signedLink(employeePayrunId, Duration.ofDays(7).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 days");
    }

    @Test
    @DisplayName("A zero, negative or null lifetime is refused")
    void lifetimeMustBePositive() {
        assertThatThrownBy(() -> service.signedLink(employeePayrunId, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.signedLink(employeePayrunId, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.signedLink(employeePayrunId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("No link for an unknown employee payrun row")
    void noLinkForUnknownRow() {
        assertThatThrownBy(() -> service.signedLink(UUID.randomUUID(), Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("No link with no tenant bound")
    void noLinkWithoutTenant() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.signedLink(employeePayrunId, Duration.ofDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A valid link verifies and extracts tenant, employeePayrunId and expiresAt")
    void validLinkVerifies() {
        PayslipLinkService.SignedLink link = service.signedLink(employeePayrunId, Duration.ofDays(7));

        Optional<PayslipLinkService.PayslipClaims> claims = service.verify(token(link));

        assertThat(claims).isPresent();
        assertThat(claims.get().tenantId()).isEqualTo(TENANT);
        assertThat(claims.get().employeePayrunId()).isEqualTo(employeePayrunId);
        assertThat(claims.get().expiresAt()).isEqualTo(link.expiresAt());
    }

    @Test
    @DisplayName("Tampered links do not verify - signature, row id, tenant or expiry changed")
    void tamperedLinksDoNotVerify() {
        String[] parts =
                token(service.signedLink(employeePayrunId, Duration.ofDays(7))).split("\\.");

        char first = parts[3].charAt(0);
        String badSignature = (first == 'A' ? 'B' : 'A') + parts[3].substring(1);
        assertThat(service.verify(join(parts[0], parts[1], parts[2], badSignature)))
                .isEmpty();

        assertThat(service.verify(join(parts[0], UUID.randomUUID().toString(), parts[2], parts[3])))
                .isEmpty();
        assertThat(service.verify(join(OTHER_TENANT.toString(), parts[1], parts[2], parts[3])))
                .isEmpty();
        assertThat(service.verify(join(parts[0], parts[1], Long.toString(Long.parseLong(parts[2]) + 3600), parts[3])))
                .isEmpty();
    }

    @Test
    @DisplayName("A signature cut short does not verify")
    void truncatedSignatureDoesNotVerify() {
        String[] parts =
                token(service.signedLink(employeePayrunId, Duration.ofDays(7))).split("\\.");

        assertThat(service.verify(join(parts[0], parts[1], parts[2], parts[3].substring(0, 10))))
                .isEmpty();
    }

    @Test
    @DisplayName("A link signed with another secret does not verify")
    void anotherSecretDoesNotVerify() {
        PayslipLinkServiceImpl other = new PayslipLinkServiceImpl(
                employeePayRuns, "some-other-secret-entirely-0123456789", BASE_URL, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.verify(token(other.signedLink(employeePayrunId, Duration.ofDays(7)))))
                .isEmpty();
    }

    @Test
    @DisplayName("A W-21 document token signed with document domain tag does not verify as a payslip link")
    void documentTokenDoesNotVerify() throws Exception {
        long expires = NOW.plus(Duration.ofDays(7)).getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update("infinevo:document-link:v1".getBytes(StandardCharsets.UTF_8));
        mac.update((byte) ':');
        mac.update(TENANT.toString().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(employeePayrunId.toString().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(Long.toString(expires).getBytes(StandardCharsets.UTF_8));
        String docSig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());

        String docToken = TENANT + "." + employeePayrunId + "." + expires + "." + docSig;
        assertThat(service.verify(docToken)).isEmpty();
    }

    @Test
    @DisplayName("An expired link does not verify; one second before expiry it does")
    void expiredLinkDoesNotVerify() {
        PayslipLinkService.SignedLink link = service.signedLink(employeePayrunId, Duration.ofDays(7));
        String token = token(link);

        assertThat(at(link.expiresAt().minusSeconds(1)).verify(token)).isPresent();
        assertThat(at(link.expiresAt()).verify(token)).isEmpty();
        assertThat(at(link.expiresAt().plus(Duration.ofDays(1))).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("Malformed tokens are refused, never throw")
    void malformedTokensAreRefused() {
        String[] parts =
                token(service.signedLink(employeePayrunId, Duration.ofDays(7))).split("\\.");

        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("a.b.c")).isEmpty();
        assertThat(service.verify("a.b.c.d.e")).isEmpty();
        assertThat(service.verify(join("not-a-uuid", parts[1], parts[2], parts[3])))
                .isEmpty();
        assertThat(service.verify(join(parts[0], parts[1], "soon", parts[3]))).isEmpty();
        assertThat(service.verify(join(parts[0], parts[1], parts[2], "***not base64***")))
                .isEmpty();
    }

    @Test
    @DisplayName("Non-canonical spelling of UUID or numbers is refused")
    void nonCanonicalSpellingRefused() {
        UUID lettered = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(employeePayRuns.findByIdAndTenantId(lettered, TENANT)).thenReturn(Optional.of(mock(EmployeePayRun.class)));
        String[] parts = token(service.signedLink(lettered, Duration.ofDays(7))).split("\\.");

        assertThat(service.verify(join(parts))).isPresent();
        assertThat(service.verify(join(parts[0], parts[1].toUpperCase(Locale.ROOT), parts[2], parts[3])))
                .isEmpty();
        assertThat(service.verify(join(parts[0], parts[1], "0" + parts[2], parts[3])))
                .isEmpty();
    }

    @Test
    @DisplayName("No secret, a blank one, or a short one (< 24 chars): refused on start")
    void secretRequirements() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        for (String notAbsolute : new String[] {null, "  ", "/public/payslips"}) {
            assertThatThrownBy(() -> new PayslipLinkServiceImpl(employeePayRuns, SECRET, notAbsolute, clock))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PAYSLIP_LINK_BASE_URL");
        }
        assertThatThrownBy(() -> new PayslipLinkServiceImpl(employeePayRuns, null, BASE_URL, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMENT_LINK_SECRET");
        assertThatThrownBy(() -> new PayslipLinkServiceImpl(employeePayRuns, "   ", BASE_URL, clock))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PayslipLinkServiceImpl(employeePayRuns, "short-secret", BASE_URL, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(PayslipLinkServiceImpl.MIN_SECRET_LENGTH));
    }

    @Test
    @DisplayName("SignedLink toString withholds URL containing the token")
    void toStringWithholdsUrl() {
        PayslipLinkService.SignedLink link = service.signedLink(employeePayrunId, Duration.ofDays(7));

        assertThat(link.toString()).doesNotContain(token(link)).contains("<withheld>");
    }

    private PayslipLinkServiceImpl at(Instant instant) {
        return new PayslipLinkServiceImpl(employeePayRuns, SECRET, BASE_URL, Clock.fixed(instant, ZoneOffset.UTC));
    }

    static String token(PayslipLinkService.SignedLink link) {
        return link.url().substring(link.url().indexOf("?t=") + 3);
    }

    private static String join(String... parts) {
        return String.join(".", parts);
    }
}
