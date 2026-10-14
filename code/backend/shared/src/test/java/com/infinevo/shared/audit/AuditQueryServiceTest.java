package com.infinevo.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class AuditQueryServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEYCLOAK_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AuditLogRepository repository;
    private UserAccountRepository userAccounts;
    private AuditQueryService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        repository = mock(AuditLogRepository.class);
        userAccounts = mock(UserAccountRepository.class);
        service = new AuditQueryService(repository, userAccounts);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Enriches UUID actorLabel with user email from UserAccountRepository")
    void enrichesUuidActorLabelWithUserEmail() {
        AuditLog row = new AuditLog(
                TENANT,
                Instant.now(),
                KEYCLOAK_USER_ID,
                KEYCLOAK_USER_ID.toString(),
                "INSERT",
                "core",
                "employee",
                "emp-1",
                new String[] {"first_name"},
                null,
                null,
                null);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        UserAccount account = mock(UserAccount.class);
        when(account.getKeycloakUserId()).thenReturn(KEYCLOAK_USER_ID);
        when(account.getEmail()).thenReturn("krushna@infinevocloud.com");
        when(userAccounts.findByTenantIdAndKeycloakUserIdIn(eq(TENANT), eq(Set.of(KEYCLOAK_USER_ID))))
                .thenReturn(List.of(account));

        Page<AuditQueryService.AuditLogView> results =
                service.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).actorLabel()).isEqualTo("krushna@infinevocloud.com");
    }

    @Test
    @DisplayName("Preserves non-UUID actorLabel as is")
    void preservesExistingEmailActorLabel() {
        AuditLog row = new AuditLog(
                TENANT,
                Instant.now(),
                KEYCLOAK_USER_ID,
                "krushna@infinevocloud.com",
                "INSERT",
                "core",
                "employee",
                "emp-1",
                null,
                null,
                null,
                null);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));

        UserAccount account = mock(UserAccount.class);
        when(account.getKeycloakUserId()).thenReturn(KEYCLOAK_USER_ID);
        when(account.getEmail()).thenReturn("krushna@infinevocloud.com");
        when(userAccounts.findByTenantIdAndKeycloakUserIdIn(eq(TENANT), eq(Set.of(KEYCLOAK_USER_ID))))
                .thenReturn(List.of(account));

        Page<AuditQueryService.AuditLogView> results =
                service.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).actorLabel()).isEqualTo("krushna@infinevocloud.com");
    }

    @Test
    @DisplayName("Searching by actor email checks UserAccount to match Keycloak ID as well")
    void searchByActorEmailMatchesUserAccountKeycloakId() {
        UserAccount account = mock(UserAccount.class);
        when(account.getKeycloakUserId()).thenReturn(KEYCLOAK_USER_ID);
        when(account.getEmail()).thenReturn("sahmad@infinevocloud.com");
        when(userAccounts.findByTenantIdAndEmailIgnoreCase(TENANT, "sahmad@infinevocloud.com"))
                .thenReturn(Optional.of(account));

        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        Page<AuditQueryService.AuditLogView> results =
                service.search(null, null, "sahmad@infinevocloud.com", null, null, PageRequest.of(0, 20));

        assertThat(results).isNotNull();
    }
}
