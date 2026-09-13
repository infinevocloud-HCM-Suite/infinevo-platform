package com.itsdev.payroll.entity.payRun.offCyclePayrun;


import com.itsdev.payroll.entity.salarycomponents.Deduction;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "off_cycle_payrun_employee_deductions")
public class OffCyclePayrunEmployeeDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link back to employee payrun record
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "off_cycle_employee_id", nullable = false)
    private OffCyclePayrunEmployee offCyclePayrunEmployee;

    // Link to deduction master
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deduction_id", nullable = false)
    private Deduction deduction;

    // Deduction amount
    private BigDecimal amount;

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

    public Deduction getDeduction() {
        return deduction;
    }

    public void setDeduction(Deduction deduction) {
        this.deduction = deduction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}

