package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_inv_let_out_property_detail")
public class EmployeeInvLetOutPropertyDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "let_out_property_id")
    private EmployeeInvLetOutProperty letOutProperty;

    @Column(name = "type")
    private String type;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "name_of_lender")
    private String nameOfLender;

    @Column(name = "pan_of_lender")
    private String panOfLender;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public EmployeeInvLetOutPropertyDetail() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EmployeeInvLetOutProperty getLetOutProperty() {
        return letOutProperty;
    }

    public void setLetOutProperty(EmployeeInvLetOutProperty letOutProperty) {
        this.letOutProperty = letOutProperty;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNameOfLender() {
        return nameOfLender;
    }

    public void setNameOfLender(String nameOfLender) {
        this.nameOfLender = nameOfLender;
    }

    public String getPanOfLender() {
        return panOfLender;
    }

    public void setPanOfLender(String panOfLender) {
        this.panOfLender = panOfLender;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
