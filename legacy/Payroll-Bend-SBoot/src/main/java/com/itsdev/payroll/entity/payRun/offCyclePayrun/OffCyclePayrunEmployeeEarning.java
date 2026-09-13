package com.itsdev.payroll.entity.payRun.offCyclePayrun;


import com.itsdev.payroll.entity.salarycomponents.Earning;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "off_cycle_payrun_employee_earnings")
public class OffCyclePayrunEmployeeEarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link back to employee payrun record
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "off_cycle_employee_id", nullable = false)
    private OffCyclePayrunEmployee offCyclePayrunEmployee;

    // Link to earning master
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "earning_id", nullable = false)
    private Earning earning;

    // Amount / days for this earning
    private BigDecimal amount;
    private Integer days;

    // Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffCyclePayrunEmployee getOffCyclePayrunEmployee() {
        return offCyclePayrunEmployee;
    }

    public void setOffCyclePayrunEmployee(OffCyclePayrunEmployee offCyclePayrunEmployee) {
        this.offCyclePayrunEmployee = offCyclePayrunEmployee;
    }

    public Earning getEarning() {
        return earning;
    }

    public void setEarning(Earning earning) {
        this.earning = earning;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }
}

