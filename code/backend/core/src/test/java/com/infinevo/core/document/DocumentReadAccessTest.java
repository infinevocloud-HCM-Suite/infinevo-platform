package com.infinevo.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-21 spec section 4 — {@code core.document.read}, or {@code core.document.read_own} when the
 * document's employee is the caller; a document with no {@code employee_id} is never readable through
 * {@code read_own}.
 */
class DocumentReadAccessTest {

    private final UUID me = UUID.randomUUID();
    private final UUID colleague = UUID.randomUUID();

    private PermissionService permissions;
    private DocumentService documents;
    private DocumentOwnerResolver owner;
    private DocumentReadAccess access;

    @BeforeEach
    void setUp() {
        permissions = mock(PermissionService.class);
        documents = mock(DocumentService.class);
        owner = mock(DocumentOwnerResolver.class);
        when(owner.currentEmployeeId()).thenReturn(Optional.of(me));
        access = new DocumentReadAccess(permissions, documents, owner);
    }

    @Test
    @DisplayName("core.document.read reads any document, including one with no employee")
    void readReadsAnything() {
        holds(DocumentReadAccess.READ);
        UUID tenantLevel = document(null);
        UUID colleagues = document(colleague);

        assertThat(access.readable(tenantLevel).id()).isEqualTo(tenantLevel);
        assertThat(access.readable(colleagues).id()).isEqualTo(colleagues);
    }

    @Test
    @DisplayName("read_own reads the caller's own document")
    void readOwnReadsOwn() {
        holds(DocumentReadAccess.READ_OWN);
        UUID mine = document(me);

        assertThat(access.readable(mine).employeeId()).isEqualTo(me);
    }

    @Test
    @DisplayName("read_own does not read a colleague's document")
    void readOwnRefusesColleague() {
        holds(DocumentReadAccess.READ_OWN);
        UUID theirs = document(colleague);

        assertThatThrownBy(() -> access.readable(theirs))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining(DocumentReadAccess.READ);
    }

    @Test
    @DisplayName("read_own never reads a document with no employee_id")
    void readOwnRefusesTenantLevel() {
        holds(DocumentReadAccess.READ_OWN);
        UUID tenantLevel = document(null);

        assertThatThrownBy(() -> access.readable(tenantLevel)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("read_own with a login linked to no employee (before W-13.4) reads nothing")
    void readOwnUnlinkedReadsNothing() {
        holds(DocumentReadAccess.READ_OWN);
        when(owner.currentEmployeeId()).thenReturn(Optional.empty());
        UUID mine = document(me);

        assertThatThrownBy(() -> access.readable(mine)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("Neither code: refused before the document is looked up")
    void neitherCodeIsRefusedFirst() {
        UUID any = UUID.randomUUID();

        assertThatThrownBy(() -> access.readable(any)).isInstanceOf(PermissionDeniedException.class);
        verify(documents, never()).get(any());
    }

    @Test
    @DisplayName("read_own naming a document the tenant does not have is 404, not 403")
    void readOwnMissingIsNotFound() {
        holds(DocumentReadAccess.READ_OWN);
        UUID missing = UUID.randomUUID();
        when(documents.get(missing)).thenThrow(new DocumentService.NotFoundException(missing));

        assertThatThrownBy(() -> access.readable(missing)).isInstanceOf(DocumentService.NotFoundException.class);
    }

    private void holds(String action) {
        when(permissions.holds(action)).thenReturn(true);
    }

    private UUID document(UUID employeeId) {
        UUID id = UUID.randomUUID();
        when(documents.get(id))
                .thenReturn(new DocumentResponse(
                        id,
                        employeeId,
                        DocumentKind.EMPLOYEE_DOCUMENT,
                        "offer.pdf",
                        "application/pdf",
                        3,
                        "0".repeat(64),
                        Instant.parse("2026-09-25T10:00:00Z"),
                        "test"));
        return id;
    }
}
