package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * W-08 — unit tests for the lazy, transaction-local tenant binding (findings F-10 and F-11).
 *
 * <p>The binding must not happen at pool checkout, must go through
 * {@link TenantDatabaseInterceptor#bindTenantToConnection(Connection)} so the {@code D-57}
 * auto-commit guard applies, must happen exactly once per transaction, and must happen again
 * after the transaction ends.
 */
class TenantBindingDataSourceProxyTest {

    private final UUID tenantId = UUID.fromString("a1111111-1111-1111-1111-111111111111");

    private DataSource target;
    private Connection realConnection;
    private PreparedStatement setConfigStatement;
    private TenantBindingDataSourceProxy proxy;

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        target = mock(DataSource.class);
        realConnection = mock(Connection.class);
        setConfigStatement = mock(PreparedStatement.class);
        given(target.getConnection()).willReturn(realConnection);
        given(realConnection.getAutoCommit()).willReturn(false);
        given(realConnection.prepareStatement(contains("set_config"))).willReturn(setConfigStatement);
        given(realConnection.createStatement()).willReturn(mock(Statement.class));
        proxy = new TenantBindingDataSourceProxy(target);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("F-10: checking a connection out of the pool does not write the tenant anywhere")
    void getConnection_doesNotBindAtCheckout() throws Exception {
        TenantContext.set(tenantId);

        Connection wrapped = proxy.getConnection();

        assertThat(wrapped).isNotNull();
        verify(realConnection, never()).prepareStatement(contains("set_config"));
        verify(setConfigStatement, never()).execute();
    }

    @Test
    @DisplayName("The tenant is bound once per transaction, at the first statement, and again after commit()")
    void bindsOncePerTransaction_andRebindsAfterCommit() throws Exception {
        TenantContext.set(tenantId);
        Connection wrapped = proxy.getConnection();

        wrapped.createStatement();
        wrapped.createStatement();

        verify(realConnection, times(1)).prepareStatement(contains("set_config"));
        verify(setConfigStatement, times(1)).setString(1, tenantId.toString());
        verify(setConfigStatement, times(1)).execute();

        wrapped.commit();
        wrapped.createStatement();

        verify(realConnection, times(2)).prepareStatement(contains("set_config"));
        verify(setConfigStatement, times(2)).execute();
    }

    @Test
    @DisplayName("rollback(), close() and setAutoCommit(true) each end the binding, so the next statement rebinds")
    void transactionBoundaries_resetTheBinding() throws Exception {
        TenantContext.set(tenantId);
        Connection wrapped = proxy.getConnection();

        wrapped.createStatement();
        wrapped.rollback();
        wrapped.createStatement();
        wrapped.setAutoCommit(true);
        wrapped.createStatement();
        wrapped.close();
        wrapped.createStatement();

        verify(realConnection, times(4)).prepareStatement(contains("set_config"));
    }

    @Test
    @DisplayName("D-57: an auto-commit connection with a tenant bound raises IllegalStateException, not a silent"
            + " session write")
    void autoCommitConnection_throwsIllegalStateException() throws Exception {
        given(realConnection.getAutoCommit()).willReturn(true);
        TenantContext.set(tenantId);
        Connection wrapped = proxy.getConnection();

        assertThatThrownBy(wrapped::createStatement)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto-commit mode");

        verify(realConnection, never()).prepareStatement(contains("set_config"));
    }

    @Test
    @DisplayName("F-11: the production path goes through TenantDatabaseInterceptor.bindTenantToConnection")
    void statementBinding_delegatesToTenantDatabaseInterceptor() throws Exception {
        TenantContext.set(tenantId);
        Connection wrapped = proxy.getConnection();

        try (MockedStatic<TenantDatabaseInterceptor> interceptor = mockStatic(TenantDatabaseInterceptor.class)) {
            wrapped.createStatement();

            interceptor.verify(() -> TenantDatabaseInterceptor.bindTenantToConnection(realConnection));
            interceptor.verifyNoMoreInteractions();
        }
    }

    @Test
    @DisplayName("With no tenant bound the proxy touches nothing — the membership lookup runs before binding")
    void noTenantBound_doesNotBind() throws Exception {
        Connection wrapped = proxy.getConnection();

        wrapped.createStatement();

        verify(realConnection, never()).prepareStatement(contains("set_config"));
    }

    @Test
    @DisplayName("prepareStatement and prepareCall bind as well as createStatement")
    void prepareStatementAndPrepareCall_alsoBind() throws Exception {
        given(realConnection.prepareStatement("SELECT 1")).willReturn(mock(PreparedStatement.class));
        given(realConnection.prepareCall("{call noop()}")).willReturn(mock(CallableStatement.class));
        TenantContext.set(tenantId);

        Connection first = proxy.getConnection();
        first.prepareStatement("SELECT 1");
        verify(setConfigStatement, times(1)).execute();

        Connection second = proxy.getConnection();
        second.prepareCall("{call noop()}");
        verify(setConfigStatement, times(2)).execute();
    }

    @Test
    @DisplayName("Every other method is delegated untouched")
    void otherMethods_areDelegated() throws Exception {
        Connection wrapped = proxy.getConnection();

        wrapped.setAutoCommit(false);
        wrapped.isClosed();
        wrapped.getCatalog();

        verify(realConnection).setAutoCommit(false);
        verify(realConnection).isClosed();
        verify(realConnection).getCatalog();
    }

    @Test
    @DisplayName("unwrap, isWrapperFor, equals, hashCode and toString behave sanely on the proxy")
    void proxyIdentityMethods_behaveSanely() throws Exception {
        Connection wrapped = proxy.getConnection();

        assertThat(wrapped.unwrap(Connection.class)).isSameAs(realConnection);
        assertThat(wrapped.isWrapperFor(Connection.class)).isTrue();
        assertThat(wrapped).isEqualTo(wrapped);
        assertThat(wrapped).isNotEqualTo(realConnection);
        assertThat(wrapped.hashCode()).isEqualTo(System.identityHashCode(realConnection));
        assertThat(wrapped.toString()).contains("Tenant-binding proxy for");

        // An interface the target does not implement is delegated to the driver's own unwrap.
        given(realConnection.isWrapperFor(DataSource.class)).willReturn(false);
        assertThat(wrapped.isWrapperFor(DataSource.class)).isFalse();
        verify(realConnection).isWrapperFor(DataSource.class);
    }

    @Test
    @DisplayName("SQLException from the real connection propagates unwrapped, not as InvocationTargetException")
    void sqlExceptionFromTarget_propagatesUnwrapped() throws Exception {
        Connection wrapped = proxy.getConnection();
        given(realConnection.getCatalog()).willThrow(new SQLException("boom"));

        assertThatThrownBy(wrapped::getCatalog).isInstanceOf(SQLException.class).hasMessage("boom");
    }

    @Test
    @DisplayName("getConnection(username, password) is wrapped too")
    void getConnectionWithCredentials_isWrapped() throws Exception {
        given(target.getConnection("u", "p")).willReturn(realConnection);
        TenantContext.set(tenantId);

        Connection wrapped = proxy.getConnection("u", "p");
        verify(realConnection, never()).prepareStatement(contains("set_config"));

        wrapped.createStatement();
        verify(realConnection, times(1)).prepareStatement(contains("set_config"));
    }
}
