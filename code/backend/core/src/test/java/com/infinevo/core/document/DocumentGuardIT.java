package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.security.PublicEndpoints;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-21 spec section 7 — each endpoint answers {@code 403} without its code, and gets through with it.
 *
 * <p>Through the real filter chain: the tenant filter checks membership, the permission check reads
 * the roles the {@code V022} trigger seeded and {@code V037}'s grants. {@code tenant-admin} holds every
 * {@code core.document.*} code; {@code hr} holds {@code read} and {@code upload}; {@code employee} holds
 * {@code read_own} only.
 *
 * <p>{@code read_own} — an employee reads their own document and not a colleague's, and never one
 * with no {@code employee_id} — needs to know which employee the caller is. {@code W-13.4} provides
 * that; here {@link DocumentTestApp.LinkedOwners} stands in for it.
 */
@SpringBootTest(classes = DocumentTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            DocumentTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class DocumentGuardIT extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DocumentTestApp.LinkedOwners owners;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID adminSub;
    private UUID employeeSub;

    @BeforeEach
    void seed() throws SQLException {
        tenant = DocumentTestSchema.insertTenant("Guard " + UUID.randomUUID());
        adminSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, adminSub, "tenant-admin");
        employeeSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, employeeSub, "employee");
    }

    // ── refused

    @Test
    @DisplayName("employee: 403 on upload, and nothing is stored")
    void employeeCannotUpload() throws Exception {
        mvc.perform(as(employeeSub, upload("offer.pdf", PDF, "EMPLOYEE_DOCUMENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.document.upload")));
    }

    @Test
    @DisplayName(
            "employee: 403 on metadata, link and delete of a document with no employee_id - read_own never reaches one")
    void employeeCannotReadLinkOrDelete() throws Exception {
        UUID id = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT);

        mvc.perform(as(employeeSub, get("/api/v1/documents/" + id))).andExpect(status().isForbidden());
        mvc.perform(as(employeeSub, get("/api/v1/documents/" + id + "/link"))).andExpect(status().isForbidden());
        mvc.perform(as(employeeSub, delete("/api/v1/documents/" + id))).andExpect(status().isForbidden());

        assertThat(DocumentTestSchema.readColumn(id, "is_deleted")).isEqualTo(false);
    }

    @Test
    @DisplayName("employee with read_own: their own document 200, a colleague's 403, one with no employee_id 403")
    void readOwnReadsOnlyTheCallersOwn() throws Exception {
        UUID me = DocumentTestSchema.insertEmployee(tenant, "ME-" + UUID.randomUUID());
        UUID colleague = DocumentTestSchema.insertEmployee(tenant, "COL-" + UUID.randomUUID());
        owners.link(employeeSub, me);
        UUID mine = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT, me);
        UUID theirs = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT, colleague);
        UUID tenantLevel = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT);

        mvc.perform(as(employeeSub, get("/api/v1/documents/" + mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(me.toString()));
        mvc.perform(as(employeeSub, get("/api/v1/documents/" + mine + "/link")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith(DocumentLinkServiceImpl.DEFAULT_BASE_URL + "?t=")));

        mvc.perform(as(employeeSub, get("/api/v1/documents/" + theirs)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(as(employeeSub, get("/api/v1/documents/" + theirs + "/link")))
                .andExpect(status().isForbidden());
        mvc.perform(as(employeeSub, get("/api/v1/documents/" + tenantLevel))).andExpect(status().isForbidden());
        mvc.perform(as(employeeSub, get("/api/v1/documents/" + tenantLevel + "/link")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("hr: V037's grants - upload 201 and read 200, but delete 403, which stays with the administrator")
    void hrUploadsAndReadsButCannotDelete() throws Exception {
        UUID hrSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, hrSub, "hr");

        String created = mvc.perform(as(hrSub, upload("offer.pdf", PDF, "EMPLOYEE_DOCUMENT")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(json.readTree(created).get("id").asText());

        mvc.perform(as(hrSub, get("/api/v1/documents/" + id))).andExpect(status().isOk());
        mvc.perform(as(hrSub, delete("/api/v1/documents/" + id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.document.delete")));
    }

    @Test
    @DisplayName("A user with no membership in the tenant never reaches the guard")
    void nonMemberIsRefusedAtTheEdge() throws Exception {
        UUID id = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT);

        mvc.perform(as(UUID.randomUUID(), get("/api/v1/documents/" + id))).andExpect(status().isForbidden());
    }

    // ── through

    @Test
    @DisplayName("tenant-admin: upload 201, metadata 200, link 200, delete 204, then 404")
    void adminLifecycle() throws Exception {
        String created = mvc.perform(as(adminSub, upload("offer.pdf", PDF, "EMPLOYEE_DOCUMENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("offer.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID id = UUID.fromString(json.readTree(created).get("id").asText());

        mvc.perform(as(adminSub, get("/api/v1/documents/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                // Metadata, never storage internals.
                .andExpect(jsonPath("$.blobPath").doesNotExist())
                .andExpect(jsonPath("$.blobContainer").doesNotExist());

        String linked = mvc.perform(as(adminSub, get("/api/v1/documents/" + id + "/link")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith(DocumentLinkServiceImpl.DEFAULT_BASE_URL + "?t=")))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String url = json.readTree(linked).get("url").asText();
        String token = url.substring(url.indexOf("?t=") + 3);

        // D1: the link opens with NO token and NO session - through the real filter chain, which
        // lets exactly this path through and binds nothing; the controller binds the tenant.
        byte[] served = mvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD).param("t", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(served).isEqualTo(PDF);

        mvc.perform(as(adminSub, delete("/api/v1/documents/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(adminSub, get("/api/v1/documents/" + id))).andExpect(status().isNotFound());
        // A link issued before the delete stops working with it.
        mvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD).param("t", token)).andExpect(status().isNotFound());

        // Soft: the row is still there, flagged.
        assertThat(DocumentTestSchema.readColumn(id, "is_deleted")).isEqualTo(true);
    }

    /**
     * The status for "no token" is the chain's, not this ticket's. This context has no
     * {@code ResourceServerConfig} on its scan, so {@code TenantBindingAutoConfiguration}'s fallback
     * chain answers, and with no login mechanism Spring Security refuses with a bare {@code 403}. The
     * running application answers {@code 401} — {@code ResourceServerConfigTest} asserts that on
     * document paths. What is asserted here is the part that is this ticket's: the download is open,
     * and the reads are refused by the chain before any controller runs — an empty body, not the
     * {@code FORBIDDEN} envelope a refused permission check would write.
     */
    @Test
    @DisplayName(
            "Without a token only the download is open: a tampered link is 404, every other document path refused by the chain")
    void onlyTheDownloadIsPublic() throws Exception {
        UUID id = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT);

        mvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD).param("t", "not.a.real.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD)).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/documents/" + id))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
        mvc.perform(get("/api/v1/documents/" + id + "/link"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("tenant-admin: 400 uploading a payslip or an export - the platform writes those itself")
    void systemKindsAreRefused() throws Exception {
        mvc.perform(as(adminSub, upload("slip.pdf", PDF, "PAYSLIP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.kind").exists());
        mvc.perform(as(adminSub, upload("export.pdf", PDF, "EXPORT"))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("tenant-admin: 415 for a zip, 400 for an unknown kind, in the platform's envelope")
    void unsupportedAndUnreadableRequests() throws Exception {
        mvc.perform(as(adminSub, upload("archive.zip", new byte[] {'P', 'K', 3, 4}, "EMPLOYEE_DOCUMENT")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(adminSub, upload("offer.pdf", PDF, "NOT_A_KIND")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Another tenant's admin gets 404 on this tenant's document - not 403, which would confirm it exists")
    void otherTenantSeesNothing() throws Exception {
        UUID id = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT);
        UUID otherTenant = DocumentTestSchema.insertTenant("Other " + UUID.randomUUID());
        UUID otherAdmin = UUID.randomUUID();
        DocumentTestSchema.insertMember(otherTenant, otherAdmin, "tenant-admin");

        mvc.perform(asIn(otherTenant, otherAdmin, get("/api/v1/documents/" + id)))
                .andExpect(status().isNotFound());
        mvc.perform(asIn(otherTenant, otherAdmin, get("/api/v1/documents/" + id + "/link")))
                .andExpect(status().isNotFound());
        mvc.perform(asIn(otherTenant, otherAdmin, delete("/api/v1/documents/" + id)))
                .andExpect(status().isNotFound());
        assertThat(DocumentTestSchema.readColumn(id, "is_deleted")).isEqualTo(false);
    }

    // ── helpers

    private MockHttpServletRequestBuilder upload(String fileName, byte[] content, String kind) {
        return multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
                .param("kind", kind);
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return asIn(tenant, sub, request);
    }

    private static MockHttpServletRequestBuilder asIn(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
