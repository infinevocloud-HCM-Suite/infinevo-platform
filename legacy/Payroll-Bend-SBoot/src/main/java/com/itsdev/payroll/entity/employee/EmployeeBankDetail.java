package com.itsdev.payroll.entity.employee;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "employee_bank_detail")
public class EmployeeBankDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Payment mode: banktransfer, cash, cheque etc.
    @Column(name = "payment_mode", nullable = false)
    @NotNull(message = "Payment mode must be selected")
    private String paymentMode;

    // --- Bank account details ---
    @Column(name = "account_holder_name", nullable = true)  // allow null
    private String accountHolderName;

    @Column(name = "bank_name", nullable = true)
    private String bankName;

    @Column(name = "ifsc_code", nullable = true, length = 20)
    private String ifscCode;

    @Column(name = "bank_account_number", nullable = true, length = 512, unique = true)
    private String bankAccountNumber;

    @Column(name = "bank_account_type", nullable = true)
    private String bankAccountType;


    // --- Relations ---
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private BasicDetails employee;   // 🔑 link to employeeId

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public String getBankAccountNumber() {
        return bankAccountNumber;
    }

    public void setBankAccountNumber(String bankAccountNumber) {
        this.bankAccountNumber = bankAccountNumber;
    }

    public String getBankAccountType() {
        return bankAccountType;
    }

    public void setBankAccountType(String bankAccountType) {
        this.bankAccountType = bankAccountType;
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
}

