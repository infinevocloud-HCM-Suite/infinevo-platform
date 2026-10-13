package com.infinevo.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** D-79: actor columns (Keycloak subjects) resolve to display names in one read. */
class ActorNameServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID finance = UUID.randomUUID();

    private UserAccountRepository accounts;
    private ActorNameService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        accounts = mock(UserAccountRepository.class);
        service = new ActorNameService(accounts);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("subjects become first and last name, the email when no name is synced, in one read")
    void namesSubjectsInOneRead() {
        UserAccount named = account(officer, "Priya", "Nair", "priya@example.com");
        UserAccount unnamed = account(finance, null, " ", "finance@example.com");
        when(accounts.findByTenantIdAndKeycloakUserIdIn(eq(tenantId), anyCollection()))
                .thenReturn(List.of(named, unnamed));

        Map<String, String> names = service.displayNames(
                Arrays.asList(officer.toString(), finance.toString(), officer.toString(), "system", null));

        assertThat(names)
                .containsEntry(officer.toString(), "Priya Nair")
                .containsEntry(finance.toString(), "finance@example.com")
                .doesNotContainKey("system");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(accounts, times(1)).findByTenantIdAndKeycloakUserIdIn(eq(tenantId), ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(officer, finance);
    }

    @Test
    @DisplayName("no subject that parses means no read at all")
    void noSubjectsNoRead() {
        assertThat(service.displayNames(List.of("system"))).isEmpty();
        assertThat(service.displayNames(List.of())).isEmpty();
        verify(accounts, never()).findByTenantIdAndKeycloakUserIdIn(any(), anyCollection());
    }

    private static UserAccount account(UUID subject, String first, String last, String email) {
        UserAccount account = mock(UserAccount.class);
        when(account.getKeycloakUserId()).thenReturn(subject);
        when(account.getFirstName()).thenReturn(first);
        when(account.getLastName()).thenReturn(last);
        when(account.getEmail()).thenReturn(email);
        return account;
    }
}
