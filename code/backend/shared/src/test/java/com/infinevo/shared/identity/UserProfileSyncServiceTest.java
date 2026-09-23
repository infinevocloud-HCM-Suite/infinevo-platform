package com.infinevo.shared.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.identity.UserProfileSyncService.SyncOutcome;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * W-10 — profile sync: insert on first sight, update on a changed claim, and <strong>no write at
 * all</strong> when nothing changed (spec section 7).
 *
 * <p>The third case is the one with teeth. An unconditional {@code save()} looks identical from the
 * outside and rewrites {@code updated_at} on every request the platform ever serves.
 */
class UserProfileSyncServiceTest {

    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER = UUID.fromString("b0000000-0000-0000-0000-000000000001");

    private UserAccountRepository repository;
    private UserProfileSyncService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        service = new UserProfileSyncService(repository);
    }

    @Test
    @DisplayName("First sight of a user inserts the profile from the token claims")
    void insertsOnFirstSight() {
        when(repository.findByTenantIdAndKeycloakUserId(TENANT, USER)).thenReturn(Optional.empty());

        SyncOutcome outcome = service.sync(TENANT, USER, "admin@globex-full.local", "Gita", "Globex");

        assertThat(outcome).isEqualTo(SyncOutcome.CREATED);
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).save(saved.capture());
        UserAccount account = saved.getValue();
        assertThat(account.getTenantId()).isEqualTo(TENANT);
        assertThat(account.getKeycloakUserId()).isEqualTo(USER);
        assertThat(account.getEmail()).isEqualTo("admin@globex-full.local");
        assertThat(account.getFirstName()).isEqualTo("Gita");
        assertThat(account.getLastName()).isEqualTo("Globex");
        assertThat(account.getStatus()).isEqualTo(UserAccount.STATUS_ACTIVE);
        assertThat(account.getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("A claim that changed in Keycloak is written through")
    void updatesWhenAClaimChanged() {
        UserAccount existing = existingAccount("admin@globex-full.local", "Gita", "Globex");
        Instant before = existing.getUpdatedAt();
        when(repository.findByTenantIdAndKeycloakUserId(TENANT, USER)).thenReturn(Optional.of(existing));

        SyncOutcome outcome = service.sync(TENANT, USER, "gita@globex-full.local", "Gita", "Globex-Smith");

        assertThat(outcome).isEqualTo(SyncOutcome.UPDATED);
        verify(repository).save(existing);
        assertThat(existing.getEmail()).isEqualTo("gita@globex-full.local");
        assertThat(existing.getLastName()).isEqualTo("Globex-Smith");
        assertThat(existing.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(existing.getLastSyncedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("Claims that match the row are not written back — no save, and updated_at untouched")
    void writesNothingWhenNothingChanged() {
        UserAccount existing = existingAccount("admin@globex-full.local", "Gita", "Globex");
        Instant updatedAtBefore = existing.getUpdatedAt();
        Instant syncedAtBefore = existing.getLastSyncedAt();
        when(repository.findByTenantIdAndKeycloakUserId(TENANT, USER)).thenReturn(Optional.of(existing));

        SyncOutcome outcome = service.sync(TENANT, USER, "admin@globex-full.local", "Gita", "Globex");

        assertThat(outcome).isEqualTo(SyncOutcome.UNCHANGED);
        verify(repository, never()).save(any());
        // Nothing mutated either, so Hibernate's dirty check has nothing to flush.
        assertThat(existing.getUpdatedAt()).isEqualTo(updatedAtBefore);
        assertThat(existing.getLastSyncedAt()).isEqualTo(syncedAtBefore);
    }

    @Test
    @DisplayName("A null name on both sides is not a change")
    void nullClaimsAreComparedNotAssumedDifferent() {
        UserAccount existing = existingAccount("admin@globex-full.local", null, null);
        when(repository.findByTenantIdAndKeycloakUserId(TENANT, USER)).thenReturn(Optional.of(existing));

        assertThat(service.sync(TENANT, USER, "admin@globex-full.local", null, null))
                .isEqualTo(SyncOutcome.UNCHANGED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("A sync without a tenant is refused rather than written to whichever tenant is bound")
    void refusesWithoutATenant() {
        assertThatThrownBy(() -> service.sync(null, USER, "a@b.local", "A", "B"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("find() reads the one row for this user in this tenant")
    void findDelegatesToTheTenantScopedLookup() {
        UserAccount existing = existingAccount("admin@globex-full.local", "Gita", "Globex");
        when(repository.findByTenantIdAndKeycloakUserId(TENANT, USER)).thenReturn(Optional.of(existing));

        assertThat(service.find(TENANT, USER)).containsSame(existing);
    }

    private static UserAccount existingAccount(String email, String firstName, String lastName) {
        return new UserAccount(
                TENANT, USER, email, firstName, lastName, Instant.now().minusSeconds(3600));
    }
}
