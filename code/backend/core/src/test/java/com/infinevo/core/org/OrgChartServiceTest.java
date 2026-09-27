package com.infinevo.core.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-14.2 — Unit tests for {@link OrgChartService} (spec section 7).
 */
class OrgChartServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ReportingLineRepository reportingLineRepository;
    private EmployeeRepository employeeRepository;
    private OrgChartService service;

    private UUID rootId;
    private UUID childId;
    private Employee root;
    private Employee child;

    @BeforeEach
    void setUp() {
        reportingLineRepository = mock(ReportingLineRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        service = new OrgChartService(reportingLineRepository, employeeRepository);

        TenantContext.set(TENANT);

        rootId = UUID.randomUUID();
        childId = UUID.randomUUID();

        root = createEmployee(rootId, "EMP-ROOT", "Root");
        child = createEmployee(childId, "EMP-CHILD", "Child");

        when(employeeRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(employeeRepository.findById(childId)).thenReturn(Optional.of(child));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Employee with no reports returns an empty directReports list, not an error")
    void noReportsReturnsEmptyList() {
        when(reportingLineRepository.findAllActivePrimaryLines(eq(TENANT), any()))
                .thenReturn(List.of());

        OrgChartNodeResponse chart = service.getOrgChart(rootId, 3);

        assertThat(chart.employeeId()).isEqualTo(rootId);
        assertThat(chart.directReports()).isEmpty();
    }

    @Test
    @DisplayName("Subtree depth is honoured")
    void subtreeDepthHonoured() {
        ReportingLine lineChildToRoot =
                new ReportingLine(TENANT, child, root, ReportingLineKind.PRIMARY, LocalDate.now(), "test");
        when(reportingLineRepository.findAllActivePrimaryLines(eq(TENANT), any()))
                .thenReturn(List.of(lineChildToRoot));

        // Request depth = 1 (returns root only without expanding direct reports)
        OrgChartNodeResponse chartDepth1 = service.getOrgChart(rootId, 1);
        assertThat(chartDepth1.employeeId()).isEqualTo(rootId);
        assertThat(chartDepth1.directReports()).isEmpty();

        // Request depth = 2 (returns root with child in direct reports)
        OrgChartNodeResponse chartDepth2 = service.getOrgChart(rootId, 2);
        assertThat(chartDepth2.employeeId()).isEqualTo(rootId);
        assertThat(chartDepth2.directReports()).hasSize(1);
        assertThat(chartDepth2.directReports().get(0).employeeId()).isEqualTo(childId);
    }

    private static Employee createEmployee(UUID id, String number, String firstName) {
        Employee e = new Employee(TENANT, "test");
        ReflectionTestUtils.setField(e, "id", id);
        ReflectionTestUtils.setField(e, "employeeNumber", number);
        ReflectionTestUtils.setField(e, "firstName", firstName);
        ReflectionTestUtils.setField(e, "lastName", "Test");
        ReflectionTestUtils.setField(e, "status", EmploymentStatus.ACTIVE);
        ReflectionTestUtils.setField(e, "deleted", false);
        return e;
    }
}
