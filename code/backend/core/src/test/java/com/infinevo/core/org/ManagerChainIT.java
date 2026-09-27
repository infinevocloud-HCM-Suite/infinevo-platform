package com.infinevo.core.org;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-14.2, spec section 7 — {@code ManagerChainIT}.
 *
 * <ul>
 *   <li>A five-deep chain resolves in order.
 *   <li>A manager change with effective dates gives different answers for different {@code asOf} values —
 *       asserted through {@code chainAbove(employee, asOf)} directly as {@code W-15.2} will call it.
 * </ul>
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class ManagerChainIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = EmployeeTestSchema.TENANT_A;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private ReportingLineService reportingLineService;

    private EmployeeResponse emp1;
    private EmployeeResponse emp2;
    private EmployeeResponse emp3;
    private EmployeeResponse emp4;
    private EmployeeResponse emp5;
    private EmployeeResponse emp6;

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

        emp1 = employeeService.create(createRequest("CHAIN-01", "Emp1", "Staff"));
        emp2 = employeeService.create(createRequest("CHAIN-02", "Emp2", "Manager1"));
        emp3 = employeeService.create(createRequest("CHAIN-03", "Emp3", "Manager2"));
        emp4 = employeeService.create(createRequest("CHAIN-04", "Emp4", "Director"));
        emp5 = employeeService.create(createRequest("CHAIN-05", "Emp5", "VP"));
        emp6 = employeeService.create(createRequest("CHAIN-06", "Emp6", "CEO"));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A 5-deep chain resolves in order from bottom to top")
    void fiveDeepChainResolvesInOrder() {
        LocalDate Jan1 = LocalDate.of(2026, 1, 1);

        // 1 reports to 2, 2 -> 3, 3 -> 4, 4 -> 5, 5 -> 6
        reportingLineService.putReportingLine(
                emp1.id(), new ReportingLineRequest(emp2.id(), ReportingLineKind.PRIMARY, Jan1, null));
        reportingLineService.putReportingLine(
                emp2.id(), new ReportingLineRequest(emp3.id(), ReportingLineKind.PRIMARY, Jan1, null));
        reportingLineService.putReportingLine(
                emp3.id(), new ReportingLineRequest(emp4.id(), ReportingLineKind.PRIMARY, Jan1, null));
        reportingLineService.putReportingLine(
                emp4.id(), new ReportingLineRequest(emp5.id(), ReportingLineKind.PRIMARY, Jan1, null));
        reportingLineService.putReportingLine(
                emp5.id(), new ReportingLineRequest(emp6.id(), ReportingLineKind.PRIMARY, Jan1, null));

        List<Employee> chain = reportingLineService.chainAbove(emp1.id(), Jan1);

        assertThat(chain).hasSize(5);
        assertThat(chain.get(0).getId()).isEqualTo(emp2.id());
        assertThat(chain.get(1).getId()).isEqualTo(emp3.id());
        assertThat(chain.get(2).getId()).isEqualTo(emp4.id());
        assertThat(chain.get(3).getId()).isEqualTo(emp5.id());
        assertThat(chain.get(4).getId()).isEqualTo(emp6.id());
    }

    @Test
    @DisplayName("Manager change with effective dates gives different answers for different asOf values")
    void managerChangeWithEffectiveDates() {
        LocalDate Jan1 = LocalDate.of(2026, 1, 1);
        LocalDate Jun1 = LocalDate.of(2026, 6, 1);

        // In January, emp1 reports to emp2
        reportingLineService.putReportingLine(
                emp1.id(), new ReportingLineRequest(emp2.id(), ReportingLineKind.PRIMARY, Jan1, null));

        // In June, emp1 is transferred to report to emp3
        reportingLineService.putReportingLine(
                emp1.id(), new ReportingLineRequest(emp3.id(), ReportingLineKind.PRIMARY, Jun1, null));

        // As of March (before Jun1), emp1's manager is emp2
        List<Employee> marchChain = reportingLineService.chainAbove(emp1.id(), LocalDate.of(2026, 3, 15));
        assertThat(marchChain).hasSize(1);
        assertThat(marchChain.get(0).getId()).isEqualTo(emp2.id());

        // As of July (after Jun1), emp1's manager is emp3
        List<Employee> julyChain = reportingLineService.chainAbove(emp1.id(), LocalDate.of(2026, 7, 1));
        assertThat(julyChain).hasSize(1);
        assertThat(julyChain.get(0).getId()).isEqualTo(emp3.id());
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
