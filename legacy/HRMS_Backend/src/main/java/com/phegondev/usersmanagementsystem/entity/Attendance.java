package com.phegondev.usersmanagementsystem.entity;

import com.phegondev.usersmanagementsystem.enumuration.AttendanceStatus;
import jakarta.persistence.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "in_time", nullable = false)
    private LocalDateTime inTime;

    @Column(name = "out_time")
    private LocalDateTime outTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private AttendanceStatus status = AttendanceStatus.ABSENT;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

	@OneToMany(mappedBy = "attendance", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ClockSession> clockSessions = new ArrayList<>();

	@Column(name = "end_day", nullable = false)
	private boolean endDay = false;

	public boolean isEndDay() {
		return endDay;
	}

	public void setEndDay(boolean endDay) {
		this.endDay = endDay;
	}







	public List<ClockSession> getClockSessions() {
		return clockSessions;
	}

	public void setClockSessions(List<ClockSession> clockSessions) {
		this.clockSessions = clockSessions;
	}



	public Long getDurationMinutes() {
        if (outTime == null) return null;
        return Duration.between(inTime, outTime).toMinutes();
    }



	public Long getId() {
		return id;
	}



	public void setId(Long id) {
		this.id = id;
	}



	public String getEmployeeId() {
		return employeeId;
	}



	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}



	public String getEmployeeName() {
		return employeeName;
	}



	public void setEmployeeName(String employeeName) {
		this.employeeName = employeeName;
	}



	public LocalDateTime getInTime() {
		return inTime;
	}



	public void setInTime(LocalDateTime inTime) {
		this.inTime = inTime;
	}



	public LocalDateTime getOutTime() {
		return outTime;
	}



	public void setOutTime(LocalDateTime outTime) {
		this.outTime = outTime;
	}



	public AttendanceStatus getStatus() {
		return status;
	}



	public void setStatus(AttendanceStatus status) {
		this.status = status;
	}



	public LocalDateTime getCreatedAt() {
		return createdAt;
	}



	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}



	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}



	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public void  addClockSession(ClockSession session) {
		if (session != null) {
			clockSessions.add(session);
			session.setAttendance(this);
		}
	}

	public void removeClockSession(ClockSession session) {
		if (session != null && clockSessions.remove(session)) {
			session.setAttendance(null);
		}
	}




	@Override
	public String toString() {
		return "Attendance [id=" + id + ", employeeId=" + employeeId + ", employeeName=" + employeeName + ", inTime="
				+ inTime + ", outTime=" + outTime + ", status=" + status + ", createdAt=" + createdAt + ", updatedAt="
				+ updatedAt + "]";
	}
    
    
}