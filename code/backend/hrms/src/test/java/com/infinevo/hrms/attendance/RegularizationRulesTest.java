package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.attendance.AttendanceDay;
import com.infinevo.core.attendance.AttendanceQuery;
import com.infinevo.core.attendance.AttendanceSource;
import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-40.4 §7, {@code RegularizationRulesTest}: every row of §4 "Rules at submit", with a fixed clock. The tenant is in
 * Asia/Kolkata; now is 2026-10-15 17:30 IST, so today is 2026-10-15.
 */
class RegularizationRulesTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    private AttendanceRegularizationRepository regularizations;
    private ClockSessionRepository clockSessions;
    private AttendancePreferenceService preferences;
    private AttendanceQuery attendanceQuery;
    private ApprovalService approvalService;
    private EmployeeService employeeService;
    private RegularizationServiceImpl service;
    private UUID instanceId;

    @BeforeEach
    void setUp() {
        regularizations = mock(AttendanceRegularizationRepository.class);
        clockSessions = mock(ClockSessionRepository.class);
        preferences = mock(AttendancePreferenceService.class);
        attendanceQuery = mock(AttendanceQuery.class);
        approvalService = mock(ApprovalService.class);
        employeeService = mock(EmployeeService.class);
        TenantClock tenantClock = mock(TenantClock.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        when(tenantClock.today()).thenReturn(TODAY);
        when(tenantClock.dateOf(any()))
                .thenAnswer(
                        inv -> ((Instant) inv.getArgument(0)).atZone(KOLKATA).toLocalDate());

        EmployeeResponse employee = mock(EmployeeResponse.class);
        when(employee.id()).thenReturn(EMPLOYEE);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(employee));

        preferences(null, null, true);
        when(attendanceQuery.days(any(), any(), any())).thenReturn(List.of());
        when(regularizations.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(regularizations.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        instanceId = UUID.randomUUID();
        when(approvalService.start(eq(ApprovalFlowType.REGULARIZATION), any(SubjectRef.class), eq(EMPLOYEE)))
                .thenReturn(instanceId);

        service = new RegularizationServiceImpl(
                regularizations,
                clockSessions,
                preferences,
                attendanceQuery,
                approvalService,
                employeeService,
                tenantClock,
                mock(com.infinevo.shared.authz.PermissionService.class),
                clock);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static AttendanceRegularization withId(AttendanceRegularization row) {
        if (row.getId() == null) {
            ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        }
        return row;
    }

    private void preferences(Integer windowDays, Integer maxPerMonth, boolean allowWithoutSession) {
        when(preferences.current())
                .thenReturn(new AttendancePreferenceResponse(
                        null,
                        TENANT,
                        HoursCalculation.EVERY_SESSION,
                        AttendancePreferenceResponse.DEFAULT_FULL_DAY_HOURS,
                        AttendancePreferenceResponse.DEFAULT_HALF_DAY_HOURS,
                        windowDays,
                        maxPerMonth,
                        allowWithoutSession,
                        false,
                        null,
                        null));
    }

    private static OffsetDateTime at(LocalDate date, int hour, int minute) {
        return OffsetDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, minute, 0, 0, IST);
    }

    /** 09:00 to 18:00 IST on the date. */
    private static RegularizationRequest normal(LocalDate date) {
        return new RegularizationRequest(date, at(date, 9, 0), at(date, 18, 0), "Forgot to clock out");
    }

    private void assertValidation(RegularizationRequest request) {
        assertThatThrownBy(() -> service.submit(request)).isInstanceOf(RegularizationService.ValidationException.class);
        verify(regularizations, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A sound request is saved PENDING and starts the REGULARIZATION flow on its own row")
    void soundRequestStartsTheFlow() {
        RegularizationResponse response = service.submit(normal(DAY));

        assertThat(response.status()).isEqualTo(RegularizationStatus.PENDING);
        assertThat(response.approvalInstanceId()).isEqualTo(instanceId);
        assertThat(response.employeeId()).isEqualTo(EMPLOYEE);
        assertThat(response.date()).isEqualTo(DAY);
        assertThat(response.inAt()).isEqualTo(Instant.parse("2026-10-14T03:30:00Z"));
        assertThat(response.outAt()).isEqualTo(Instant.parse("2026-10-14T12:30:00Z"));

        ArgumentCaptor<SubjectRef> subject = ArgumentCaptor.forClass(SubjectRef.class);
        verify(approvalService).start(eq(ApprovalFlowType.REGULARIZATION), subject.capture(), eq(EMPLOYEE));
        assertThat(subject.getValue().table()).isEqualTo("hrms.attendance_regularization");
        assertThat(subject.getValue().id()).isEqualTo(response.id());
    }

    @Test
    @DisplayName("A login not linked to an employee is refused as W-40.3 refuses it")
    void loginNotLinked() {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(normal(DAY))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Missing date, inAt or outAt is 400")
    void missingFields() {
        assertValidation(new RegularizationRequest(null, at(DAY, 9, 0), at(DAY, 18, 0), "x"));
        assertValidation(new RegularizationRequest(DAY, null, at(DAY, 18, 0), "x"));
        assertValidation(new RegularizationRequest(DAY, at(DAY, 9, 0), null, "x"));
    }

    @Test
    @DisplayName("A date after the tenant's today is 400")
    void dateAfterToday() {
        LocalDate tomorrow = TODAY.plusDays(1);
        assertValidation(new RegularizationRequest(tomorrow, at(TODAY, 9, 0), at(TODAY, 10, 0), "x"));
    }

    @Test
    @DisplayName("inAt not before outAt is 400")
    void inNotBeforeOut() {
        assertValidation(new RegularizationRequest(DAY, at(DAY, 18, 0), at(DAY, 18, 0), "x"));
        assertValidation(new RegularizationRequest(DAY, at(DAY, 18, 0), at(DAY, 9, 0), "x"));
    }

    @Test
    @DisplayName("More than 24 hours apart is 400; exactly 24 hours is accepted")
    void moreThanADay() {
        assertValidation(new RegularizationRequest(DAY.minusDays(1), at(DAY.minusDays(1), 9, 0), at(DAY, 9, 1), "x"));

        RegularizationResponse ok = service.submit(
                new RegularizationRequest(DAY.minusDays(1), at(DAY.minusDays(1), 9, 0), at(DAY, 9, 0), "x"));
        assertThat(ok.status()).isEqualTo(RegularizationStatus.PENDING);
    }

    @Test
    @DisplayName("outAt in the future is 400")
    void outInFuture() {
        // Now is 17:30 IST today.
        assertValidation(new RegularizationRequest(TODAY, at(TODAY, 9, 0), at(TODAY, 18, 0), "x"));
    }

    @Test
    @DisplayName("inAt on another day in the tenant's zone is 400, even when it is that day in UTC")
    void inAtOnAnotherDay() {
        // 2026-10-15 01:00 IST is 2026-10-14 19:30 UTC: the tenant's day is the 15th, not the 14th.
        assertValidation(new RegularizationRequest(DAY, at(TODAY, 1, 0), at(TODAY, 2, 0), "x"));
        assertValidation(new RegularizationRequest(DAY, at(DAY.minusDays(1), 23, 0), at(DAY, 8, 0), "x"));
    }

    @Test
    @DisplayName("A blank, missing or over-500-character reason is 400; exactly 500 is accepted")
    void reason() {
        assertValidation(new RegularizationRequest(DAY, at(DAY, 9, 0), at(DAY, 18, 0), null));
        assertValidation(new RegularizationRequest(DAY, at(DAY, 9, 0), at(DAY, 18, 0), "   "));
        assertValidation(new RegularizationRequest(DAY, at(DAY, 9, 0), at(DAY, 18, 0), "x".repeat(501)));

        assertThat(service.submit(new RegularizationRequest(DAY, at(DAY, 9, 0), at(DAY, 18, 0), "x".repeat(500)))
                        .reason())
                .hasSize(500);
    }

    @Test
    @DisplayName("With a window set, a date older than that many days before today is 400; the edge is accepted")
    void window() {
        preferences(5, null, true);
        assertValidation(normal(TODAY.minusDays(6)));

        assertThat(service.submit(normal(TODAY.minusDays(5))).status()).isEqualTo(RegularizationStatus.PENDING);
    }

    @Test
    @DisplayName("With no window set, an old date is accepted")
    void noWindow() {
        assertThat(service.submit(normal(LocalDate.of(2026, 1, 5))).status()).isEqualTo(RegularizationStatus.PENDING);
    }

    @Test
    @DisplayName("The monthly limit counts PENDING and APPROVED by the corrected day's month, never REJECTED")
    @SuppressWarnings("unchecked")
    void monthlyLimit() {
        preferences(null, 2, true);
        when(regularizations.countByTenantIdAndEmployeeIdAndAttendanceDateBetweenAndStatusIn(
                        eq(TENANT), eq(EMPLOYEE), any(), any(), anyCollection()))
                .thenReturn(2L);
        assertValidation(normal(DAY));

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<Collection<RegularizationStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(regularizations)
                .countByTenantIdAndEmployeeIdAndAttendanceDateBetweenAndStatusIn(
                        eq(TENANT), eq(EMPLOYEE), from.capture(), to.capture(), statuses.capture());
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(RegularizationStatus.PENDING, RegularizationStatus.APPROVED)
                .doesNotContain(RegularizationStatus.REJECTED);

        when(regularizations.countByTenantIdAndEmployeeIdAndAttendanceDateBetweenAndStatusIn(
                        eq(TENANT), eq(EMPLOYEE), any(), any(), anyCollection()))
                .thenReturn(1L);
        assertThat(service.submit(normal(DAY)).status()).isEqualTo(RegularizationStatus.PENDING);
    }

    @Test
    @DisplayName("Without a session allowed off, a date with no session at all is 400; one session is enough")
    void withoutSession() {
        preferences(null, null, false);
        when(clockSessions.findByTenantIdAndEmployeeIdAndAttendanceDateOrderByClockInAtAsc(TENANT, EMPLOYEE, DAY))
                .thenReturn(List.of());
        assertValidation(normal(DAY));

        ClockSession open = new ClockSession(
                TENANT, EMPLOYEE, DAY, Instant.parse("2026-10-14T03:30:00Z"), SessionOrigin.CLOCK, "test");
        when(clockSessions.findByTenantIdAndEmployeeIdAndAttendanceDateOrderByClockInAtAsc(TENANT, EMPLOYEE, DAY))
                .thenReturn(List.of(open));
        assertThat(service.submit(normal(DAY)).status()).isEqualTo(RegularizationStatus.PENDING);
    }

    @Test
    @DisplayName("A pending request for the same employee and date is 409")
    void pendingDuplicate() {
        when(regularizations.existsByTenantIdAndEmployeeIdAndAttendanceDateAndStatus(
                        TENANT, EMPLOYEE, DAY, RegularizationStatus.PENDING))
                .thenReturn(true);
        assertThatThrownBy(() -> service.submit(normal(DAY)))
                .isInstanceOf(RegularizationService.ConflictException.class);
        verify(regularizations, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("A day an administrator set is 409; a clocked day is accepted")
    void adminDay() {
        when(attendanceQuery.days(EMPLOYEE, DAY, DAY))
                .thenReturn(List.of(new AttendanceDay(DAY, AttendanceStatus.ABSENT, AttendanceSource.ADMIN, null)));
        assertThatThrownBy(() -> service.submit(normal(DAY)))
                .isInstanceOf(RegularizationService.ConflictException.class)
                .hasMessageContaining("administrator");
        verify(regularizations, never()).saveAndFlush(any());

        when(attendanceQuery.days(EMPLOYEE, DAY, DAY))
                .thenReturn(List.of(new AttendanceDay(DAY, AttendanceStatus.ABSENT, AttendanceSource.CLOCK, null)));
        assertThat(service.submit(normal(DAY)).status()).isEqualTo(RegularizationStatus.PENDING);
    }

    @Test
    @DisplayName("A concurrent submit that loses the pending unique index is the same 409 as the exists check")
    void concurrentPendingDuplicate() {
        doThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new RuntimeException("ERROR: duplicate key value violates unique constraint \""
                                + RegularizationServiceImpl.INDEX_PENDING_UNIQUE + "\"")))
                .when(regularizations)
                .saveAndFlush(any());
        assertThatThrownBy(() -> service.submit(normal(DAY)))
                .isInstanceOf(RegularizationService.ConflictException.class)
                .hasMessage("a pending regularization already exists for " + DAY);
        verify(approvalService, never()).start(any(), any(), any());
    }

    @Test
    @DisplayName("Any other integrity violation on save is not turned into a 409")
    void otherIntegrityViolationPropagates() {
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("ERROR: violates check constraint \"ck_other\""));
        doThrow(other).when(regularizations).saveAndFlush(any());
        assertThatThrownBy(() -> service.submit(normal(DAY))).isSameAs(other);
    }

    @Test
    @DisplayName(
            "No active REGULARIZATION definition: the engine's refusal becomes a 409 and the transaction rolls back")
    void noDefinition() {
        when(approvalService.start(any(), any(), any()))
                .thenThrow(new IllegalStateException("No active approval definition found for flow REGULARIZATION"));
        assertThatThrownBy(() -> service.submit(normal(DAY)))
                .isInstanceOf(RegularizationService.ConflictException.class)
                .hasMessageContaining("No active approval definition");
    }

    @Test
    @DisplayName("Any other IllegalStateException from the engine is not turned into a 409")
    void otherEngineFailurePropagates() {
        IllegalStateException other = new IllegalStateException("No approver resolved for step 0");
        when(approvalService.start(any(), any(), any())).thenThrow(other);
        assertThatThrownBy(() -> service.submit(normal(DAY))).isSameAs(other);
    }

    @Test
    @DisplayName("Reads refuse a span over 93 days or from after to")
    void readRange() {
        assertThatThrownBy(() -> service.mine(DAY, DAY.minusDays(1)))
                .isInstanceOf(RegularizationService.ValidationException.class);
        assertThatThrownBy(() -> service.all(DAY.minusDays(94), DAY, null, null))
                .isInstanceOf(RegularizationService.ValidationException.class);
        assertThat(service.all(DAY.minusDays(93), DAY, null, null)).isEmpty();
    }
}
