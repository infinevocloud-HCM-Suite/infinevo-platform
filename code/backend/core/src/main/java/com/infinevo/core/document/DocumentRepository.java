package com.infinevo.core.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@code core.document} (W-21).
 *
 * <p>The two rules {@code EmployeeRepository} follows, for the same reasons: <strong>every read names
 * the tenant</strong>, so the query matches the {@code (tenant_id, ...)} indexes and cannot be called
 * by accident from a path where none is bound; and <strong>every finder declared here excludes
 * soft-deleted rows</strong>.
 *
 * <p>The inherited {@code findById} and {@code findAll} do neither, and nothing in this package uses
 * them. {@code deleteById} would fail anyway: {@code app_user} holds no {@code DELETE} on the table
 * ({@code V037__document.sql}).
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** The one live document with this id in this tenant, if there is one. */
    Optional<Document> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}
