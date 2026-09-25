package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * W-21 spec section 7 — tenant A cannot read tenant B's document rows as {@code app_user}.
 *
 * <p>Asserted twice, as {@code EmployeeDetailRlsIT} does it: on a raw {@code app_user} connection, so
 * the policy alone is what hides the other tenant, and through the services, so no read path leaks
 * around it. Plus decision 2 at the database: {@code app_user} cannot {@code DELETE} a document row.
 */
@SpringBootTest(classes = DocumentTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            DocumentTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class DocumentRlsIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentLinkService linkService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID documentOfA;
    private UUID documentOfB;

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        tenantA = DocumentTestSchema.insertTenant("A " + UUID.randomUUID());
        tenantB = DocumentTestSchema.insertTenant("B " + UUID.randomUUID());
        documentOfA = DocumentTestSchema.insertDocumentRow(tenantA, DocumentKind.EMPLOYEE_DOCUMENT);
        documentOfB = DocumentTestSchema.insertDocumentRow(tenantB, DocumentKind.EMPLOYEE_DOCUMENT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Row-level security alone hides tenant B's row from app_user bound to tenant A")
    void policyHidesTheOtherTenant() throws SQLException {
        assertThat(visibleToAppUser(tenantA, documentOfA))
                .as("the control: A sees its own")
                .isTrue();
        assertThat(visibleToAppUser(tenantA, documentOfB))
                .as("A must not see B's")
                .isFalse();
        assertThat(visibleToAppUser(tenantB, documentOfB))
                .as("the control: B sees its own")
                .isTrue();
    }

    @Test
    @DisplayName("With no tenant bound, app_user sees no document at all")
    void unboundSeesNothing() throws SQLException {
        try (Connection conn = DocumentTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.document")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("app_user cannot DELETE a document row, even its own tenant's - deletion is soft")
    void appUserCannotDelete() throws SQLException {
        try (Connection conn = DocumentTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            DocumentTestSchema.bindTenant(conn, tenantA);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.document WHERE id = ?")) {
                ps.setObject(1, documentOfA);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            } finally {
                conn.rollback();
            }
        }
        assertThat(DocumentTestSchema.rowExists(documentOfA)).isTrue();
    }

    @Test
    @DisplayName("Through the services, tenant A can neither read, link nor delete tenant B's document")
    void servicesRefuseTheOtherTenant() throws SQLException {
        TenantContext.set(tenantA);

        assertThat(documentService.get(documentOfA).id()).as("the control").isEqualTo(documentOfA);
        assertThatThrownBy(() -> documentService.get(documentOfB))
                .isInstanceOf(DocumentService.NotFoundException.class);
        assertThatThrownBy(() -> linkService.signedLink(documentOfB))
                .isInstanceOf(DocumentService.NotFoundException.class);
        assertThatThrownBy(() -> documentService.delete(documentOfB))
                .isInstanceOf(DocumentService.NotFoundException.class);

        assertThat(DocumentTestSchema.readColumn(documentOfB, "is_deleted")).isEqualTo(false);
    }

    private static boolean visibleToAppUser(UUID boundTenant, UUID documentId) throws SQLException {
        try (Connection conn = DocumentTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                DocumentTestSchema.bindTenant(conn, boundTenant);
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.document WHERE id = ?")) {
                    ps.setObject(1, documentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
