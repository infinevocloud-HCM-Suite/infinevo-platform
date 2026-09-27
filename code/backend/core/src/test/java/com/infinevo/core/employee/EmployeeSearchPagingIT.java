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
 * W-13.3, spec section 7 — paging and count verification.
 *
 * <p>Seeds 7 employees in tenant A and 3 in tenant B. Asserts:
 * <ul>
 *   <li>Total count is the tenant's count, not the table's.
 *   <li>Last page is not short by an off-by-one.
 *   <li>Page beyond the last is empty.
 * </ul>
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class EmployeeSearchPagingIT extends AbstractIntegrationTest {

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

        for (int i = 1; i <= 7; i++) {
            EmployeeTestSchema.seedEmployee(TENANT_A, "PA-" + String.format("%03d", i), "PageAlpha" + i);
        }
        for (int i = 1; i <= 3; i++) {
            EmployeeTestSchema.seedEmployee(TENANT_B, "PB-" + String.format("%03d", i), "PageBeta" + i);
        }
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Total count is the tenant's count, not the table's total of 10")
    void totalCountIsTenantScoped() {
        TenantContext.set(TENANT_A);
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_A, null, null, false, PageRequest.of(0, 25, Sort.by("lastName"))));

        assertThat(page.getTotalElements())
                .as("Tenant A has 7 employees, not 10")
                .isEqualTo(7);
    }

    @Test
    @DisplayName("First page of size 3 returns 3 rows and reports the correct total")
    void firstPageOfSize3() {
        TenantContext.set(TENANT_A);
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_A, null, null, false, PageRequest.of(0, 3, Sort.by("lastName"))));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(3); // ceil(7/3) = 3
    }

    @Test
    @DisplayName("Last page has exactly the remaining rows — no off-by-one")
    void lastPageHasRemainder() {
        TenantContext.set(TENANT_A);
        // 7 rows, page size 3 → pages 0,1,2; page 2 has 1 row
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_A, null, null, false, PageRequest.of(2, 3, Sort.by("lastName"))));

        assertThat(page.getContent())
                .as("last page should have exactly 1 row (7 mod 3)")
                .hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(7);
    }

    @Test
    @DisplayName("Page beyond the last is empty")
    void pageAfterLastIsEmpty() {
        TenantContext.set(TENANT_A);
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_A, null, null, false, PageRequest.of(10, 3, Sort.by("lastName"))));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(7);
    }

    @Test
    @DisplayName("Tenant B sees only its own 3 rows")
    void tenantBCount() {
        TenantContext.set(TENANT_B);
        Page<Employee> page = tx.execute(
                status -> repository.search(TENANT_B, null, null, false, PageRequest.of(0, 25, Sort.by("lastName"))));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(3);
    }
}
