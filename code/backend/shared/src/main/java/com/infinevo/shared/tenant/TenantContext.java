package com.infinevo.shared.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the tenant for the current request or job.
 *
 * <p>Set once, at the edge, by the binding filter ({@code W-08}) or by the job runner.
 * Never by business code. Every read below that point sees the same value, which is what
 * lets the tenant stop being a parameter threaded through method signatures - today it is
 * passed explicitly into roughly 270 controller methods.
 *
 * <p><strong>Always clear it in a {@code finally} block.</strong> Threads are pooled and
 * reused; a value left behind is read by the next unrelated request, which is a
 * cross-tenant data leak rather than a tidiness problem.
 *
 * <pre>{@code
 * TenantContext.set(tenantId);
 * try {
 *     chain.doFilter(request, response);
 * } finally {
 *     TenantContext.clear();
 * }
 * }</pre>
 *
 * <p>The database is the real boundary - row-level security on {@code tenant_id}
 * ({@code W-07}). This class is how the application tells it which tenant is asking.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    /** Binds the tenant for this thread. Rejects null - an unbound tenant must fail loudly. */
    public static void set(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        CURRENT.set(tenantId);
    }

    /**
     * The current tenant.
     *
     * @throws IllegalStateException if none is bound. Deliberate: a query running with no
     *         tenant is either a bug or a cross-tenant read, and both should stop here.
     */
    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant bound to this thread. The request did not pass the tenant "
                    + "binding filter, or a background job did not set one.");
        }
        return id;
    }

    /** The current tenant if bound. For the few places where absence is legitimate. */
    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    /**
     * Binds the current thread's tenant to {@code conn} for the duration of the
     * <strong>open transaction</strong>, by executing
     * {@code SELECT set_config('app.current_tenant_id', ?, true)}. This is the
     * transaction-local equivalent of {@code SET LOCAL}, which cannot take a bind parameter.
     *
     * <p><strong>The connection must have auto-commit disabled.</strong> The binding is
     * transaction-local ({@code is_local = true}), so on an auto-commit connection every
     * statement is its own transaction and the value is discarded the instant this call
     * returns - the next query would run with no tenant bound and, under the row-level
     * security policies, quietly return zero rows. This method refuses that case rather
     * than appearing to succeed:
     *
     * <pre>{@code
     * conn.setAutoCommit(false);
     * TenantContext.setForConnection(conn);
     * // ... queries in this transaction see only this tenant's rows ...
     * conn.commit();
     * }</pre>
     *
     * <p>Once the transaction ends the variable does not revert to unset - it reverts to
     * the empty string. The RLS policies in {@code db/migration/} handle both states; see
     * {@code migration/README.md} section "Row-level security".
     *
     * @param conn an open JDBC connection with auto-commit disabled
     * @throws java.sql.SQLException if a database access error occurs
     * @throws IllegalArgumentException if {@code conn} is null
     * @throws IllegalStateException if no tenant is bound to the current thread, or if
     *         {@code conn} is in auto-commit mode
     */
    public static void setForConnection(java.sql.Connection conn) throws java.sql.SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("conn must not be null");
        }
        UUID tenantId = require();
        if (conn.getAutoCommit()) {
            throw new IllegalStateException("connection is in auto-commit mode: the tenant binding is "
                    + "transaction-local and would be discarded immediately, leaving every subsequent "
                    + "query to return zero rows under RLS. Call conn.setAutoCommit(false) first.");
        }
        try (var stmt = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            stmt.setString(1, tenantId.toString());
            stmt.execute();
        }
    }

    /** Clears the binding. Call from a {@code finally} block, always. */
    public static void clear() {
        CURRENT.remove();
    }
}
