package com.infinevo.core.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * W-14.2 — Unit tests for {@link ReportingLineService} (spec section 7).
 */
class ReportingLineServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ReportingLineRepository repository;
    private EmployeeRepository employeeRepository;
    private ReportingLineService service;

    private UUID empAId;
    private UUID empBId;
    private UUID empCId;
    private UUID empDId;

    private Employee empA;
    private Employee empB;
    private Employee empC;
    private Employee empD;

    @BeforeEach
    void setUp() {
        repository = mock(ReportingLineRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        service = new ReportingLineService(repository, employeeRepository);

        TenantContext.set(TENANT);

        empAId = UUID.randomUUID();
        empBId = UUID.randomUUID();
        empCId = UUID.randomUUID();
        empDId = UUID.randomUUID();

        empA = createEmployee(empAId, "EMP-A", "Alice");
        empB = createEmployee(empBId, "EMP-B", "Bob");
        empC = createEmployee(empCId, "EMP-C", "Charlie");
        empD = createEmployee(empDId, "EMP-D", "David");

        when(employeeRepository.findById(empAId)).thenReturn(Optional.of(empA));
        when(employeeRepository.findById(empBId)).thenReturn(Optional.of(empB));
        when(employeeRepository.findById(empCId)).thenReturn(Optional.of(empC));
        when(employeeRepository.findById(empDId)).thenReturn(Optional.of(empD));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Self-management is refused")
    void selfManagementRefused() {
        ReportingLineRequest req = new ReportingLineRequest(empAId, ReportingLineKind.PRIMARY, LocalDate.now(), null);

        assertThatThrownBy(() -> service.putReportingLine(empAId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-management refused");
    }

    @Test
    @DisplayName("A two-step cycle is refused (A -> B -> A)")
    void twoStepCycleRefused() {
        // B reports to A
        ReportingLine lineBtoA =
                new ReportingLine(TENANT, empB, empA, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), "test");
        when(repository.findActiveLines(eq(TENANT), eq(empBId), eq(ReportingLineKind.PRIMARY), any()))
                .thenReturn(List.of(lineBtoA));

        // Now trying to make A report to B
        ReportingLineRequest req =
                new ReportingLineRequest(empBId, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.putReportingLine(empAId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reporting line cycle detected");
    }

    @Test
    @DisplayName("A three-step cycle is refused (A -> B -> C -> A)")
    void threeStepCycleRefused() {
        // B reports to C, C reports to A
        ReportingLine lineBtoC =
                new ReportingLine(TENANT, empB, empC, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), "test");
        ReportingLine lineCtoA =
                new ReportingLine(TENANT, empC, empA, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), "test");

        when(repository.findActiveLines(eq(TENANT), eq(empBId), eq(ReportingLineKind.PRIMARY), any()))
                .thenReturn(List.of(lineBtoC));
        when(repository.findActiveLines(eq(TENANT), eq(empCId), eq(ReportingLineKind.PRIMARY), any()))
                .thenReturn(List.of(lineCtoA));

        // Trying to make A report to B
        ReportingLineRequest req =
                new ReportingLineRequest(empBId, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.putReportingLine(empAId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reporting line cycle detected");
    }

    @Test
    @DisplayName("A diamond reporting structure is allowed")
    void diamondAllowed() {
        // B reports to D, C reports to D. Now A reports to B
        ReportingLine lineBtoD =
                new ReportingLine(TENANT, empB, empD, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), "test");
        ReportingLine lineCtoD =
                new ReportingLine(TENANT, empC, empD, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), "test");

        when(repository.findActiveLines(eq(TENANT), eq(empBId), eq(ReportingLineKind.PRIMARY), any()))
                .thenReturn(List.of(lineBtoD));
        when(repository.findActiveLines(eq(TENANT), eq(empCId), eq(ReportingLineKind.PRIMARY), any()))
                .thenReturn(List.of(lineCtoD));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReportingLineRequest req =
                new ReportingLineRequest(empBId, ReportingLineKind.PRIMARY, LocalDate.of(2026, 1, 1), null);

        ReportingLineResponse res = service.putReportingLine(empAId, req);
        assertThat(res.employeeId()).isEqualTo(empAId);
        assertThat(res.managerId()).isEqualTo(empBId);
    }

    @Test
    @DisplayName("Single primary manager in force at a time (Decision 1)")
    void singlePrimaryManagerEnforced() {
        LocalDate Jan1 = LocalDate.of(2026, 1, 1);
        LocalDate Feb1 = LocalDate.of(2026, 2, 1);

        ReportingLine oldPrimary = new ReportingLine(TENANT, empA, empB, ReportingLineKind.PRIMARY, Jan1, "test");
        when(repository.findCurrentOpenLine(TENANT, empAId, ReportingLineKind.PRIMARY))
                .thenReturn(Optional.of(oldPrimary));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReportingLineRequest req = new ReportingLineRequest(empCId, ReportingLineKind.PRIMARY, Feb1, null);

        service.putReportingLine(empAId, req);

        assertThat(oldPrimary.getEffectiveTo()).isEqualTo(Feb1.minusDays(1));
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
