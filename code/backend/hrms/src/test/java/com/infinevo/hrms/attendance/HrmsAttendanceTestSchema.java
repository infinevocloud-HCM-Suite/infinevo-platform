package com.infinevo.hrms.attendance;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Database schema and test fixture setup for HRMS attendance preferences integration tests (W-40.1).
 *
 * <p>Delegates database initialization and fixtures to {@link HrmsProjectTestSchema}.
 */
public final class HrmsAttendanceTestSchema {

    private HrmsAttendanceTestSchema() {}

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            new HrmsProjectTestSchema.Initializer().initialize(ctx);
        }
    }

    public static Connection migrationConnection() throws SQLException {
        return HrmsProjectTestSchema.migrationConnection();
    }

    public static Connection appConnection() throws SQLException {
        return HrmsProjectTestSchema.appConnection();
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        HrmsProjectTestSchema.bindTenant(conn, tenantId);
    }

    public static UUID insertTenant(String name) throws SQLException {
        return HrmsProjectTestSchema.insertTenant(name);
    }

    public static void insertMember(UUID tenantId, UUID userAccountId, String roleName) throws SQLException {
        HrmsProjectTestSchema.insertMember(tenantId, userAccountId, roleName);
    }

    public static int countAuditRows(UUID tenantId, String entityTable) throws SQLException {
        return HrmsProjectTestSchema.countAuditRows(tenantId, entityTable);
    }
}
