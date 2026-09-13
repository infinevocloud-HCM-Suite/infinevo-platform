package com.itsdev.payroll.entity.payRun.oneTimePayout;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "one_time_payout")
public class OneTimePayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to earning (one-time component)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "earning_id", nullable = false)
    private Earning earning;

    // Link to employee
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private BasicDetails employee; // remove nullable=false

    // Link to organization
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // One-time amount
    @Column(nullable = true)
    private BigDecimal earningAmount;

    // Payout date
    @Column(nullable = false)
    private LocalDate payDate;

    // Optional: store applied taxes (JSON or mapped table later)
    @Column(columnDefinition = "json")
    private String taxes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();


    // Number of days (for leave encashment case)
    @Column(nullable = true)
    private Integer days;

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Earning getEarning() {
        return earning;
    }

    public void setEarning(Earning earning) {
        this.earning = earning;
    }

    public BasicDetails getEmployee() {
        return employee;
    }

    public void setEmployee(BasicDetails employee) {
        this.employee = employee;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public BigDecimal getEarningAmount() {
        return earningAmount;
    }

    public void setEarningAmount(BigDecimal earningAmount) {
        this.earningAmount = earningAmount;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public String getTaxes() {
        return taxes;
    }

    public void setTaxes(String taxes) {
        this.taxes = taxes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
