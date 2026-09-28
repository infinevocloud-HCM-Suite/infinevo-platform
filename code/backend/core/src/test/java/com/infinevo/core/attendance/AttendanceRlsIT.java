package com.infinevo.core.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-39.1, spec section 7 — {@code AttendanceRlsIT}.
 *
 * <p>Asserts that Tenant A's attendance rows are invisible to Tenant B through the service,
 * repository, and database Row-Level Security policies.
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class AttendanceRlsIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = AttendanceTestSchema.TENANT_A;
    private static final UUID TENANT_B = AttendanceTestSchema.TENANT_B;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate txTemplate;

    private UUID empAId;
    private UUID empBId;

    @BeforeAll
    static void applySchema() throws Exception {
        AttendanceTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        TenantContext.clear();
        AttendanceTestSchema.clearAttendance();
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        EmployeeTestSchema.seedTenants();
        AttendanceTestSchema.clearAttendance();
        EmployeeTestSchema.clearEmployees();

        empAId = EmployeeTestSchema.seedEmployee(TENANT_A, "ATT-RLS-A", "Alice");
        empBId = EmployeeTestSchema.seedEmployee(TENANT_B, "ATT-RLS-B", "Bob");
    }

    @AfterEach
    void unbind() throws Exception {
        TenantContext.clear();
        AttendanceTestSchema.clearAttendance();
        EmployeeTestSchema.clearEmployees();
    }

    @Test
    @DisplayName("Tenant A rows are completely isolated and invisible to Tenant B")
    void attendanceIsolatedBetweenTenants() throws Exception {
        LocalDate date = LocalDate.now().minusDays(1);

        // Record attendance for Tenant A employee
        TenantContext.set(TENANT_A);
        List<AttendanceResponse> created = attendanceService.upsert(
                List.of(new AttendanceEntry(empAId, date, AttendanceStatus.PRESENT, "Present at Acme")));
        assertThat(created).hasSize(1);
        UUID attAId = created.get(0).id();

        // Tenant A can see it via service and repo
        List<AttendanceResponse> listA = attendanceService.list(date.minusDays(5), date.plusDays(1), null);
        assertThat(listA).hasSize(1);
        List<Attendance> repoA = txTemplate.execute(status ->
                attendanceRepository.findByTenantIdAndDateRange(TENANT_A, date.minusDays(5), date.plusDays(1)));
        assertThat(repoA).hasSize(1);

        // Raw database policy level check for app_user
        assertThat(AttendanceTestSchema.visibleRowCount("attendance", TENANT_A)).isEqualTo(1);
        assertThat(AttendanceTestSchema.visibleRowCount("attendance", TENANT_B)).isEqualTo(0);

        // Switch to Tenant B
        TenantContext.set(TENANT_B);

        // Tenant B sees nothing via service and repo
        List<AttendanceResponse> listB = attendanceService.list(date.minusDays(5), date.plusDays(1), null);
        assertThat(listB).isEmpty();
        List<Attendance> repoB = txTemplate.execute(status ->
                attendanceRepository.findByTenantIdAndDateRange(TENANT_B, date.minusDays(5), date.plusDays(1)));
        assertThat(repoB).isEmpty();

        // Tenant B cannot delete Tenant A's record
        assertThatThrownBy(() -> attendanceService.delete(attAId))
                .isInstanceOf(AttendanceService.NotFoundException.class);

        // Tenant B cannot record attendance for Tenant A's employee
        assertThatThrownBy(() -> attendanceService.upsert(
                        List.of(new AttendanceEntry(empAId, date, AttendanceStatus.PRESENT, "Tenant cross write"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee not found in tenant");
    }
}
