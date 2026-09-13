
package com.phegondev.usersmanagementsystem.entity;

import com.phegondev.usersmanagementsystem.enumuration.OvertimeStatus;
import jakarta.persistence.*;
        import lombok.*;

        import java.time.LocalDateTime;

@Entity
@Table(name = "overtime_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OvertimeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "project", nullable = false)
    private String project;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_status", nullable = false)
    private OvertimeStatus managerStatus = OvertimeStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "hr_status", nullable = false)
    private OvertimeStatus hrStatus = OvertimeStatus.PENDING;

    @Column(name = "manager_updated_at")
    private LocalDateTime managerUpdatedAt;

    @Column(name = "hr_updated_at")
    private LocalDateTime hrUpdatedAt;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "comp_off_created")
    private boolean compOffCreated = false;

    @Column(name = "manager_comment", length = 1000)
    private String managerComment;

    @Column(name = "hr_comment", length = 1000)
    private String hrComment;

    @Column(name = "manager_employee_id", nullable = false)
    private String managerEmployeeId;


    public boolean getCompOffCreated() {
        return compOffCreated;
    }

    public void setCompOffCreated(boolean compOffCreated) {
        this.compOffCreated = compOffCreated;
    }

    // Calculated field for duration in hours
    @Transient
    public double getDurationHours() {
        if (startTime == null || endTime == null) return 0;
        return java.time.Duration.between(startTime, endTime).toMinutes() / 60.0;
    }
}