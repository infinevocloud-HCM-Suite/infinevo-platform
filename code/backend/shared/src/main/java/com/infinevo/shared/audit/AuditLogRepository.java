package com.infinevo.shared.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Reads and appends {@code core.audit_log}. Never updates, never deletes - {@code app_user} holds
 * no such grant ({@code V008__audit_log.sql:44}).
 *
 * <p>The filters behind {@code GET /api/v1/audit} are all optional, so they are built as a {@link
 * org.springframework.data.jpa.domain.Specification} in {@link AuditQueryService} rather than as a
 * query with {@code :param is null} branches: PostgreSQL cannot infer the type of a bound null on
 * its own side of such a comparison and rejects the statement with "could not determine data type
 * of parameter".
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {}
