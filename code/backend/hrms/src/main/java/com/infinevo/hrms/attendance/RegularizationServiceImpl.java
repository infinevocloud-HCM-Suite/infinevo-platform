package com.infinevo.hrms.attendance;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.attendance.AttendanceQuery;
import com.infinevo.core.attendance.AttendanceSource;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link RegularizationService} (W-40.4 §4).
 *
 * <p>The request row and the approval instance are written in one transaction, as
 * {@code payroll/.../reimbursement/ReimbursementClaimServiceImpl.java:125-140} does: if the engine refuses (no active
 * {@code REGULARIZATION} definition) the request is not saved.
 */
@Service
public class RegularizationServiceImpl implements RegularizationService {

    private static final Duration MAX_SPAN = Duration.ofHours(24);

    private final AttendanceRegularizationRepository regularizations;
    private final ClockSessionRepository clockSessions;
    private final AttendancePreferenceService preferences;
    private final AttendanceQuery attendanceQuery;
    private final ApprovalService approvalService;
    private final EmployeeService employeeService;
    private final TenantClock tenantClock;
    private final Clock clock;

    @Autowired
    public RegularizationServiceImpl(
            AttendanceRegularizationRepository regularizations,
            ClockSessionRepository clockSessions,
            AttendancePreferenceService preferences,
            AttendanceQuery attendanceQuery,
            ApprovalService approvalService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            ObjectProvider<Clock> clockProvider) {
        this(
                regularizations,
                clockSessions,
                preferences,
                attendanceQuery,
                approvalService,
                employeeService,
                tenantClock,
                clockProvider != null ? clockProvider.getIfAvailable(Clock::systemUTC) : Clock.systemUTC());
    }

    RegularizationServiceImpl(
            AttendanceRegularizationRepository regularizations,
            ClockSessionRepository clockSessions,
            AttendancePreferenceService preferences,
            AttendanceQuery attendanceQuery,
            ApprovalService approvalService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            Clock clock) {
        this.regularizations = Objects.requireNonNull(regularizations, "regularizations must not be null");
        this.clockSessions = Objects.requireNonNull(clockSessions, "clockSessions must not be null");
        this.preferences = Objects.requireNonNull(preferences, "preferences must not be null");
        this.attendanceQuery = Objects.requireNonNull(attendanceQuery, "attendanceQuery must not be null");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public RegularizationResponse submit(RegularizationRequest request) {
        EmployeeResponse employee = requireCurrentEmployee();
        UUID tenantId = TenantContext.require();
        if (request == null || request.date() == null || request.inAt() == null || request.outAt() == null) {
            throw new ValidationException("date, inAt and outAt are required");
        }
        LocalDate date = request.date();
        Instant inAt = request.inAt().toInstant();
        Instant outAt = request.outAt().toInstant();
        LocalDate today = tenantClock.today();

        if (date.isAfter(today)) {
            throw new ValidationException("date must not be after today");
        }
        if (!inAt.isBefore(outAt)) {
            throw new ValidationException("inAt must be before outAt");
        }
        if (Duration.between(inAt, outAt).compareTo(MAX_SPAN) > 0) {
            throw new ValidationException("inAt and outAt must be at most 24 hours apart");
        }
        if (outAt.isAfter(clock.instant())) {
            throw new ValidationException("outAt must not be in the future");
        }
        if (!tenantClock.dateOf(inAt).equals(date)) {
            throw new ValidationException("inAt must fall on date in the tenant's time zone");
        }
        String reason = request.reason();
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason is required");
        }
        if (reason.length() > AttendanceRegularization.MAX_TEXT) {
            throw new ValidationException(
                    "reason must be at most " + AttendanceRegularization.MAX_TEXT + " characters");
        }

        AttendancePreferenceResponse pref = preferences.current();
        Integer windowDays = pref.regularizationWindowDays();
        if (windowDays != null && date.isBefore(today.minusDays(windowDays))) {
            throw new ValidationException("date is older than the regularization window of " + windowDays + " days");
        }
        Integer maxPerMonth = pref.maxRegularizationsPerMonth();
        if (maxPerMonth != null) {
            YearMonth month = YearMonth.from(date);
            long used = regularizations.countByTenantIdAndEmployeeIdAndAttendanceDateBetweenAndStatusIn(
                    tenantId,
                    employee.id(),
                    month.atDay(1),
                    month.atEndOfMonth(),
                    EnumSet.of(RegularizationStatus.PENDING, RegularizationStatus.APPROVED));
            if (used >= maxPerMonth) {
                throw new ValidationException(
                        "the limit of " + maxPerMonth + " regularizations for " + month + " is reached");
            }
        }
        if (!pref.allowRegularizationWithoutSession()
                && clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateOrderByClockInAtAsc(tenantId, employee.id(), date)
                        .isEmpty()) {
            throw new ValidationException("the date has no clock session to regularize");
        }
        if (regularizations.existsByTenantIdAndEmployeeIdAndAttendanceDateAndStatus(
                tenantId, employee.id(), date, RegularizationStatus.PENDING)) {
            throw new ConflictException("a pending regularization already exists for " + date);
        }
        boolean adminDay = attendanceQuery.days(employee.id(), date, date).stream()
                .anyMatch(d -> date.equals(d.date()) && d.source() == AttendanceSource.ADMIN);
        if (adminDay) {
            throw new ConflictException("attendance for " + date + " was set by an administrator");
        }

        AttendanceRegularization row =
                new AttendanceRegularization(tenantId, employee.id(), date, inAt, outAt, reason, currentActor());
        row = regularizations.save(row);

        UUID instanceId = approvalService.start(
                ApprovalFlowType.REGULARIZATION, new SubjectRef(SUBJECT_TABLE, row.getId()), employee.id());
        row.attachInstance(instanceId);
        row = regularizations.save(row);
        return RegularizationResponse.from(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegularizationResponse> mine(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        EmployeeResponse employee = requireCurrentEmployee();
        UUID tenantId = TenantContext.require();
        return regularizations
                .findByTenantIdAndEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                        tenantId, employee.id(), from, to)
                .stream()
                .map(RegularizationResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegularizationResponse> all(
            LocalDate from, LocalDate to, RegularizationStatus status, UUID employeeId) {
        validateDateRange(from, to);
        UUID tenantId = TenantContext.require();
        List<AttendanceRegularization> rows;
        if (employeeId != null && status != null) {
            rows =
                    regularizations
                            .findByTenantIdAndEmployeeIdAndStatusAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                                    tenantId, employeeId, status, from, to);
        } else if (employeeId != null) {
            rows =
                    regularizations
                            .findByTenantIdAndEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                                    tenantId, employeeId, from, to);
        } else if (status != null) {
            rows =
                    regularizations
                            .findByTenantIdAndStatusAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                                    tenantId, status, from, to);
        } else {
            rows = regularizations.findByTenantIdAndAttendanceDateBetweenOrderByAttendanceDateDescCreatedAtDesc(
                    tenantId, from, to);
        }
        return rows.stream().map(RegularizationResponse::from).toList();
    }

    private EmployeeResponse requireCurrentEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("from date must not be after to date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ValidationException("date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
