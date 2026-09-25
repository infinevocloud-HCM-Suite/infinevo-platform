package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-21 spec section 7 — upload, then read back, round-trips against Azurite and the checksum matches.
 *
 * <p>Read back straight from storage, through {@link BlobStorage#open}, so what this proves is the
 * store itself: the bytes that went in are the bytes stored, at the path the row names, and a link
 * issued for the row verifies to it. The same round trip through the public download endpoint, with
 * no token, is {@code DocumentGuardIT.adminLifecycle}.
 */
@SpringBootTest(classes = DocumentTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            DocumentTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class DocumentUploadIT extends AbstractIntegrationTest {

    private static final byte[] PDF =
            "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentLinkService linkService;

    @Autowired
    private BlobStorage blobStorage;

    private UUID tenant;

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        tenant = DocumentTestSchema.insertTenant("Upload " + UUID.randomUUID());
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Stored bytes read back from Azurite unchanged, and their SHA-256 is the row's")
    void uploadRoundTrips() throws Exception {
        TenantContext.set(tenant);

        UUID id = documentService.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "offer.pdf", in(PDF));

        String container = (String) DocumentTestSchema.readColumn(id, "blob_container");
        String path = (String) DocumentTestSchema.readColumn(id, "blob_path");
        String checksum = ((String) DocumentTestSchema.readColumn(id, "checksum_sha256")).trim();
        byte[] stored;
        try (InputStream blob = blobStorage.open(container, path)) {
            stored = blob.readAllBytes();
        }

        assertThat(stored).isEqualTo(PDF);
        assertThat(DocumentServiceImpl.sha256Hex(stored)).isEqualTo(checksum);
        assertThat(path).isEqualTo(tenant + "/tenant/EMPLOYEE_DOCUMENT/" + id);
    }

    @Test
    @DisplayName("A document of an employee in the bound tenant lands under that employee")
    void employeeDocumentLandsUnderTheEmployee() throws Exception {
        UUID employee = DocumentTestSchema.insertEmployee(tenant, "E-" + UUID.randomUUID());
        TenantContext.set(tenant);

        UUID id = documentService.store(DocumentKind.EMPLOYEE_DOCUMENT, employee, "offer.pdf", in(PDF));

        assertThat(DocumentTestSchema.readColumn(id, "blob_path"))
                .isEqualTo(tenant + "/" + employee + "/EMPLOYEE_DOCUMENT/" + id);
        assertThat(DocumentTestSchema.readColumn(id, "employee_id")).isEqualTo(employee);
    }

    @Test
    @DisplayName("Another tenant's employee is refused - the foreign key alone would have accepted it")
    void otherTenantsEmployeeIsRefused() throws Exception {
        UUID otherTenant = DocumentTestSchema.insertTenant("Other " + UUID.randomUUID());
        UUID theirEmployee = DocumentTestSchema.insertEmployee(otherTenant, "E-" + UUID.randomUUID());
        TenantContext.set(tenant);

        assertThatThrownBy(() ->
                        documentService.store(DocumentKind.EMPLOYEE_DOCUMENT, theirEmployee, "offer.pdf", in(PDF)))
                .isInstanceOf(DocumentService.ValidationException.class);
    }

    @Test
    @DisplayName("A link issued for a stored document verifies to that document, in that tenant")
    void issuedLinkVerifies() throws Exception {
        TenantContext.set(tenant);
        UUID id = documentService.store(DocumentKind.EMPLOYEE_DOCUMENT, null, "offer.pdf", in(PDF));

        DocumentLinkService.SignedLink link = linkService.signedLink(id, Duration.ofDays(7));
        Optional<DocumentLinkService.LinkClaims> claims = linkService.verify(DocumentLinkServiceTest.token(link));

        assertThat(claims).isPresent();
        assertThat(claims.get().tenantId()).isEqualTo(tenant);
        assertThat(claims.get().documentId()).isEqualTo(id);
    }

    @Test
    @DisplayName("A generated file is streamed to Azurite from disk and reads back unchanged (storeFile, D2)")
    void generatedFileRoundTrips(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        byte[] csv = "number,name\nA-001,Asha\n".getBytes(StandardCharsets.UTF_8);
        java.nio.file.Path export = java.nio.file.Files.write(dir.resolve("export.csv"), csv);
        TenantContext.set(tenant);

        UUID id = documentService.storeFile(DocumentKind.EXPORT, null, "export.csv", export);

        try (InputStream stored = documentService.open(id).content()) {
            assertThat(stored.readAllBytes()).isEqualTo(csv);
        }
    }

    private static InputStream in(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}
