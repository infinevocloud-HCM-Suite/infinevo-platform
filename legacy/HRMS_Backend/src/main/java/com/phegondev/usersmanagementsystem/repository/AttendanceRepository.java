package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    // Get all attendance sorted by inTime descending
    List<Attendance> findAllByOrderByInTimeDesc();

    // Get attendance by employee ID sorted by inTime
    List<Attendance> findByEmployeeIdOrderByInTimeDesc(String employeeId);

    // Get attendance between date range
    List<Attendance> findByInTimeBetweenOrderByInTimeDesc(LocalDateTime start, LocalDateTime end);

    // Get attendance by employee ID and date range
    List<Attendance> findByEmployeeIdAndInTimeBetweenOrderByInTimeDesc(
        String employeeId, LocalDateTime start, LocalDateTime end);

    // Get today's attendance for employee
    @Query("SELECT a FROM Attendance a WHERE a.employeeId = :employeeId AND DATE(a.inTime) = CURRENT_DATE")
    List<Attendance> findTodayAttendanceByEmployeeId(@Param("employeeId") String employeeId);

    // Get open attendance (no outTime)
    @Query("SELECT a FROM Attendance a WHERE a.employeeId = :employeeId AND a.outTime IS NULL")
    List<Attendance> findOpenAttendanceByEmployeeId(@Param("employeeId") String employeeId);

    // Get attendance by specific date
    @Query("SELECT a FROM Attendance a WHERE DATE(a.inTime) = :date")
    List<Attendance> findByDate(@Param("date") LocalDate date);

    List<Attendance> findByEmployeeIdInOrderByInTimeDesc(List<String> employeeIds);

    Optional<Attendance> findTopByEmployeeIdOrderByInTimeDesc(String employeeId);
}