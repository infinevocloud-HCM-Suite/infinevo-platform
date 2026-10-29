package com.infinevo.hrms.attendance;

import com.infinevo.core.attendance.Attendance;
import com.infinevo.core.attendance.AttendanceRepository;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ClockService} (W-40.3 §4).
 */
@Service
public class ClockServiceImpl implements ClockService {

    private final ClockSessionRepository clockSessions;
    private final ClockDayService clockDayService;
    private final EmployeeService employeeService;
    private final TenantClock tenantClock;
    private final AttendanceRepository attendanceRepository;
    private final Clock clock;

    @Autowired
    public ClockServiceImpl(
            ClockSessionRepository clockSessions,
            ClockDayService clockDayService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            AttendanceRepository attendanceRepository,
            org.springframework.beans.factory.ObjectProvider<Clock> clockProvider) {
        this(
                clockSessions,
                clockDayService,
                employeeService,
                tenantClock,
                attendanceRepository,
                clockProvider != null ? clockProvider.getIfAvailable(Clock::systemUTC) : Clock.systemUTC());
    }

    public ClockServiceImpl(
            ClockSessionRepository clockSessions,
            ClockDayService clockDayService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            Clock clock) {
        this(clockSessions, clockDayService, employeeService, tenantClock, null, clock);
    }

