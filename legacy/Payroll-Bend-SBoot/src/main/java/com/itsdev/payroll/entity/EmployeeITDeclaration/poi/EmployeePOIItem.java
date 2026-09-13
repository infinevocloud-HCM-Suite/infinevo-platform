package com.itsdev.payroll.entity.EmployeeITDeclaration.poi;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "employee_poi_item")
public class EmployeePOIItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_id", nullable = false)
    private EmployeeProofOfInvestment proofOfInvestment;

    @Column(name = "investment_type")
    private String investmentType;

    @Column(name = "section6a_item_id")
    private Long section6aItemId;

    @Column(name = "declared_amount", precision = 15, scale = 2)
    private BigDecimal declaredAmount = BigDecimal.ZERO;

    @Column(name = "actual_amount", precision = 15, scale = 2)
    private BigDecimal actualAmount = BigDecimal.ZERO;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayRunStatus status;

    @Column(name = "admin_comment", length = 1000)
    private String adminComment;

    @Column(name = "item_id_external")
    private String itemIdExternal;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @OneToMany(mappedBy = "poiItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeePOIDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "poiItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeePOIItemComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "poiItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeePOIPropertyDetail> propertyDetails = new ArrayList<>();

    @Column(name = "admin_adjusted")
    private Boolean adminAdjusted = false;

    @Column(name = "amount_adjusted_by")
    private String amountAdjustedBy;

    @Column(name = "amount_adjusted_date")
    private LocalDateTime amountAdjustedDate;

    /* ================= GETTERS & SETTERS ================= */

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EmployeeProofOfInvestment getProofOfInvestment() {
        return proofOfInvestment;
    }

    public void setProofOfInvestment(EmployeeProofOfInvestment proofOfInvestment) {
        this.proofOfInvestment = proofOfInvestment;
    }

    public String getInvestmentType() {
        return investmentType;
    }

    public void setInvestmentType(String investmentType) {
        this.investmentType = investmentType;
    }

    public Long getSection6aItemId() {
        return section6aItemId;
    }

    public void setSection6aItemId(Long section6aItemId) {
        this.section6aItemId = section6aItemId;
    }

    public BigDecimal getDeclaredAmount() {
        return declaredAmount;
    }

    public void setDeclaredAmount(BigDecimal declaredAmount) {
        this.declaredAmount = declaredAmount;
    }

    public BigDecimal getActualAmount() {
        return actualAmount;
    }

    public void setActualAmount(BigDecimal actualAmount) {
        this.actualAmount = actualAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayRunStatus status) {
        this.status = status;
    }

    public String getAdminComment() {
        return adminComment;
    }

    public void setAdminComment(String adminComment) {
        this.adminComment = adminComment;
    }

    public String getItemIdExternal() {
        return itemIdExternal;
    }

    public void setItemIdExternal(String itemIdExternal) {
        this.itemIdExternal = itemIdExternal;
    }

    public List<EmployeePOIDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<EmployeePOIDocument> documents) {
        this.documents = documents;
    }

    public List<EmployeePOIItemComment> getComments() {
        return comments;
    }

    public void setComments(List<EmployeePOIItemComment> comments) {
        this.comments = comments;
    }

    public void addComment(EmployeePOIItemComment comment) {
        comments.add(comment);
        comment.setPoiItem(this);
    }

    public void removeComment(EmployeePOIItemComment comment) {
        comments.remove(comment);
        comment.setPoiItem(null);
    }

    public Boolean getAdminAdjusted() {
        return adminAdjusted;
    }

    public void setAdminAdjusted(Boolean adminAdjusted) {
        this.adminAdjusted = adminAdjusted;
    }

    public String getAmountAdjustedBy() {
        return amountAdjustedBy;
    }

    public void setAmountAdjustedBy(String amountAdjustedBy) {
        this.amountAdjustedBy = amountAdjustedBy;
    }

    public LocalDateTime getAmountAdjustedDate() {
        return amountAdjustedDate;
    }

    public void setAmountAdjustedDate(LocalDateTime amountAdjustedDate) {
        this.amountAdjustedDate = amountAdjustedDate;
    }

    public List<EmployeePOIPropertyDetail> getPropertyDetails() {
        return propertyDetails;
    }

    public void setPropertyDetails(List<EmployeePOIPropertyDetail> propertyDetails) {
        this.propertyDetails = propertyDetails;
    }
}
