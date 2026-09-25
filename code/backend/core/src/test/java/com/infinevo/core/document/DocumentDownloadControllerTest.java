package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * W-21 decision D1 — the public download: a valid link serves the file with the tenant it names bound
 * for exactly the length of the read; every kind of refusal is the same {@code 404}.
 *
 * <p>No Spring context. That the path is reachable with no token is {@code ResourceServerConfigTest}'s
 * and {@code TenantContextFilterTest}'s; the round trip through the real chain is
 * {@code DocumentGuardIT}'s.
 */
class DocumentDownloadControllerTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);

    private DocumentLinkService links;
    private DocumentService documents;
    private DocumentDownloadController controller;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        links = mock(DocumentLinkService.class);
        documents = mock(DocumentService.class);
        controller = new DocumentDownloadController(links, documents);
        documentId = UUID.randomUUID();
        TenantContext.clear();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A valid link serves the file, with the tenant it names bound while reading - and only then")
    void validLinkServesTheFile() throws Exception {
        claims("good");
        AtomicReference<UUID> boundDuringOpen = new AtomicReference<>();
        when(documents.open(documentId)).thenAnswer(inv -> {
            boundDuringOpen.set(TenantContext.require());
            return content("offer letter.pdf", PDF);
        });

        ResponseEntity<?> response = controller.download("good");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(boundDuringOpen.get()).isEqualTo(TENANT);
        assertThat(TenantContext.isBound()).as("cleared on the way out").isFalse();
        try (InputStream body = ((InputStreamResource) response.getBody()).getInputStream()) {
            assertThat(body.readAllBytes()).isEqualTo(PDF);
        }
        HttpHeaders headers = response.getHeaders();
        assertThat(headers.getContentType()).hasToString("application/pdf");
        assertThat(headers.getContentLength()).isEqualTo(PDF.length);
        assertThat(headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment")
                .contains("offer");
        assertThat(headers.getCacheControl()).isEqualTo("no-store");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("A link that does not verify is 404 - and nothing is read")
    void badLinkIsNotFound() {
        when(links.verify(any())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.download("tampered");

        assertRefused(response);
        verify(documents, never()).open(any());
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("No token at all is the same 404")
    void missingTokenIsNotFound() {
        when(links.verify(null)).thenReturn(Optional.empty());

        assertRefused(controller.download(null));
    }

    @Test
    @DisplayName("A valid link to a document deleted since is the same 404, and the tenant is still cleared")
    void deletedDocumentIsTheSame404() {
        claims("good");
        when(documents.open(documentId)).thenThrow(new DocumentService.NotFoundException(documentId));

        assertRefused(controller.download("good"));
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("No storage in this runtime is 503, and the tenant is still cleared")
    void noStorageIs503() {
        claims("good");
        when(documents.open(documentId)).thenThrow(new DocumentService.StorageUnavailableException("none"));

        assertThat(controller.download("good").getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(TenantContext.isBound()).isFalse();
    }

    private void claims(String token) {
        when(links.verify(token))
                .thenReturn(Optional.of(new DocumentLinkService.LinkClaims(
                        TENANT, documentId, Instant.now().plusSeconds(60))));
    }

    private DocumentService.DocumentContent content(String fileName, byte[] bytes) {
        DocumentResponse metadata = new DocumentResponse(
                documentId,
                null,
                DocumentKind.EMPLOYEE_DOCUMENT,
                fileName,
                "application/pdf",
                bytes.length,
                "0".repeat(64),
                Instant.now(),
                "t");
        return new DocumentService.DocumentContent(metadata, new ByteArrayInputStream(bytes));
    }

    private static void assertRefused(ResponseEntity<?> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ApiErrorResponse) response.getBody()).message()).isEqualTo(DocumentDownloadController.NOT_A_LINK);
    }
}
