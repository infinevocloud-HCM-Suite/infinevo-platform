package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * W-73.5 spec section 7 — the employee page's Documents tab: {@code GET /api/v1/employees/{id}/documents}
 * lists that employee's {@code EMPLOYEE_DOCUMENT} rows with their label, scoped to the tenant; the
 * employee sees their own through {@code /me/documents}; delete stays behind {@code core.document.delete}.
 *
 * <p>Through the real filter chain, as {@link DocumentGuardIT}: {@code tenant-admin} holds every code;
 * {@code hr} holds {@code core.employee.read}, {@code core.employee.update} and
 * {@code core.document.upload} but not {@code core.document.delete}; {@code employee} holds
 * {@code core.document.read_own} and not {@code core.employee.read}.
 */
@SpringBootTest(classes = DocumentTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            DocumentTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class EmployeeDocumentIT extends AbstractIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID adminSub;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = DocumentTestSchema.insertTenant("Employee documents " + UUID.randomUUID());
        adminSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, adminSub, "tenant-admin");
        DocumentTestSchema.nameMember(tenant, adminSub, "Asha", "Rao");
        employee = DocumentTestSchema.insertEmployee(tenant, "E-" + UUID.randomUUID());
    }

    @Test
    @DisplayName("tenant-admin uploads an offer letter with its label; the employee's list shows it, and nothing else")
    void uploadWithLabelThenList() throws Exception {
        UUID colleague = DocumentTestSchema.insertEmployee(tenant, "C-" + UUID.randomUUID());
        DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.LEAVE_ATTACHMENT, employee);

        UUID id = uploadOffer(adminSub);

        mvc.perform(as(adminSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].fileName").value("offer.pdf"))
                .andExpect(jsonPath("$[0].label").value("OFFER_LETTER"))
                .andExpect(jsonPath("$[0].sizeBytes").value(PDF.length))
                // Spec section 8, "your name": the uploader's account name, not their Keycloak subject.
                .andExpect(jsonPath("$[0].uploadedBy").value("Asha Rao"))
                .andExpect(jsonPath("$[0].uploadedAt").exists())
                // The tab's view, not the store's: no storage internals, no checksum.
                .andExpect(jsonPath("$[0].checksumSha256").doesNotExist())
                .andExpect(jsonPath("$[0].blobPath").doesNotExist());

        mvc.perform(as(adminSub, get("/api/v1/employees/" + colleague + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("An uploader with no account in the tenant is shown as stored - here the system actor")
    void uploaderWithNoAccountIsShownAsStored() throws Exception {
        DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT, employee);

        mvc.perform(as(adminSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].uploadedBy").value("system"));
    }

    @Test
    @DisplayName("payroll-officer holds core.employee.read but not core.document.read: 403 on the list")
    void payrollOfficerCannotListDocuments() throws Exception {
        UUID officerSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, officerSub, "payroll-officer");
        uploadOffer(adminSub);

        mvc.perform(as(officerSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.document.read")));
        // Refused before the lookup: an unknown employee is the same 403, not a 404.
        mvc.perform(as(officerSub, get("/api/v1/employees/" + UUID.randomUUID() + "/documents")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("V166: the database refuses a label on any kind but EMPLOYEE_DOCUMENT, written past the service")
    void databaseRefusesALabelOnAnotherKind() throws Exception {
        UUID leave = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.LEAVE_ATTACHMENT, employee);
        UUID own = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT, employee);

        try (java.sql.Connection conn = DocumentTestSchema.migrationConnection();
                java.sql.PreparedStatement ps =
                        conn.prepareStatement("UPDATE core.document SET label = 'ID_PROOF' WHERE id = ?")) {
            ps.setObject(1, leave);
            assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("document_label_check");
            ps.setObject(1, own);
            assertThat(ps.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("A label on a leave attachment is 400 on label; a label outside the six is 400 VALIDATION_FAILED")
    void labelIsValidated() throws Exception {
        mvc.perform(as(adminSub, upload("note.pdf", "LEAVE_ATTACHMENT", employee, "OFFER_LETTER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.label").exists());
        mvc.perform(as(adminSub, upload("offer.pdf", "EMPLOYEE_DOCUMENT", employee, "BOGUS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(as(adminSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("The list is tenant-scoped: another tenant's admin gets 404 - the employee is not in their tenant")
    void listIsTenantScoped() throws Exception {
        uploadOffer(adminSub);
        UUID otherTenant = DocumentTestSchema.insertTenant("Other " + UUID.randomUUID());
        UUID otherAdmin = UUID.randomUUID();
        DocumentTestSchema.insertMember(otherTenant, otherAdmin, "tenant-admin");

        mvc.perform(asIn(otherTenant, otherAdmin, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(as(adminSub, get("/api/v1/employees/" + UUID.randomUUID() + "/documents")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("employee: 403 on the employee list, but /me/documents lists their own rows, with the label")
    void employeeSeesOwnOnlyThroughMe() throws Exception {
        UUID employeeSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, employeeSub, "employee");
        DocumentTestSchema.linkLogin(tenant, employeeSub, employee);
        UUID colleague = DocumentTestSchema.insertEmployee(tenant, "C-" + UUID.randomUUID());
        UUID theirs = DocumentTestSchema.insertDocumentRow(tenant, DocumentKind.EMPLOYEE_DOCUMENT, colleague);
        UUID mine = uploadOffer(adminSub);

        mvc.perform(as(employeeSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.employee.read")));

        String body = mvc.perform(as(employeeSub, get("/api/v1/me/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(mine.toString()))
                .andExpect(jsonPath("$[0].label").value("OFFER_LETTER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain(theirs.toString());
    }

    @Test
    @DisplayName("Delete: hr is 403 and the row stays live - the server guard is core.document.delete, as today;"
            + " tenant-admin is 204 and the document leaves the list")
    void deleteNeedsTheDocumentDeleteCode() throws Exception {
        UUID hrSub = UUID.randomUUID();
        DocumentTestSchema.insertMember(tenant, hrSub, "hr");
        UUID id = uploadOffer(hrSub);

        mvc.perform(as(hrSub, delete("/api/v1/documents/" + id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("core.document.delete")));
        assertThat(DocumentTestSchema.readColumn(id, "is_deleted")).isEqualTo(false);
        mvc.perform(as(hrSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(as(adminSub, delete("/api/v1/documents/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(adminSub, get("/api/v1/employees/" + employee + "/documents")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(DocumentTestSchema.readColumn(id, "is_deleted")).isEqualTo(true);
    }

    // ── helpers

    /** Uploads {@code offer.pdf} as {@code sub}, labelled an offer letter, and returns its id. */
    private UUID uploadOffer(UUID sub) throws Exception {
        String created = mvc.perform(as(sub, upload("offer.pdf", "EMPLOYEE_DOCUMENT", employee, "OFFER_LETTER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("OFFER_LETTER"))
                .andExpect(jsonPath("$.employeeId").value(employee.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(json.readTree(created).get("id").asText());
    }

    private static MockHttpServletRequestBuilder upload(String fileName, String kind, UUID employeeId, String label) {
        return multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", PDF))
                .param("kind", kind)
                .param("employeeId", employeeId.toString())
                .param("label", label);
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return asIn(tenant, sub, request);
    }

    private static MockHttpServletRequestBuilder asIn(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
