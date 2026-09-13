package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.AttendanceDTO;
import com.phegondev.usersmanagementsystem.dto.ClockSessionDTO;
import com.phegondev.usersmanagementsystem.entity.Attendance;
import com.phegondev.usersmanagementsystem.entity.ClockSession;
import com.phegondev.usersmanagementsystem.enumuration.AttendanceStatus;
import com.phegondev.usersmanagementsystem.repository.AttendanceRepository;
import com.phegondev.usersmanagementsystem.repository.ClockSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private ClockSessionRepository clockSessionRepository;


    public AttendanceDTO clockIn(String employeeId, String employeeName, LocalDateTime now) {
       // LocalDateTime now = LocalDateTime.now();

        System.out.println("🔔 [Clock-In Triggered] Time: " + now);
        System.out.println("👤 Employee ID: " + employeeId + ", Name: " + employeeName);

        Attendance latestAttendance = attendanceRepository
                .findTopByEmployeeIdOrderByInTimeDesc(employeeId)
                .orElse(null);

        boolean shouldCreateNewAttendance = shouldCreateNewAttendance(latestAttendance, now);

        Attendance attendance;

        if (shouldCreateNewAttendance) {
            attendance = new Attendance();
            attendance.setEmployeeId(employeeId);
            attendance.setEmployeeName(employeeName);
            attendance.setInTime(now);
            System.out.println("✅ New Attendance created: " + attendance);
        } else {
            attendance = latestAttendance;
            System.out.println("↩️ Reusing Attendance with ID: " + attendance.getId());
        }


        // Step 2: Create and attach ClockSession
        ClockSession session = new ClockSession();
        session.setInTime(now);
        attendance.addClockSession(session);
        Attendance savedAttendance =  attendanceRepository.save(attendance);// sets attendance internally

        System.out.println("🕐 New ClockSession created at: " + now);
        System.out.println("🔗 Linked ClockSession to Attendance ID: " + attendance.getId());

        // Step 3: Update status
        updateAttendanceStatus(attendance);
        System.out.println("🔄 Attendance status updated to: " + attendance.getStatus());

        // Step 5: Return DTO
        AttendanceDTO dto = toAttendanceDTO(savedAttendance);
        System.out.println("📤 Returning AttendanceDTO with " + savedAttendance.getClockSessions().size() + " session(s)");
        return dto;
    }

    private boolean shouldCreateNewAttendance(Attendance latestAttendance, LocalDateTime now) {
        if (latestAttendance == null) {
            System.out.println("📄 No previous attendance found. Creating new attendance.");
            return true;
        }

        System.out.println("📄 Found last attendance:");
        System.out.println("    ➤ InTime: " + latestAttendance.getInTime());
        System.out.println("    ➤ Status: " + latestAttendance.getStatus());
        System.out.println("    ➤ End Day: " + latestAttendance.isEndDay());

        // Check 1: Day was already ended
        if (latestAttendance.isEndDay()) {
            System.out.println("🚫 End Day is true. Creating new attendance.");
            return true;
        }

        // Check 2: Time since last attendance
        Duration durationSinceLastInTime = Duration.between(latestAttendance.getInTime(), now);
        long hoursSinceLastAttendance = durationSinceLastInTime.toHours();
        System.out.println("⏱️ Time since last attendance: " + hoursSinceLastAttendance + " hour(s)");

        if (hoursSinceLastAttendance > 9) {
            System.out.println("🕘 More than 9 hours passed. Creating new attendance.");
            return true;
        }

        System.out.println("🔁 Less than 9 hours passed. Reusing last attendance.");
        return false;
    }






    public List<AttendanceDTO> getMyAttendance(String employeeId) {
        System.out.println("🔍 [Service] Fetching attendance records for employeeId: " + employeeId);

        List<Attendance> attendanceList = attendanceRepository.findByEmployeeIdOrderByInTimeDesc(employeeId);

        System.out.println("✅ [Service] Found " + attendanceList.size() + " attendance entries for " + employeeId);

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service] Converted to " + dtoList.size() + " DTOs");

        return dtoList;
    }


    public void updateAttendanceStatus(Attendance attendance) {
        List<ClockSession> sessions = attendance.getClockSessions();

        Duration totalDuration = Duration.ZERO;

        for (ClockSession session : sessions) {
            if (session.getInTime() != null && session.getOutTime() != null) {
                Duration sessionDuration = Duration.between(session.getInTime(), session.getOutTime());
                totalDuration = totalDuration.plus(sessionDuration);
                System.out.println("🕒 Session from " + session.getInTime() + " to " + session.getOutTime()
                        + " = " + sessionDuration.toMinutes() + " mins");
            } else {
                System.out.println("⚠️ Skipping session with incomplete in/out time.");
            }
        }

        double totalHours = totalDuration.toMinutes() / 60.0;
        System.out.println("⏱️ Total worked hours: " + totalHours);

        if (totalHours >= 9) {
            attendance.setStatus(AttendanceStatus.PRESENT);
        } else if (totalHours >= 4.5) {
            attendance.setStatus(AttendanceStatus.HALF_DAY);
        } else {
            attendance.setStatus(AttendanceStatus.ABSENT);
        }

        System.out.println("📌 Updated status: " + attendance.getStatus());
    }

    public  AttendanceDTO toAttendanceDTO(Attendance attendance) {
        AttendanceDTO dto = new AttendanceDTO();
        dto.setId(attendance.getId());
        dto.setEmployeeId(attendance.getEmployeeId());
        dto.setEmployeeName(attendance.getEmployeeName());
        dto.setInTime(attendance.getInTime());
        dto.setOutTime(attendance.getOutTime());
        dto.setStatus(attendance.getStatus().name());
        dto.setCreatedAt(attendance.getCreatedAt());
        dto.setUpdatedAt(attendance.getUpdatedAt());
        dto.setEndDay(attendance.isEndDay());

        List<ClockSessionDTO> sessionDTOs = attendance.getClockSessions().stream()
                .map(this :: toClockSessionDTO)
                .collect(Collectors.toList());
        dto.setClockSessions(sessionDTOs);

        return dto;
    }

    public  ClockSessionDTO toClockSessionDTO(ClockSession session) {
        ClockSessionDTO dto = new ClockSessionDTO();
        dto.setId(session.getId());
        dto.setInTime(session.getInTime());
        dto.setOutTime(session.getOutTime());
        return dto;
    }




