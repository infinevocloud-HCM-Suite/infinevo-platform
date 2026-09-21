package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantDatabaseInterceptorTest {

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("bindTenantToConnection executes set_config when tenant is bound and autoCommit is false")
    void bindTenantToConnection_transactionalConnection_executesSetConfig() throws Exception {
        TenantContext.set(tenantId);
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);

        given(conn.getAutoCommit()).willReturn(false);
        given(conn.prepareStatement(contains("set_config"))).willReturn(stmt);

        TenantDatabaseInterceptor.bindTenantToConnection(conn);

        verify(stmt).setString(1, tenantId.toString());
        verify(stmt).execute();
    }

    @Test
    @DisplayName("setForConnection throws IllegalStateException when connection is in auto-commit mode (D-57)")
    void setForConnection_autoCommitMode_throwsIllegalStateException() throws Exception {
        TenantContext.set(tenantId);
        Connection conn = mock(Connection.class);

        given(conn.getAutoCommit()).willReturn(true);

        assertThatThrownBy(() -> TenantContext.setForConnection(conn))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto-commit mode");
    }

    @Test
    @DisplayName("bindTenantToConnection is no-op when no tenant is bound")
    void bindTenantToConnection_noTenantBound_noop() throws Exception {
        Connection conn = mock(Connection.class);

        TenantDatabaseInterceptor.bindTenantToConnection(conn);

        assertThat(TenantContext.isBound()).isFalse();
    }
}