    public ClockServiceImpl(
            ClockSessionRepository clockSessions,
            ClockDayService clockDayService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            AttendanceRepository attendanceRepository,
            Clock clock) {
        this.clockSessions = Objects.requireNonNull(clockSessions, "clockSessions must not be null");
        this.clockDayService = Objects.requireNonNull(clockDayService, "clockDayService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.attendanceRepository = attendanceRepository;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public ClockSessionResponse clockIn() {
        EmployeeResponse employee = requireCurrentEmployee();
        UUID tenantId = TenantContext.require();
        Instant now = clock.instant();
        LocalDate today = tenantClock.dateOf(now);

        Optional<ClockSession> openOpt =
                clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(tenantId, employee.id());
        if (openOpt.isPresent()) {
            ClockSession openSession = openOpt.get();
            boolean isOver24Hours =
                    Duration.between(openSession.getClockInAt(), now).compareTo(Duration.ofHours(24)) > 0;
            if (openSession.getAttendanceDate().equals(today) && !isOver24Hours) {
                throw new ClockConflictException("Active session already open for today");
            }
            openSession.voidSession(VoidReason.NOT_CLOCKED_OUT, now, currentActor());
            clockSessions.saveAndFlush(openSession);
        }

        ClockSession newSession =
                new ClockSession(tenantId, employee.id(), today, now, SessionOrigin.CLOCK, currentActor());
        newSession = clockSessions.save(newSession);
        return ClockSessionResponse.from(newSession);
    }

    @Override
    @Transactional
    public ClockOutResponse clockOut() {
        EmployeeResponse employee = requireCurrentEmployee();
        UUID tenantId = TenantContext.require();
        Instant now = clock.instant();

        ClockSession openSession = clockSessions
                .findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(tenantId, employee.id())
                .orElseThrow(() -> new ClockConflictException("No active session open to clock out"));

        Duration duration = Duration.between(openSession.getClockInAt(), now);
        if (duration.compareTo(Duration.ofHours(24)) > 0) {
            throw new ClockConflictException("Session cannot be clocked out more than 24 hours after clock in");
        }

        openSession.close(now, currentActor());
        openSession = clockSessions.save(openSession);

        ClockDayResponse day = clockDayService.rederive(employee.id(), openSession.getAttendanceDate());
        return new ClockOutResponse(ClockSessionResponse.from(openSession), day);
    }

    @Override
    @Transactional(readOnly = true)
    public TodayResponse today() {
        EmployeeResponse employee = requireCurrentEmployee();
        UUID tenantId = TenantContext.require();
        Instant now = clock.instant();
        LocalDate today = tenantClock.dateOf(now);

        Optional<ClockSession> openOpt =
                clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(tenantId, employee.id());
        List<ClockSession> sessions = clockSessions.findByTenantIdAndEmployeeIdAndAttendanceDateOrderByClockInAtAsc(
                tenantId, employee.id(), today);

        long workedMinutes = 0;
        for (ClockSession s : sessions) {
            if (s.getClockOutAt() != null && s.getVoidedAt() == null) {
                workedMinutes +=
                        Duration.between(s.getClockInAt(), s.getClockOutAt()).toMinutes();
            }
        }

        return new TodayResponse(
                today,
                openOpt.map(ClockSessionResponse::from).orElse(null),
                sessions.stream().map(ClockSessionResponse::from).toList(),
                (int) workedMinutes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClockSessionResponse> mySessions(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        EmployeeResponse employee = requireCurrentEmployee();
        UUID tenantId = TenantContext.require();

        List<ClockSession> list = clockSessions.findByTenantIdAndEmployeeIdAndAttendanceDateBetweenOrderByClockInAtDesc(
                tenantId, employee.id(), from, to);

        java.util.Map<LocalDate, Attendance> attMap = new java.util.HashMap<>();
        if (attendanceRepository != null) {
            List<Attendance> attendances =
                    attendanceRepository.findByTenantIdAndEmployeeIdAndDateRange(tenantId, employee.id(), from, to);
            if (attendances != null) {
                for (Attendance a : attendances) {
                    if (a.getAttendanceDate() != null) {
                        attMap.put(a.getAttendanceDate(), a);
                    }
                }
            }
        }

        return list.stream()
                .map(s -> {
                    Attendance att = attMap.get(s.getAttendanceDate());
                    String dayStatus = att != null && att.getStatus() != null
                            ? att.getStatus().name()
                            : null;
                    String remarks = att != null ? att.getRemarks() : null;
                    return ClockSessionResponse.from(s, java.util.Map.of(), dayStatus, remarks);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClockSessionResponse> allSessions(LocalDate from, LocalDate to, UUID employeeId) {
        validateDateRange(from, to);
        UUID tenantId = TenantContext.require();

        List<ClockSession> list = employeeId != null
                ? clockSessions.findByTenantIdAndEmployeeIdAndAttendanceDateBetweenOrderByClockInAtDesc(
                        tenantId, employeeId, from, to)
                : clockSessions.findByTenantIdAndAttendanceDateBetweenOrderByClockInAtDesc(tenantId, from, to);
        // One batch name read for the whole list (W-48.4 §4), never one per row.
        java.util.Set<UUID> ids =
                list.stream().map(ClockSession::getEmployeeId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, String> names = ids.isEmpty() ? java.util.Map.of() : employeeService.displayNames(ids);
        java.util.Map<UUID, String> resolved = names != null ? names : java.util.Map.of();

        // Batch read daily attendance status and remarks
        java.util.Map<String, Attendance> attMap = new java.util.HashMap<>();
        if (attendanceRepository != null) {
            List<Attendance> attendances = employeeId != null
                    ? attendanceRepository.findByTenantIdAndEmployeeIdAndDateRange(tenantId, employeeId, from, to)
                    : attendanceRepository.findByTenantIdAndDateRange(tenantId, from, to);
            if (attendances != null) {
                for (Attendance a : attendances) {
                    if (a.getEmployee() != null && a.getAttendanceDate() != null) {
                        attMap.put(a.getEmployee().getId() + "_" + a.getAttendanceDate(), a);
                    }
                }
            }
        }

        return list.stream()
                .map(s -> {
                    Attendance att = attMap.get(s.getEmployeeId() + "_" + s.getAttendanceDate());
                    String dayStatus = att != null && att.getStatus() != null
                            ? att.getStatus().name()
                            : null;
                    String remarks = att != null ? att.getRemarks() : null;
                    return ClockSessionResponse.from(s, resolved, dayStatus, remarks);
                })
                .toList();
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
