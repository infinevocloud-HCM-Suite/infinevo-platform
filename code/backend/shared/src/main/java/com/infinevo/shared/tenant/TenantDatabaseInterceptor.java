package com.infinevo.shared.tenant;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interceptor and utility for binding {@link TenantContext} to physical JDBC connections during Spring transactions.
 */
public class TenantDatabaseInterceptor {

    /**
     * Binds the current thread's tenant context to the given connection.
     *
     * @param conn physical open JDBC connection
     * @throws SQLException if a database access error occurs
     * @throws IllegalStateException if conn is in auto-commit mode or no tenant is bound to current thread
     */
    public static void bindTenantToConnection(Connection conn) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("Connection must not be null");
        }
        if (!TenantContext.isBound()) {
            return;
        }
        TenantContext.setForConnection(conn);
    }
}
