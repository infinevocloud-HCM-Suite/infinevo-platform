package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * W-21 spec section 7 — no log statement in the package emits a signature.
 *
 * <p>This test exists because incident 3 was a log line, not a crypto flaw: the legacy payslip token
 * was sound and {@code PublicPayslipController.java:43-44} wrote it to the log at INFO. So every path
 * in the package that handles a link — issuing it, verifying it, and each way verification refuses —
 * runs here with the package at DEBUG, the most verbose level anyone would set while chasing a bug,
 * and every captured event is searched for the token, the signature and the secret.
 */
class DocumentLoggingTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SECRET = "logging-test-document-link-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    private final Logger packageLogger = (Logger) LoggerFactory.getLogger("com.infinevo.core.document");
    private ListAppender<ILoggingEvent> captured;
    private Level previousLevel;

    private DocumentRepository documents;
    private UUID documentId;

    @BeforeEach
    void capture() {
        captured = new ListAppender<>();
        captured.start();
        packageLogger.addAppender(captured);
        previousLevel = packageLogger.getLevel();
        packageLogger.setLevel(Level.DEBUG);

        documents = mock(DocumentRepository.class);
        documentId = UUID.randomUUID();
        when(documents.findByIdAndTenantIdAndDeletedFalse(documentId, TENANT))
                .thenReturn(Optional.of(mock(Document.class)));
        when(documents.saveAndFlush(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void release() {
        packageLogger.detachAppender(captured);
        packageLogger.setLevel(previousLevel);
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Issuing and verifying a link - valid, tampered, expired, malformed - logs no token, signature or secret")
    void noLinkPathLogsASecret() {
        DocumentLinkServiceImpl links = at(NOW);

        DocumentLinkService.SignedLink link = links.signedLink(documentId, Duration.ofDays(7));
        String token = DocumentLinkServiceTest.token(link);
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String tampered = token.substring(0, token.lastIndexOf('.') + 1)
                + (signature.charAt(0) == 'A' ? 'B' : 'A')
                + signature.substring(1);

        assertThat(links.verify(token)).isPresent();
        assertThat(links.verify(tampered)).isEmpty();
        assertThat(at(link.expiresAt().plusSeconds(1)).verify(token)).isEmpty();
        assertThat(links.verify("not.a.token")).isEmpty();
        assertThat(links.verify(token + ".extra")).isEmpty();

        assertThat(captured.list)
                .as("the paths above logged something - the check below would be vacuous otherwise")
                .isNotEmpty();
        assertLogged(token, signature, tampered, SECRET, link.url());
    }

    @Test
    @DisplayName("Storing a file logs its id and size, never a link")
    void storingLogsNoSecret() {
        DocumentServiceImpl store = new DocumentServiceImpl(
                documents,
                mock(EmployeeRepository.class),
                new DocumentServiceTest.RecordingBlobStorage(),
                "documents",
                1024,
                List.of("csv"));

        store.store(
                DocumentKind.EXPORT, null, "e.csv", new ByteArrayInputStream("a,b\n".getBytes(StandardCharsets.UTF_8)));

        assertThat(captured.list).isNotEmpty();
        assertLogged(SECRET);
    }

    private DocumentLinkServiceImpl at(Instant instant) {
        return new DocumentLinkServiceImpl(
                documents, SECRET, DocumentLinkServiceImpl.DEFAULT_BASE_URL, Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** Fails if any captured event - its message, its arguments or its formatted text - holds a needle. */
    private void assertLogged(String... needles) {
        for (ILoggingEvent event : captured.list) {
            List<String> haystack = new ArrayList<>();
            haystack.add(event.getMessage());
            haystack.add(event.getFormattedMessage());
            if (event.getArgumentArray() != null) {
                for (Object argument : event.getArgumentArray()) {
                    haystack.add(String.valueOf(argument));
                }
            }
            for (String text : haystack) {
                for (String needle : needles) {
                    assertThat(text)
                            .as("log event [%s] must not contain a link secret", event.getFormattedMessage())
                            .doesNotContain(needle);
                }
            }
        }
    }
}