/*
    public Optional<Attendance> clockOut(Long attendanceId) {
        return repository.findById(attendanceId).map(attendance -> {
            attendance.setOutTime(LocalDateTime.now());
            long durationHours = Duration.between(attendance.getInTime(), attendance.getOutTime()).toHours();

            if (durationHours < 4)
                attendance.setStatus("Absent");
            else if (durationHours < 6)
                attendance.setStatus("Half-day");
            else
                attendance.setStatus("Present");

            return repository.save(attendance);
        });
    }

    public List<Attendance> getAll() {
        return repository.findAllByOrderByInTimeDesc();
    }

    public List<Attendance> getByEmployeeId(String employeeId) {
        return repository.findByEmployeeIdOrderByInTimeDesc(employeeId);
    }

    public List<Attendance> getTodayAttendance() {
        return repository.findByDate(LocalDate.now());
    }

    public List<Attendance> getOpenAttendance(String employeeId) {
        return repository.findOpenAttendanceByEmployeeId(employeeId);
    }

    public List<Attendance> getByDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return repository.findByInTimeBetweenOrderByInTimeDesc(start, end);
    }

    public List<Attendance> getByEmployeeAndDateRange(String employeeId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        return repository.findByEmployeeIdAndInTimeBetweenOrderByInTimeDesc(employeeId, start, end);
    }

    public List<Attendance> getByEmployeeIds(List<String> employeeIds) {
        return repository.findByEmployeeIdInOrderByInTimeDesc(employeeIds);
    }  */


    public AttendanceDTO clockOutByAttendanceId(Long attendanceId,  LocalDateTime now) {
       // LocalDateTime now = LocalDateTime.now();

        System.out.println("🔴 [Clock-Out Triggered] Time: " + now);
        System.out.println("🆔 Attendance ID: " + attendanceId);

        // 1. Fetch attendance
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance not found for ID: " + attendanceId));

        // 2. Find active clock session
        ClockSession activeSession = attendance.getClockSessions().stream()
                .filter(cs -> cs.getOutTime() == null)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No active clock session found"));

        // 3. Set clock session outTime
        activeSession.setOutTime(now);
        System.out.println("⏹️ ClockSession outTime set to: " + now);

        // 4. Update Attendance outTime (last closed session)
        attendance.setOutTime(now);
        System.out.println("📅 Attendance outTime set to: " + now);

        // 5. Update attendance status
        updateAttendanceStatus(attendance);

        // 6. Save attendance
        Attendance saved = attendanceRepository.save(attendance);
        System.out.println("💾 Attendance saved with clock-out. ID: " + saved.getId());

        return toAttendanceDTO(saved);
    }

    public void endCurrentDay(String employeeId,  LocalDateTime now) {
        System.out.println("🔔 [End Day Triggered] for employee ID: " + employeeId);

        Attendance latestAttendance = attendanceRepository
                .findTopByEmployeeIdOrderByInTimeDesc(employeeId)
                .orElseThrow(() -> new RuntimeException("❌ No attendance record found for employee ID: " + employeeId));

        System.out.println("📄 Latest Attendance ID: " + latestAttendance.getId() + ", Status: " + latestAttendance.getStatus());

        if (latestAttendance.isEndDay()) {
            System.out.println("ℹ️ Attendance already marked as end of day for ID: " + latestAttendance.getId());
            return;
        }

      //  LocalDateTime now = LocalDateTime.now();
        System.out.println("🕒 Current Time: " + now);

        // Optionally mark the last ClockSession outTime
        List<ClockSession> sessions = latestAttendance.getClockSessions();
        if (!sessions.isEmpty()) {
            ClockSession lastSession = sessions.get(sessions.size() - 1);
            System.out.println("🔍 Found " + sessions.size() + " session(s). Last session ID: " + lastSession.getId());

            if (lastSession.getOutTime() == null) {
                lastSession.setOutTime(now);
                System.out.println("🕑 ClockSession outTime set to: " + lastSession.getOutTime());
            } else {
                System.out.println("✅ Last session already has outTime: " + lastSession.getOutTime());
            }
        } else {
            System.out.println("⚠️ No clock sessions found for this attendance.");
        }

        latestAttendance.setOutTime(now);
        System.out.println("📅 Attendance outTime set to: " + now);

        latestAttendance.setEndDay(true);
        System.out.println("🔒 Marked endDay = true for attendance ID: " + latestAttendance.getId());

        attendanceRepository.save(latestAttendance);
        System.out.println("💾 Attendance saved successfully. ID: " + latestAttendance.getId());
        System.out.println("✅ Attendance ended for the day.");
    }



    public List<AttendanceDTO> getByDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Attendance> attendanceList = attendanceRepository
                .findByInTimeBetweenOrderByInTimeDesc(start, end);

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service:getByDateRange] Converted to " + dtoList.size() + " DTOs");
        return dtoList;
    }

    public List<AttendanceDTO> getByEmployeeAndDateRange(String employeeId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Attendance> attendanceList = attendanceRepository
                .findByEmployeeIdAndInTimeBetweenOrderByInTimeDesc(employeeId, start, end);

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service:getByEmployeeAndDateRange] Converted to " + dtoList.size() + " DTOs");
        return dtoList;
    }

    public List<AttendanceDTO> getByEmployeeIds(List<String> employeeIds) {
        List<Attendance> attendanceList = attendanceRepository
                .findByEmployeeIdInOrderByInTimeDesc(employeeIds);

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service:getByEmployeeIds] Converted to " + dtoList.size() + " DTOs");
        return dtoList;
    }

    public List<AttendanceDTO> getAll() {
        List<Attendance> attendanceList = attendanceRepository.findAllByOrderByInTimeDesc();

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service:getAll] Converted " + dtoList.size() + " records to DTOs");

        return dtoList;
    }

    public List<AttendanceDTO> getByEmployeeId(String employeeId) {
        List<Attendance> attendanceList = attendanceRepository.findByEmployeeIdOrderByInTimeDesc(employeeId);

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service:getByEmployeeId] Converted " + dtoList.size() + " records to DTOs for employeeId: " + employeeId);

        return dtoList;
    }

    public List<AttendanceDTO> getOpenAttendance(String employeeId) {
        List<Attendance> attendanceList = attendanceRepository.findOpenAttendanceByEmployeeId(employeeId);

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        System.out.println("🔁 [Service:getByEmployeeId] Converted " + dtoList.size() + " records to DTOs for employeeId: " + employeeId);

        return dtoList;
    }

    public List<AttendanceDTO> getTodayAttendance() {
        List<Attendance> attendanceList = attendanceRepository.findByDate(LocalDate.now());

        List<AttendanceDTO> dtoList = attendanceList.stream()
                .map(this::toAttendanceDTO)
                .collect(Collectors.toList());

        return dtoList;
    }





}