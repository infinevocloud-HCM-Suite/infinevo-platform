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
        assertThatThrownBy(() -> TenantContext.set(null))
                .isInstanceOf(IllegalArgumentException.class);
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
        var other = new Thread(() -> seenByOtherThread[0] = TenantContext.current().orElse(null));
        other.start();
        other.join();

        assertThat(seenByOtherThread[0]).isNull();
        assertThat(TenantContext.require()).isEqualTo(TENANT);
    }
}
