package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.revision;



import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "old_tax_revision_section_deduction")
public class OldTaxRevisionSectionDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_code", nullable = false, length = 20)
    private String sectionCode;   // 80C, HRA, 80D, etc.

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    private OldTaxCalculationRevision revision;

    /* ================= GETTERS / SETTERS ================= */

    public Long getId() {
        return id;
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

    public OldTaxCalculationRevision getRevision() {
        return revision;
    }

    public void setRevision(OldTaxCalculationRevision revision) {
        this.revision = revision;
    }
}

