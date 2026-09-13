package com.itsdev.payroll.entity;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "paySchedule")
public class PaySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="payScheduleId", nullable = false, unique = true)
    private String payScheduleId;

    @Column(name="payScheduleType", nullable = false)
    private String payScheduleType = "monthly";

    @Column(name="payDay", nullable = false)
    private String payDay;

    @Column(name="payPeriodStartDate", nullable = false)
    private LocalDate payPeriodStartDate;

    @Column(name="payPeriodEndDate")
    private LocalDate payPeriodEndDate;

    @Column(name="payDate", nullable = false)
    private LocalDate payDate;

    @ElementCollection
    @CollectionTable(name = "payScheduleWorkingDays", joinColumns = @JoinColumn(name = "payScheduleId"))
    @Column(name = "workingDays")
    private List<String> workingDays;

    @Column(name="noOfWorkingDays", nullable = false)
    private Integer noOfWorkingDays;

    @Column(name="includeHolidays", nullable = false)
    private Boolean includeHolidays = true;

    @Column(name="includeWeekends", nullable = false)
    private Boolean includeWeekends = true;

    @Column(name="workingDaysCalculationType")
    private String workingDaysCalculationType;

    @OneToOne
    @JoinColumn(name = "organizationId", nullable = false, unique = true)
    private Organization organization;


    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getPayScheduleId() {
        return payScheduleId;
    }
    public void setPayScheduleId(String payScheduleId) {
        this.payScheduleId = payScheduleId;
    }

    public String getPayScheduleType() {
        return payScheduleType;
    }
    public void setPayScheduleType(String payScheduleType) {
        this.payScheduleType = payScheduleType;
    }

    public String getPayDay() {
        return payDay;
    }
    public void setPayDay(String payDay) {
        this.payDay = payDay;
    }

    public LocalDate getPayPeriodStartDate() {
        return payPeriodStartDate;
    }
    public void setPayPeriodStartDate(LocalDate payPeriodStartDate) {
        this.payPeriodStartDate = payPeriodStartDate;
    }

    public LocalDate getPayPeriodEndDate() {
        return payPeriodEndDate;
    }
    public void setPayPeriodEndDate(LocalDate payPeriodEndDate) {
        this.payPeriodEndDate = payPeriodEndDate;
    }

    public LocalDate getPayDate() {
        return payDate;
    }
    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public List<String> getWorkingDays() {
        return workingDays;
    }
    public void setWorkingDays(List<String> workingDays) {
        this.workingDays = workingDays;
    }

    public Integer getNoOfWorkingDays() {
        return noOfWorkingDays;
    }
    public void setNoOfWorkingDays(Integer noOfWorkingDays) {
        this.noOfWorkingDays = noOfWorkingDays;
    }

    public Boolean getIncludeHolidays() {
        return includeHolidays;
    }
    public void setIncludeHolidays(Boolean includeHolidays) {
        this.includeHolidays = includeHolidays;
    }

    public Boolean getIncludeWeekends() {
        return includeWeekends;
    }
    public void setIncludeWeekends(Boolean includeWeekends) {
        this.includeWeekends = includeWeekends;
    }

    public String getWorkingDaysCalculationType() {
        return workingDaysCalculationType;
    }
    public void setWorkingDaysCalculationType(String workingDaysCalculationType) {
        this.workingDaysCalculationType = workingDaysCalculationType;
    }

    public Organization getOrganization() {
        return organization;
    }
    public void setOrganization(Organization organization) {
        this.organization = organization;
    }
}
