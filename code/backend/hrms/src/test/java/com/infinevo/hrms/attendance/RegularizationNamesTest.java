package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.attendance.AttendanceQuery;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * D-79: a regularization reply names who decided it ({@code decidedByName}) beside {@code decidedBy}, and
 * HR's list reads every requester and decider with one {@code displayNames} call.
 */
class RegularizationNamesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ASHA = UUID.randomUUID();
    private static final UUID RAVI = UUID.randomUUID();
    private static final UUID MANAGER = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private AttendanceRegularizationRepository regularizations;
    private EmployeeService employeeService;
    private RegularizationServiceImpl service;

    @BeforeEach
    void setUp() {
        regularizations = mock(AttendanceRegularizationRepository.class);
        employeeService = mock(EmployeeService.class);
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds("core.attendance.read")).thenReturn(true);
        service = new RegularizationServiceImpl(
                regularizations,
                mock(ClockSessionRepository.class),
                mock(AttendancePreferenceService.class),
                mock(AttendanceQuery.class),
                mock(ApprovalService.class),
                employeeService,
                mock(TenantClock.class),
                permissions,
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC));
        when(employeeService.displayNames(anyCollection()))
                .thenReturn(Map.of(ASHA, "Asha Rao", RAVI, "Ravi Kumar", MANAGER, "Vikram Shah"));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("HR's list names requester and decider with one read for the whole list")
    void listNamesRequesterAndDecider() {
        AttendanceRegularization decided = row(ASHA, MANAGER);
        AttendanceRegularization pending = row(RAVI, null);
        when(regularizations.findByTenantIdAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                        TENANT, FROM, TO))
                .thenReturn(List.of(decided, pending));

        List<RegularizationResponse> rows = service.all(FROM, TO, null, null);

        assertThat(rows).extracting(RegularizationResponse::employeeName).containsExactly("Asha Rao", "Ravi Kumar");
        assertThat(rows.get(0).decidedBy()).isEqualTo(MANAGER);
        assertThat(rows.get(0).decidedByName()).isEqualTo("Vikram Shah");
        assertThat(rows.get(1).decidedBy()).isNull();
        assertThat(rows.get(1).decidedByName()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(employeeService, times(1)).displayNames(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(ASHA, RAVI, MANAGER);
    }

    @Test
    @DisplayName("one request read by id names its requester and decider")
    void detailNamesDecider() {
        AttendanceRegularization decided = row(ASHA, MANAGER);
        when(regularizations.findByTenantIdAndId(TENANT, decided.getId())).thenReturn(Optional.of(decided));

        RegularizationResponse response = service.get(decided.getId());

        assertThat(response.employeeName()).isEqualTo("Asha Rao");
        assertThat(response.decidedByName()).isEqualTo("Vikram Shah");
    }

    private static AttendanceRegularization row(UUID employeeId, UUID decidedBy) {
        AttendanceRegularization row = new AttendanceRegularization(
                TENANT,
                employeeId,
                LocalDate.of(2026, 10, 14),
                Instant.parse("2026-10-14T03:30:00Z"),
                Instant.parse("2026-10-14T12:30:00Z"),
                "Forgot to clock in",
                "tester");
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        if (decidedBy != null) {
            row.decide(RegularizationStatus.APPROVED, Instant.parse("2026-10-15T05:00:00Z"), decidedBy, "ok");
        }
        return row;
    }
}
