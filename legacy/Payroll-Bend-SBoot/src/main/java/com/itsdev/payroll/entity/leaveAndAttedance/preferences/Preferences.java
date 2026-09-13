package com.itsdev.payroll.entity.leaveAndAttedance.preferences;


import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "preferences")
public class Preferences {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    @Column(name = "end_day", nullable = false)
    private String endDay;

    @Column(name = "payroll_report_day", nullable = false)
    private String payrollReportDay;

    @Column(name = "is_leave_encashment_enabled", nullable = false)
    private Boolean leaveEncashmentEnabled;


    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getEndDay() {
        return endDay;
    }

    public void setEndDay(String endDay) {
        this.endDay = endDay;
    }

    public String getPayrollReportDay() {
        return payrollReportDay;
    }

    public void setPayrollReportDay(String payrollReportDay) {
        this.payrollReportDay = payrollReportDay;
    }

    public Boolean getLeaveEncashmentEnabled() {
        return leaveEncashmentEnabled;
    }

    public void setLeaveEncashmentEnabled(Boolean leaveEncashmentEnabled) {
        this.leaveEncashmentEnabled = leaveEncashmentEnabled;
    }
}

