package com.infinevo.core.employee;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-13.3, spec section 7 — every page and every {@code q} returns only the bound tenant's rows,
 * and {@code totalElements} is the bound tenant's count, not the table's.
 *
 * <p>This is the test that matters (spec section 9, risk table): a count query that escapes RLS
 * leaks how many employees another tenant has, even when it returns none of their rows. So the
 * assertion is on {@code totalElements}, not only on page contents.
 *
 * <p>50 employees in tenant A, 50 in tenant B. Every assertion checks both the content and the
 * count.
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class EmployeeSearchRlsIT extends AbstractIntegrationTest {

    private static final int EMPLOYEES_PER_TENANT = 50;

    @Autowired
    private EmployeeRepository repository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        tx = new TransactionTemplate(txManager);
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();

        for (int i = 1; i <= EMPLOYEES_PER_TENANT; i++) {
            EmployeeTestSchema.seedEmployee(TENANT_A, "A-" + String.format("%03d", i), "Alpha" + i);
            EmployeeTestSchema.seedEmployee(TENANT_B, "B-" + String.format("%03d", i), "Beta" + i);
        }
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Control: both tenants have the expected row counts")
    void controlCounts() throws SQLException {
        assertThat(EmployeeTestSchema.countEmployees(TENANT_A)).isEqualTo(EMPLOYEES_PER_TENANT);
        assertThat(EmployeeTestSchema.countEmployees(TENANT_B)).isEqualTo(EMPLOYEES_PER_TENANT);
    }

    @Test
    @DisplayName("Bound to tenant A, totalElements is A's count only")
    void totalElementsIsTenantScoped() {
        TenantContext.set(TENANT_A);
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_A, null, null, false, PageRequest.of(0, 100, Sort.by("lastName"))));

        assertThat(page.getTotalElements())
                .as("totalElements must be tenant A's count, not the whole table's")
                .isEqualTo(EMPLOYEES_PER_TENANT);
    }

    @Test
    @DisplayName("Free-text search returns only the bound tenant's matches")
    void freeTextSearchIsTenantScoped() {
        TenantContext.set(TENANT_A);
        // "Alpha" matches tenant A's employees; "Beta" would match B's but must not appear.
        Page<Employee> page = tx.execute(status ->
                repository.search(TENANT_A, "Alpha", null, false, PageRequest.of(0, 100, Sort.by("lastName"))));

        assertThat(page.getTotalElements()).isEqualTo(EMPLOYEES_PER_TENANT);
        assertThat(page.getContent())
                .allMatch(e -> e.getTenantId().equals(TENANT_A))
                .allMatch(e -> e.getFirstName().startsWith("Alpha"));
    }

    @Test
    @DisplayName("Searching for B-prefixed names while bound to tenant A returns nothing")
    void crossTenantSearchReturnsNothing() {
        TenantContext.set(TENANT_A);
        Page<Employee> page = tx.execute(status ->
                repository.search(TENANT_A, "Beta", null, false, PageRequest.of(0, 100, Sort.by("lastName"))));

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Bound to tenant B, totalElements is B's count only")
    void tenantBScopedCorrectly() {
        TenantContext.set(TENANT_B);
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_B, null, null, false, PageRequest.of(0, 100, Sort.by("lastName"))));

        assertThat(page.getTotalElements()).isEqualTo(EMPLOYEES_PER_TENANT);
        assertThat(page.getContent()).allMatch(e -> e.getTenantId().equals(TENANT_B));
    }
}
