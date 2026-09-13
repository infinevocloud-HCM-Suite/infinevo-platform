package com.phegondev.usersmanagementsystem.entity;


import jakarta.persistence.*;
import lombok.Data;
import java.time.Year;

@Entity
@Table(name = "leave_balances")
@Data
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "year", nullable = false)
    private int year = Year.now().getValue();

    @Column(name = "annual_allotted", nullable = false)
    private int annualAllotted = 24; // Default annual leave days

    @Column(name = "annual_used", nullable = false)
    private int annualUsed = 0;

    @Column(name = "sick_allotted", nullable = false)
    private int sickAllotted = 12; // Default sick leave days

    @Column(name = "sick_used", nullable = false)
    private int sickUsed = 0;

    @Column(name = "casual_allotted", nullable = false)
    private int casualAllotted = 8; // Default casual leave days

    @Column(name = "casual_used", nullable = false)
    private int casualUsed = 0;

    @Column(name = "maternity_allotted", nullable = false)
    private int maternityAllotted = 90; // For eligible employees

    @Column(name = "maternity_used", nullable = false)
    private int maternityUsed = 0;

    @Column(name = "paternity_allotted", nullable = false)
    private int paternityAllotted = 10; // For eligible employees

    @Column(name = "paternity_used", nullable = false)
    private int paternityUsed = 0;

    // Calculate remaining leaves
    public int getAnnualRemaining() {
        return annualAllotted - annualUsed;
    }

    public int getSickRemaining() {
        return sickAllotted - sickUsed;
    }

    public int getCasualRemaining() {
        return casualAllotted - casualUsed;
    }

    public int getMaternityRemaining() {
        return maternityAllotted - maternityUsed;
    }

    public int getPaternityRemaining() {
        return paternityAllotted - paternityUsed;
    }
}