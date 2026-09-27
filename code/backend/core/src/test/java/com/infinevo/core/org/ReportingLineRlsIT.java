package com.infinevo.core.org;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.core.employee.EmploymentStatus;
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
 * W-14.2, spec section 7 — {@code ReportingLineRlsIT}.
 *
 * <p>Assures that a manager in another tenant is refused when setting up reporting lines.
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class ReportingLineRlsIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = EmployeeTestSchema.TENANT_A;
    private static final UUID TENANT_B = EmployeeTestSchema.TENANT_B;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private ReportingLineService reportingLineService;

    private EmployeeResponse empA;
    private EmployeeResponse empB;

    @BeforeAll
    static void applySchema() throws Exception {
        EmployeeTestSchema.apply();
        OrgTestSchema.apply();
    }

    @BeforeEach
    void seed() throws Exception {
        EmployeeTestSchema.seedTenants();
        EmployeeTestSchema.clearEmployees();

        TenantContext.set(TENANT_A);
        empA = employeeService.create(createRequest("RLS-A01", "Alice", "Acme"));

        TenantContext.set(TENANT_B);
        empB = employeeService.create(createRequest("RLS-B01", "Bob", "Globex"));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A manager in another tenant is refused as app_user")
    void managerInAnotherTenantRefused() {
        TenantContext.set(TENANT_A);

        // Attempting to assign empB (from Tenant B) as manager for empA (in Tenant A)
        ReportingLineRequest req =
                new ReportingLineRequest(empB.id(), ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> reportingLineService.putReportingLine(empA.id(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Manager not found in tenant");
    }

    @Test
    @DisplayName("Raw app_user connection isolates reporting lines by tenant at DB policy level")
    void rawReportingLineRlsEnforced() throws Exception {
        TenantContext.set(TENANT_A);
        EmployeeResponse empA2 = employeeService.create(createRequest("RLS-A02", "Aaron", "Acme"));
        reportingLineService.putReportingLine(
                empA.id(),
                new ReportingLineRequest(empA2.id(), ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), null));

        org.assertj.core.api.Assertions.assertThat(OrgTestSchema.visibleRowCount("reporting_line", TENANT_A))
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(OrgTestSchema.visibleRowCount("reporting_line", TENANT_B))
                .isEqualTo(0);
    }

    private static EmployeeRequest createRequest(String number, String firstName, String lastName) {
        return new EmployeeRequest(
                number,
                firstName,
                null,
                lastName,
                "Other",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                number.toLowerCase() + "@test.com",
                null,
                true,
                null,
                null,
                null);
    }
}
