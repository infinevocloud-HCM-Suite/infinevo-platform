package com.infinevo.core.attendance;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AttendanceService} and {@link AttendanceQuery} (W-39.1, W-40.2).
 */
@Service
public class AttendanceServiceImpl implements AttendanceService, AttendanceQuery {

    private static final int MAX_BATCH_SIZE = 1000;
    private static final long MAX_DATE_SPAN_DAYS = 93;

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantClock tenantClock;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository) {
        this(attendanceRepository, employeeRepository, (TenantClock) null);
    }

    public AttendanceServiceImpl(
            AttendanceRepository attendanceRepository, EmployeeRepository employeeRepository, TenantClock tenantClock) {
        this.attendanceRepository =
                Objects.requireNonNull(attendanceRepository, "attendanceRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.tenantClock = tenantClock;
    }

    @Autowired
    public AttendanceServiceImpl(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            org.springframework.beans.factory.ObjectProvider<TenantClock> tenantClockProvider) {
        this(
                attendanceRepository,
                employeeRepository,
                tenantClockProvider != null ? tenantClockProvider.getIfAvailable() : null);
    }

    @Override
    @Transactional
    public List<AttendanceResponse> upsert(List<AttendanceEntry> entries) {
        UUID tenantId = TenantContext.require();

        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be empty");
        }
        if (entries.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "entries cannot exceed " + MAX_BATCH_SIZE + " items in a single request");
        }

        LocalDate today = LocalDate.now();
        Set<EmployeeDatePair> seen = new HashSet<>();

        // Validation pass: reject entire batch if any entry is invalid
        for (AttendanceEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("entry must not be null");
            }
            if (entry.employeeId() == null) {
                throw new IllegalArgumentException("employeeId is required");
            }
            if (entry.date() == null) {
                throw new IllegalArgumentException("date is required");
            }
            if (entry.status() == null) {
                throw new IllegalArgumentException("status is required");
            }
            if (entry.date().isAfter(today)) {
                throw new IllegalArgumentException("Attendance date cannot be in the future: " + entry.date());
            }
            if (!seen.add(new EmployeeDatePair(entry.employeeId(), entry.date()))) {
                throw new IllegalArgumentException(
                        "Duplicate entry for employee " + entry.employeeId() + " on " + entry.date());
            }
        }

        // Verify employees belong to current tenant and are not soft-deleted
        Map<UUID, Employee> employeeMap = new HashMap<>();
        for (AttendanceEntry entry : entries) {
            if (!employeeMap.containsKey(entry.employeeId())) {
                Employee employee = employeeRepository
                        .findByIdAndTenantIdAndDeletedFalse(entry.employeeId(), tenantId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Employee not found in tenant or is deleted: " + entry.employeeId()));
                employeeMap.put(entry.employeeId(), employee);
            }
        }

        // Execution pass: idempotent update existing rows or create new rows
        String actor = currentActor();
        List<AttendanceResponse> responses = new ArrayList<>(entries.size());
        for (AttendanceEntry entry : entries) {
            Employee employee = employeeMap.get(entry.employeeId());
            Optional<Attendance> existing = attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(
                    tenantId, entry.employeeId(), entry.date());
            Attendance attendance;
            if (existing.isPresent()) {
                attendance = existing.get();
                attendance.update(entry.status(), AttendanceSource.ADMIN, entry.remarks(), actor);
            } else {
                attendance = new Attendance(
                        tenantId,
                        employee,
                        entry.date(),
                        entry.status(),
                        AttendanceSource.ADMIN,
                        entry.remarks(),
                        actor);
            }
            attendance = attendanceRepository.save(attendance);
            responses.add(AttendanceResponse.from(attendance));
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> list(LocalDate from, LocalDate to, UUID employeeId) {
        UUID tenantId = TenantContext.require();

        validateDateRange(from, to);

        List<Attendance> records = (employeeId != null)
                ? attendanceRepository.findByTenantIdAndEmployeeIdAndDateRange(tenantId, employeeId, from, to)
                : attendanceRepository.findByTenantIdAndDateRange(tenantId, from, to);

        return records.stream().map(AttendanceResponse::from).toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        Attendance attendance =
                attendanceRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(id));
        attendanceRepository.delete(attendance);
        attendanceRepository.flush();
    }

    @Override
    @Transactional
    public ClockDayResult recordFromClock(UUID employeeId, LocalDate date, AttendanceStatus status) {
        UUID tenantId = TenantContext.require();

        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }

        LocalDate today = tenantClock != null ? tenantClock.today() : LocalDate.now();
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Attendance date cannot be in the future: " + date);
        }

        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Employee not found in tenant or is deleted: " + employeeId));

        Optional<Attendance> existing =
                attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, employeeId, date);

        if (existing.isEmpty()) {
            String actor = currentActor();
            Attendance attendance =
                    new Attendance(tenantId, employee, date, status, AttendanceSource.CLOCK, null, actor);
            attendanceRepository.save(attendance);
            return new ClockDayResult(true, status, AttendanceSource.CLOCK);
        }

        Attendance attendance = existing.get();
        if (attendance.getSource() == AttendanceSource.ADMIN) {
            return new ClockDayResult(false, attendance.getStatus(), AttendanceSource.ADMIN);
        }

        String actor = currentActor();
        attendance.update(status, AttendanceSource.CLOCK, attendance.getRemarks(), actor);
        attendanceRepository.save(attendance);
        return new ClockDayResult(true, status, AttendanceSource.CLOCK);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceDay> days(UUID employeeId, LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.require();
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        validateDateRange(from, to);

        List<Attendance> records =
                attendanceRepository.findByTenantIdAndEmployeeIdAndDateRangeAsc(tenantId, employeeId, from, to);

        return records.stream()
                .map(a -> new AttendanceDay(a.getAttendanceDate(), a.getStatus(), a.getSource(), a.getRemarks()))
                .toList();
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from date must not be after to date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DATE_SPAN_DAYS) {
            throw new IllegalArgumentException("Date range must not exceed " + MAX_DATE_SPAN_DAYS + " days");
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String name = auth.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "system";
    }

    private record EmployeeDatePair(UUID employeeId, LocalDate date) {}
}
