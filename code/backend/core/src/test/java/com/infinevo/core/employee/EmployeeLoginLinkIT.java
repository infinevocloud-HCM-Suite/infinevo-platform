package com.infinevo.core.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-13.4 integration test for user account linking on employees.
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class EmployeeLoginLinkIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private UserProfileSyncService userProfileSyncService;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Link login to employee in bound tenant succeeds and unlinking clears it")
    void linkLoginAndUnlink() {
        TenantContext.set(TENANT_A);
        UUID keycloakUserId = UUID.randomUUID();
        userProfileSyncService.sync(TENANT_A, keycloakUserId, "user@tenant-a.com", "Test", "User");
        UserAccount account =
                userProfileSyncService.find(TENANT_A, keycloakUserId).orElseThrow();

        EmployeeResponse emp = employeeService.create(new EmployeeRequest(
                "EMP-LINK-1",
                "Emp",
                null,
                "One",
                "M",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp1@example.com",
                null,
                true,
                null,
                null,
                null));

        assertThat(emp.userAccountId()).isNull();

        EmployeeResponse linked = employeeService.linkLogin(emp.id(), account.getId());
        assertThat(linked.userAccountId()).isEqualTo(account.getId());

        EmployeeResponse unlinked = employeeService.linkLogin(emp.id(), null);
        assertThat(unlinked.userAccountId()).isNull();
    }

    @Test
    @DisplayName("Linking a user account from tenant B to an employee in tenant A is refused as 400 validation error")
    void userAccountFromOtherTenantRefused() {
        TenantContext.set(TENANT_B);
        UUID keycloakUserIdB = UUID.randomUUID();
        userProfileSyncService.sync(TENANT_B, keycloakUserIdB, "user@tenant-b.com", "Test", "User");
        UserAccount accountB =
                userProfileSyncService.find(TENANT_B, keycloakUserIdB).orElseThrow();

        TenantContext.set(TENANT_A);
        EmployeeResponse empA = employeeService.create(new EmployeeRequest(
                "EMP-LINK-2",
                "Emp",
                null,
                "Two",
                "F",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp2@example.com",
                null,
                true,
                null,
                null,
                null));

        assertThatThrownBy(() -> employeeService.linkLogin(empA.id(), accountB.getId()))
                .isInstanceOf(EmployeeService.ValidationException.class);
    }

    @Test
    @DisplayName(
            "Linking a user account already linked to another employee in the same tenant is refused as 409 conflict")
    void duplicateLinkInSameTenantRefused() {
        TenantContext.set(TENANT_A);
        UUID keycloakUserId = UUID.randomUUID();
        userProfileSyncService.sync(TENANT_A, keycloakUserId, "dup@tenant-a.com", "Dup", "User");
        UserAccount account =
                userProfileSyncService.find(TENANT_A, keycloakUserId).orElseThrow();

        EmployeeResponse emp1 = employeeService.create(new EmployeeRequest(
                "EMP-LINK-3",
                "Emp",
                null,
                "Three",
                "M",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp3@example.com",
                null,
                true,
                null,
                null,
                null));
        EmployeeResponse emp2 = employeeService.create(new EmployeeRequest(
                "EMP-LINK-4",
                "Emp",
                null,
                "Four",
                "F",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp4@example.com",
                null,
                true,
                null,
                null,
                null));

        employeeService.linkLogin(emp1.id(), account.getId());

        assertThatThrownBy(() -> employeeService.linkLogin(emp2.id(), account.getId()))
                .isInstanceOf(EmployeeService.DuplicateUserAccountLinkException.class);
    }
}
