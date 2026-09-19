package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("binds and reads back the tenant")
    void bindsAndReads() {
        TenantContext.set(TENANT);

        assertThat(TenantContext.isBound()).isTrue();
        assertThat(TenantContext.require()).isEqualTo(TENANT);
        assertThat(TenantContext.current()).contains(TENANT);
    }

    @Test
    @DisplayName("require() fails loudly when nothing is bound")
    void requireFailsWhenUnbound() {
        assertThatThrownBy(TenantContext::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
    }

    @Test
    @DisplayName("current() is empty when nothing is bound")
    void currentIsEmptyWhenUnbound() {
        assertThat(TenantContext.current()).isEmpty();
        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("rejects a null tenant rather than binding nothing")
    void rejectsNull() {
        assertThatThrownBy(() -> TenantContext.set(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("clear() removes the binding")
    void clearRemovesBinding() {
        TenantContext.set(TENANT);
        TenantContext.clear();

        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("clearing in a finally block survives an exception - the leak case")
    void clearedAfterException() {
        try {
            TenantContext.set(TENANT);
            throw new RuntimeException("boom");
        } catch (RuntimeException expected) {
            // the caller's catch
        } finally {
            TenantContext.clear();
        }

        assertThat(TenantContext.isBound()).isFalse();
    }

    @Test
    @DisplayName("a binding on one thread is invisible to another - pooled threads must not leak")
    void isolatedBetweenThreads() throws Exception {
        TenantContext.set(TENANT);

        var seenByOtherThread = new UUID[1];
        var other =
                new Thread(() -> seenByOtherThread[0] = TenantContext.current().orElse(null));
        other.start();
        other.join();

        assertThat(seenByOtherThread[0]).isNull();
        assertThat(TenantContext.require()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("setForConnection rejects null connection")
    void setForConnectionRejectsNull() {
        TenantContext.set(TENANT);
        assertThatThrownBy(() -> TenantContext.setForConnection(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setForConnection fails when tenant is unbound")
    void setForConnectionFailsWhenUnbound() {
        var conn = org.mockito.Mockito.mock(java.sql.Connection.class);
        assertThatThrownBy(() -> TenantContext.setForConnection(conn)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("setForConnection executes set_config on connection")
    void setForConnectionExecutesQuery() throws Exception {
        TenantContext.set(TENANT);
        var conn = org.mockito.Mockito.mock(java.sql.Connection.class);
        var stmt = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        org.mockito.Mockito.when(conn.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(stmt);

        TenantContext.setForConnection(conn);

        org.mockito.Mockito.verify(conn).prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)");
        org.mockito.Mockito.verify(stmt).setString(1, TENANT.toString());
        org.mockito.Mockito.verify(stmt).execute();
    }

    /**
     * Review F-1. The binding is transaction-local, so on an auto-commit connection it is
     * discarded the instant the call returns and every later query silently returns zero
     * rows under RLS. Refusing is the only honest outcome; before the fix this bound
     * nothing and reported success.
     */
    @Test
    @DisplayName("setForConnection refuses an auto-commit connection instead of binding nothing")
    void setForConnectionRefusesAutoCommit() throws Exception {
        TenantContext.set(TENANT);
        var conn = org.mockito.Mockito.mock(java.sql.Connection.class);
        org.mockito.Mockito.when(conn.getAutoCommit()).thenReturn(true);

        assertThatThrownBy(() -> TenantContext.setForConnection(conn))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auto-commit");

        org.mockito.Mockito.verify(conn, org.mockito.Mockito.never())
                .prepareStatement(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("setForConnection binds when auto-commit is disabled")
    void setForConnectionBindsWhenTransactional() throws Exception {
        TenantContext.set(TENANT);
        var conn = org.mockito.Mockito.mock(java.sql.Connection.class);
        var stmt = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        org.mockito.Mockito.when(conn.getAutoCommit()).thenReturn(false);
        org.mockito.Mockito.when(conn.prepareStatement(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(stmt);

        TenantContext.setForConnection(conn);

        org.mockito.Mockito.verify(stmt).execute();
    }
}
