package com.infinevo.core.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.audit.CoreAuditTestApp;
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
 * W-40.2, spec section 7 — {@code AttendanceClockPrecedenceIT}.
 *
 * <p>Verifies the complete precedence lifecycle:
 * <ol>
 *   <li>Clock writes {@code PRESENT}.
 *   <li>Admin {@code PUT} sets {@code ABSENT}.
 *   <li>A second clock write leaves {@code ABSENT} and {@code source = ADMIN}.
 *   <li>After the admin {@code DELETE}, the clock write lands.
 * </ol>
 */
@SpringBootTest(classes = CoreAuditTestApp.class)
class AttendanceClockPrecedenceIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = AttendanceTestSchema.TENANT_A;

    @Autowired
    private AttendanceService attendanceService;

    private UUID employeeId;

    @BeforeAll
    static void applySchema() throws Exception {
        AttendanceTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        TenantContext.clear();
        AttendanceTestSchema.clearAttendance();
        AttendanceTestSchema.clearAudit();
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        EmployeeTestSchema.seedTenants();
        AttendanceTestSchema.clearAttendance();
        AttendanceTestSchema.clearAudit();
        EmployeeTestSchema.clearEmployees();

        employeeId = EmployeeTestSchema.seedEmployee(TENANT_A, "ATT-PREC-1", "Bob");
    }

    @AfterEach
    void unbind() throws Exception {
        TenantContext.clear();
        AttendanceTestSchema.clearAttendance();
        AttendanceTestSchema.clearAudit();
        EmployeeTestSchema.clearEmployees();
    }

    @Test
    @DisplayName("Clock write, admin overwrite, clock refusal against admin row, and clock write after delete")
    void clockAndAdminPrecedenceLifecycle() {
        TenantContext.set(TENANT_A);
        LocalDate date = LocalDate.now().minusDays(1);

        // 1. Clock writes PRESENT
        ClockDayResult r1 = attendanceService.recordFromClock(employeeId, date, AttendanceStatus.PRESENT);
        assertThat(r1.written()).isTrue();
        assertThat(r1.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(r1.source()).isEqualTo(AttendanceSource.CLOCK);

        List<AttendanceResponse> listed1 = attendanceService.list(date, date, employeeId);
        assertThat(listed1).hasSize(1);
        assertThat(listed1.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(listed1.get(0).source()).isEqualTo(AttendanceSource.CLOCK);

        // 2. Admin PUT sets ABSENT
        List<AttendanceResponse> adminUpsert = attendanceService.upsert(
                List.of(new AttendanceEntry(employeeId, date, AttendanceStatus.ABSENT, "Admin marked absent")));
        assertThat(adminUpsert).hasSize(1);
        assertThat(adminUpsert.get(0).status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(adminUpsert.get(0).source()).isEqualTo(AttendanceSource.ADMIN);

        List<AttendanceResponse> listed2 = attendanceService.list(date, date, employeeId);
        assertThat(listed2).hasSize(1);
        assertThat(listed2.get(0).status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(listed2.get(0).source()).isEqualTo(AttendanceSource.ADMIN);

        // 3. Second clock write leaves ABSENT and source = ADMIN (written = false)
        ClockDayResult r2 = attendanceService.recordFromClock(employeeId, date, AttendanceStatus.PRESENT);
        assertThat(r2.written()).isFalse();
        assertThat(r2.status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(r2.source()).isEqualTo(AttendanceSource.ADMIN);

        List<AttendanceResponse> listed3 = attendanceService.list(date, date, employeeId);
        assertThat(listed3).hasSize(1);
        assertThat(listed3.get(0).status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(listed3.get(0).source()).isEqualTo(AttendanceSource.ADMIN);

        // 4. After admin DELETE, the clock write lands
        attendanceService.delete(listed3.get(0).id());
        assertThat(attendanceService.list(date, date, employeeId)).isEmpty();

        ClockDayResult r3 = attendanceService.recordFromClock(employeeId, date, AttendanceStatus.PRESENT);
        assertThat(r3.written()).isTrue();
        assertThat(r3.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(r3.source()).isEqualTo(AttendanceSource.CLOCK);

        List<AttendanceResponse> listed4 = attendanceService.list(date, date, employeeId);
        assertThat(listed4).hasSize(1);
        assertThat(listed4.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(listed4.get(0).source()).isEqualTo(AttendanceSource.CLOCK);
    }
}
