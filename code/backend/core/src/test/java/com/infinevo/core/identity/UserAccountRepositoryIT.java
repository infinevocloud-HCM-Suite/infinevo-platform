package com.infinevo.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.identity.entity.UserAccount;
import com.infinevo.core.identity.repository.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserAccountRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Persists user_account under tenant RLS context and retrieves by keycloak_sub")
    void savesAndRetrievesUserAccountWithRls() {
        UUID tenantId = UUID.randomUUID();

        TenantContext.set(tenantId);

        // Seed tenant in core.tenant first
        jdbcTemplate.update("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)", tenantId, "Identity Test Corp");

        String sub = UUID.randomUUID().toString();
        UserAccount user = new UserAccount(tenantId, sub, "user@identity.test", "Test", "User");
        userAccountRepository.saveAndFlush(user);

        Optional<UserAccount> found = userAccountRepository.findByKeycloakSub(sub);
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("user@identity.test");
        assertThat(found.get().getTenantId()).isEqualTo(tenantId);
    }
}
