package com.infinevo.shared.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Datasource proxy that automatically binds {@link TenantContext} to physical JDBC connections
 * whenever a connection is retrieved for database transactions or queries during a request.
 */
public class TenantBindingDataSourceProxy extends DelegatingDataSource {

    public TenantBindingDataSourceProxy(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        bindTenantIfBound(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = super.getConnection(username, password);
        bindTenantIfBound(conn);
        return conn;
    }

    private void bindTenantIfBound(Connection conn) throws SQLException {
        if (TenantContext.isBound()) {
            java.util.UUID tenantId = TenantContext.require();
            boolean isLocal = !conn.getAutoCommit();
            try (var stmt = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, ?)")) {
                stmt.setString(1, tenantId.toString());
                stmt.setBoolean(2, isLocal);
                stmt.execute();
            }
        }
    }
}
