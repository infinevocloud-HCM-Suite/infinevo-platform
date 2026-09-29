package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-21 spec section 7 — a valid link verifies; a tampered one does not; an expired one does not; a
 * lifetime over seven days is refused; the interactive default is fifteen minutes. Plus the three
 * things the legacy payslip token got wrong: no expiry, a default secret, and {@code String.equals}.
 *
 * <p>No Spring context — {@code docs/CONVENTIONS.md} section 3.
 */
class DocumentLinkServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SECRET = "unit-test-document-link-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private DocumentRepository documents;
    private UUID documentId;
    private DocumentLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentRepository.class);
        documentId = UUID.randomUUID();
        when(documents.findByIdAndTenantIdAndDeletedFalse(documentId, TENANT))
                .thenReturn(Optional.of(mock(Document.class)));
        service = at(NOW);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    // ── issuing

    @Test
    @DisplayName("The interactive default is fifteen minutes")
    void interactiveDefaultIsFifteenMinutes() {
        DocumentLinkService.SignedLink link = service.signedLink(documentId);

        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(DocumentLinkService.INTERACTIVE_TTL).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("The link points at the download path and carries the token as t")
    void linkShape() {
        DocumentLinkService.SignedLink link = service.signedLink(documentId);

        assertThat(link.url()).startsWith(DocumentLinkServiceImpl.DEFAULT_BASE_URL + "?t=");
        assertThat(token(link)).startsWith(TENANT + "." + documentId + ".");
    }

    @Test
    @DisplayName("Seven days is accepted; a second more is refused")
    void sevenDaysIsTheCeiling() {
        assertThat(service.signedLink(documentId, Duration.ofDays(7)).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));

        assertThatThrownBy(
                        () -> service.signedLink(documentId, Duration.ofDays(7).plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 days");
    }

    @Test
    @DisplayName("A zero or negative lifetime is refused")
    void lifetimeMustBePositive() {
        assertThatThrownBy(() -> service.signedLink(documentId, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.signedLink(documentId, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.signedLink(documentId, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("No link for a document that is not live in the bound tenant")
    void noLinkForAnUnknownDocument() {
        assertThatThrownBy(() -> service.signedLink(UUID.randomUUID()))
                .isInstanceOf(DocumentService.NotFoundException.class);
    }

    @Test
    @DisplayName("No link with no tenant bound")
    void noLinkWithoutATenant() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.signedLink(documentId)).isInstanceOf(IllegalStateException.class);
    }

    // ── verifying

    @Test
    @DisplayName("A valid link verifies, and names its tenant, document and expiry")
    void validLinkVerifies() {
        DocumentLinkService.SignedLink link = service.signedLink(documentId);

        Optional<DocumentLinkService.LinkClaims> claims = service.verify(token(link));

        assertThat(claims).isPresent();
        assertThat(claims.get().tenantId()).isEqualTo(TENANT);
        assertThat(claims.get().documentId()).isEqualTo(documentId);
        assertThat(claims.get().expiresAt()).isEqualTo(link.expiresAt());
    }

    @Test
    @DisplayName("Tampered links do not verify - signature, document, tenant or expiry changed")
    void tamperedLinksDoNotVerify() {
        String[] parts = token(service.signedLink(documentId)).split("\\.");

        // The first character of the signature carries the top six bits of its first byte, so a
        // change there always changes the decoded bytes. The last character would not: it carries
        // padding bits a lenient decoder ignores.
        char first = parts[3].charAt(0);
        String badSignature = (first == 'A' ? 'B' : 'A') + parts[3].substring(1);
        assertThat(service.verify(join(parts[0], parts[1], parts[2], badSignature)))
                .as("signature changed")
                .isEmpty();

        assertThat(service.verify(join(parts[0], UUID.randomUUID().toString(), parts[2], parts[3])))
                .as("re-pointed at another document")
                .isEmpty();
        assertThat(service.verify(join(OTHER_TENANT.toString(), parts[1], parts[2], parts[3])))
                .as("moved to another tenant")
                .isEmpty();
        assertThat(service.verify(join(parts[0], parts[1], Long.toString(Long.parseLong(parts[2]) + 3600), parts[3])))
                .as("expiry pushed back an hour")
                .isEmpty();
    }

    @Test
    @DisplayName("A signature cut short does not verify - lengths are compared too")
    void truncatedSignatureDoesNotVerify() {
        String[] parts = token(service.signedLink(documentId)).split("\\.");

        assertThat(service.verify(join(parts[0], parts[1], parts[2], parts[3].substring(0, 10))))
                .isEmpty();
    }

    @Test
    @DisplayName("A link signed with another key does not verify")
    void anotherKeyDoesNotVerify() {
        DocumentLinkServiceImpl other = new DocumentLinkServiceImpl(
                documents, "some-other-secret-entirely-0123456789", "/x", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.verify(token(other.signedLink(documentId)))).isEmpty();
    }

    @Test
    @DisplayName("An expired link does not verify; one second before expiry it still does")
    void expiredLinkDoesNotVerify() {
        DocumentLinkService.SignedLink link = service.signedLink(documentId);
        String token = token(link);

        assertThat(at(link.expiresAt().minusSeconds(1)).verify(token)).isPresent();
        assertThat(at(link.expiresAt()).verify(token)).isEmpty();
        assertThat(at(link.expiresAt().plus(Duration.ofDays(30))).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("Malformed tokens are refused, never thrown on")
    void malformedTokensAreRefused() {
        String[] parts = token(service.signedLink(documentId)).split("\\.");

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
    @DisplayName("Only the canonical spelling verifies - one link cannot be written several ways")
    void nonCanonicalSpellingIsRefused() {
        // A fixed id with hex letters in it. The tenant id is all digits, and a random id could in
        // principle be too, so upper-casing either could leave the token unchanged and prove nothing.
        UUID lettered = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(documents.findByIdAndTenantIdAndDeletedFalse(lettered, TENANT))
                .thenReturn(Optional.of(mock(Document.class)));
        String[] parts = token(service.signedLink(lettered)).split("\\.");
        assertThat(service.verify(join(parts)))
                .as("the control: canonical spelling verifies")
                .isPresent();

        assertThat(service.verify(join(parts[0], parts[1].toUpperCase(Locale.ROOT), parts[2], parts[3])))
                .isEmpty();
        assertThat(service.verify(join(parts[0], parts[1], "0" + parts[2], parts[3])))
                .isEmpty();
    }

    // ── the secret

    @Test
    @DisplayName("No secret, a blank one, or a short one: the service refuses to exist")
    void secretIsRequiredAndLong() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThatThrownBy(() -> new DocumentLinkServiceImpl(documents, null, "/x", clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DOCUMENT_LINK_SECRET");
        assertThatThrownBy(() -> new DocumentLinkServiceImpl(documents, "   ", "/x", clock))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DocumentLinkServiceImpl(documents, "short-secret", "/x", clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(DocumentLinkServiceImpl.MIN_SECRET_LENGTH));
    }

    @Test
    @DisplayName("A link's toString withholds the URL, which carries the signature")
    void toStringWithholdsTheUrl() {
        DocumentLinkService.SignedLink link = service.signedLink(documentId);

        assertThat(link.toString()).doesNotContain(token(link)).contains("<withheld>");
    }

    // ── helpers

    private DocumentLinkServiceImpl at(Instant instant) {
        return new DocumentLinkServiceImpl(
                documents, SECRET, DocumentLinkServiceImpl.DEFAULT_BASE_URL, Clock.fixed(instant, ZoneOffset.UTC));
    }

    static String token(DocumentLinkService.SignedLink link) {
        return link.url().substring(link.url().indexOf("?t=") + 3);
    }

    private static String join(String... parts) {
        return String.join(".", parts);
    }
}
