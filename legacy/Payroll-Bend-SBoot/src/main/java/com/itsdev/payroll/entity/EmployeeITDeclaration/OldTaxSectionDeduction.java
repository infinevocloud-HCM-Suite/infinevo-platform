package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "old_tax_section_deduction")
public class OldTaxSectionDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sectionCode;   // 80C, 80D, etc.

    @Column(nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_tax_id", nullable = false)
    private OldTaxCalculation oldTaxCalculation;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSectionCode() {
		return sectionCode;
	}

	public void setSectionCode(String sectionCode) {
		this.sectionCode = sectionCode;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public OldTaxCalculation getOldTaxCalculation() {
		return oldTaxCalculation;
	}

	public void setOldTaxCalculation(OldTaxCalculation oldTaxCalculation) {
		this.oldTaxCalculation = oldTaxCalculation;
	}

    // getters & setters
    
    
}
