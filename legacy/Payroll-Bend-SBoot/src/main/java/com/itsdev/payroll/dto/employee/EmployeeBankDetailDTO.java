package com.itsdev.payroll.dto.employee;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;

public class EmployeeBankDetailDTO {

    private String id;
    private String employeeId;   // 🔗 Link to BasicDetails

    @Column(name = "payment_mode", nullable = false)
    @NotNull(message = "Payment mode must be selected")
    private String paymentMode;  // e.g., banktransfer, cheque, cash

    // ---- Bank Account Info ----
    private String accountHolderName;
    private String bankName;
    private String ifscCode;
    private String bankAccountNumber;   // hashed or plain depending on flow
    private String bankAccountType;     // savings, current, salary, etc.

    private String organizationId;  // for multi-org support

    // ✅ Getters and Setters
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeId() {
        return employeeId;
    }
    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
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

    public String getOrganizationId() {
        return organizationId;
    }
    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }
}

