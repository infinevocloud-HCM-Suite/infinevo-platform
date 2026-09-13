package com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit trail for income-tax slab edits.
 *
 * One row is written every time an admin saves a {@link TaxSlabMaster} regime
 * from the "TaxSlab Regim" tab. We snapshot the FULL slab JSON before and after
 * the change so the exact configuration can always be reconstructed / compared.
 *
 * NOTE: income-tax slabs are GLOBAL config (no organizationId on TaxSlabMaster),
 * so this audit table is also global and is keyed only by the slab master id.
 */
@Entity
@Table(name = "taxSlabMasterHistory")
public class TaxSlabMasterHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK-by-value to TaxSlabMaster.id that was edited. */
    private Long taxSlabMasterId;

    /** Copied from the master at edit time for easy reading of the audit log. */
    private String financialYear;
    private String taxRegime; // OLD or NEW

    /** Full slab array BEFORE the change (null for the very first INSERT). */
    @Column(columnDefinition = "json")
    private String oldSlabJson;

    /** Full slab array AFTER the change. */
    @Column(columnDefinition = "json")
    private String newSlabJson;

    /** INSERT (first time) or UPDATE. */
    private String actionType;

    /** userId pulled from the JWT of whoever saved the change. */
    private String changedBy;

    private LocalDateTime changedAt;

    // ----- getters / setters -----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaxSlabMasterId() { return taxSlabMasterId; }
    public void setTaxSlabMasterId(Long taxSlabMasterId) { this.taxSlabMasterId = taxSlabMasterId; }

    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }

    public String getTaxRegime() { return taxRegime; }
    public void setTaxRegime(String taxRegime) { this.taxRegime = taxRegime; }

    public String getOldSlabJson() { return oldSlabJson; }
    public void setOldSlabJson(String oldSlabJson) { this.oldSlabJson = oldSlabJson; }

    public String getNewSlabJson() { return newSlabJson; }
    public void setNewSlabJson(String newSlabJson) { this.newSlabJson = newSlabJson; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
