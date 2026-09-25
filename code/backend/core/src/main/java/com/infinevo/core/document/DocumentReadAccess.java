package com.infinevo.core.document;

import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Who may read a document's metadata or take a link to it (W-21, spec section 4):
 * {@code core.document.read}, or {@code core.document.read_own} when the document's employee is the
 * caller. A document with no {@code employee_id} — an export, a tenant-level file — is never readable
 * through {@code read_own}.
 *
 * <p>Here rather than in {@code @RequiresAction} because the annotation takes one code, and "read, or
 * for yourself read_own" is two. {@code W-13.4} adds {@code anyOf} to the annotation; the ownership
 * test stays here either way, since only the document knows whose it is.
 *
 * <p>Here rather than in {@link DocumentService#get} because the service is the contract other
 * modules call ({@code 12-core-contracts.md:107}): an export links its own file for a caller who holds
 * {@code core.report.read}, not a document code. The check belongs to the document endpoints.
 *
 * <p><strong>Fails closed</strong>, as {@link PermissionService} does: a caller holding neither code
 * is refused before the document is looked up, and a {@code read_own} caller whose login is linked to
 * no employee is refused for every document.
 */
@Component
class DocumentReadAccess {

    static final String READ = "core.document.read";
    static final String READ_OWN = "core.document.read_own";

    private final PermissionService permissions;
    private final DocumentService documentService;
    private final DocumentOwnerResolver owner;

    DocumentReadAccess(PermissionService permissions, DocumentService documentService, DocumentOwnerResolver owner) {
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
    }

    /**
     * The document's metadata, if the caller may read it.
     *
     * @throws PermissionDeniedException answered {@code 403}, when the caller may not
     * @throws DocumentService.NotFoundException answered {@code 404}, for a caller who holds a read code
     *     and names a document the bound tenant does not have
     */
    DocumentResponse readable(UUID documentId) {
        if (permissions.holds(READ)) {
            return documentService.get(documentId);
        }
        if (permissions.holds(READ_OWN)) {
            DocumentResponse document = documentService.get(documentId);
            Optional<UUID> caller = owner.currentEmployeeId();
            if (document.employeeId() != null
                    && caller.isPresent()
                    && document.employeeId().equals(caller.get())) {
                return document;
            }
        }
        throw new PermissionDeniedException(READ);
    }
}
