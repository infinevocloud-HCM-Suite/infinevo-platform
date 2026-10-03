package com.infinevo.hrms.attendance;

import com.infinevo.core.attendance.AttendanceService;
import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.attendance.ClockDayResult;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ClockDayService} (W-40.3 §4).
 */
@Service
public class ClockDayServiceImpl implements ClockDayService {

    private final ClockSessionRepository clockSessions;
    private final AttendancePreferenceService preferenceService;
    private final AttendanceService attendanceService;

    @Autowired
    public ClockDayServiceImpl(
            ClockSessionRepository clockSessions,
            AttendancePreferenceService preferenceService,
            AttendanceService attendanceService) {
        this.clockSessions = Objects.requireNonNull(clockSessions, "clockSessions must not be null");
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService must not be null");
        this.attendanceService = Objects.requireNonNull(attendanceService, "attendanceService must not be null");
    }

    @Override
    @Transactional
    public ClockDayResponse rederive(UUID employeeId, LocalDate date) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        UUID tenantId = TenantContext.require();

        List<ClockSession> validSessions =
                clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                                tenantId, employeeId, date);

        AttendancePreferenceResponse pref = preferenceService.current();
        long workedMinutes = 0;

        if (!validSessions.isEmpty()) {
            if (pref.hoursCalculation() == HoursCalculation.FIRST_IN_LAST_OUT) {
                Instant firstIn = validSessions.get(0).getClockInAt();
                Instant lastOut = validSessions.get(0).getClockOutAt();
                for (ClockSession s : validSessions) {
                    if (s.getClockInAt().isBefore(firstIn)) {
                        firstIn = s.getClockInAt();
                    }
                    if (s.getClockOutAt().isAfter(lastOut)) {
                        lastOut = s.getClockOutAt();
                    }
                }
                workedMinutes = Duration.between(firstIn, lastOut).toMinutes();
            } else {
                for (ClockSession s : validSessions) {
                    workedMinutes += Duration.between(s.getClockInAt(), s.getClockOutAt())
                            .toMinutes();
                }
            }
        }

        int fullDayMinutes = pref.fullDayMinimumHours() != null
                ? pref.fullDayMinimumHours().multiply(BigDecimal.valueOf(60)).intValue()
                : 540;
        int halfDayMinutes = pref.halfDayMinimumHours() != null
                ? pref.halfDayMinimumHours().multiply(BigDecimal.valueOf(60)).intValue()
                : 270;

        AttendanceStatus status;
        if (workedMinutes >= fullDayMinutes) {
            status = AttendanceStatus.PRESENT;
        } else if (workedMinutes >= halfDayMinutes) {
            status = AttendanceStatus.HALF_DAY;
        } else {
            status = AttendanceStatus.ABSENT;
        }

        ClockDayResult result = attendanceService.recordFromClock(employeeId, date, status);
        return new ClockDayResponse(date, (int) workedMinutes, result.status(), result.written());
    }
}
