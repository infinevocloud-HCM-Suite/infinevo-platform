package com.infinevo.shared.impersonation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActingAsTest {

    private final UUID staffId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void cleanActingAs() {
        ActingAs.clear();
    }

    @Test
    @DisplayName("binds and retrieves an active impersonation context for a target user")
    void shouldBindAndRetrieveTargetUserImpersonation() {
        assertThat(ActingAs.isActing()).isFalse();
        assertThat(ActingAs.current()).isEmpty();
        assertThat(ActingAs.get()).isEmpty();

        ActingAs.set(staffId, targetUserId, sessionId, "user@customer.local", Set.of("core.leave.apply"));

        assertThat(ActingAs.isActing()).isTrue();
        assertThat(ActingAs.current()).isPresent();

        ActingAs.Impersonation ctx = ActingAs.require();
        assertThat(ctx.platformUserId()).isEqualTo(staffId);
        assertThat(ctx.targetUserAccountId()).isEqualTo(targetUserId);
        assertThat(ctx.sessionId()).isEqualTo(sessionId);
        assertThat(ctx.targetEmail()).isEqualTo("user@customer.local");
        assertThat(ctx.actionCodes()).containsExactly("core.leave.apply");
        assertThat(ctx.isBootstrap()).isFalse();

        ActingAs.clear();
        assertThat(ActingAs.isActing()).isFalse();
        assertThat(ActingAs.current()).isEmpty();
    }

    @Test
    @DisplayName("binds and retrieves a bootstrap impersonation session with null target user")
    void shouldBindAndRetrieveBootstrapImpersonation() {
        ActingAs.set(staffId, null, sessionId, null, Set.of("core.user.invite", "core.role.assign"));

        assertThat(ActingAs.isActing()).isTrue();
        ActingAs.Impersonation ctx = ActingAs.require();
        assertThat(ctx.platformUserId()).isEqualTo(staffId);
        assertThat(ctx.targetUserAccountId()).isNull();
        assertThat(ctx.sessionId()).isEqualTo(sessionId);
        assertThat(ctx.targetEmail()).isNull();
        assertThat(ctx.actionCodes()).containsExactlyInAnyOrder("core.user.invite", "core.role.assign");
        assertThat(ctx.isBootstrap()).isTrue();
    }

    @Test
    @DisplayName("require() throws IllegalStateException when no impersonation is active")
    void requireThrowsWhenUnbound() {
        assertThatThrownBy(ActingAs::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No impersonation session active on this thread");
    }

    @Test
    @DisplayName("defensively copies actionCodes set into an unmodifiable set")
    void defensivelyCopiesActionCodes() {
        Set<String> mutableCodes = new HashSet<>();
        mutableCodes.add("core.employee.read");

        ActingAs.set(staffId, targetUserId, sessionId, "test@test.local", mutableCodes);
        mutableCodes.add("core.employee.update");

        ActingAs.Impersonation ctx = ActingAs.require();
        assertThat(ctx.actionCodes()).containsExactly("core.employee.read");
        assertThatThrownBy(() -> ctx.actionCodes().add("forbidden")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("validates that platformUserId and sessionId must not be null")
    void validatesRequiredFields() {
        assertThatThrownBy(() -> new ActingAs.Impersonation(null, targetUserId, sessionId, "email", Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("platformUserId must not be null");

        assertThatThrownBy(() -> new ActingAs.Impersonation(staffId, targetUserId, null, "email", Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionId must not be null");

        assertThatThrownBy(() -> ActingAs.set(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("impersonation must not be null");
    }
}
