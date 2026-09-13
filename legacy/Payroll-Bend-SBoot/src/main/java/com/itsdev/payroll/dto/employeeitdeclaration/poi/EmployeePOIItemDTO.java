package com.itsdev.payroll.dto.employeeitdeclaration.poi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class EmployeePOIItemDTO {
	private Long id;
	private Long poiId;
	private String investmentType;
	private Long section6aItemId;
	private BigDecimal declaredAmount;
	private BigDecimal actualAmount;
	private BigDecimal approvedAmount;
	private String status;
	private String adminComment; // Legacy field - keep for backward compatibility
	private String itemIdExternal;
	private LocalDateTime createdTime;
	private LocalDateTime updatedTime;
	private List<EmployeePOIDocumentDTO> documents;
	private List<POIItemCommentDTO> comments; // NEW: Add comments list
	private List<POIPropertyDetailDTO> propertyDetails;

	// Optional: Helper fields
	private Integer commentCount;
	private Integer unresolvedCommentCount;

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getPoiId() {
		return poiId;
	}

	public void setPoiId(Long poiId) {
		this.poiId = poiId;
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

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
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

	public LocalDateTime getCreatedTime() {
		return createdTime;
	}

	public void setCreatedTime(LocalDateTime createdTime) {
		this.createdTime = createdTime;
	}

	public LocalDateTime getUpdatedTime() {
		return updatedTime;
	}

	public void setUpdatedTime(LocalDateTime updatedTime) {
		this.updatedTime = updatedTime;
	}

	public List<EmployeePOIDocumentDTO> getDocuments() {
		return documents;
	}

	public void setDocuments(List<EmployeePOIDocumentDTO> documents) {
		this.documents = documents;
	}

	public List<POIItemCommentDTO> getComments() {
		return comments;
	}

	public void setComments(List<POIItemCommentDTO> comments) {
		this.comments = comments;
	}

	public Integer getCommentCount() {
		return commentCount;
	}

	public void setCommentCount(Integer commentCount) {
		this.commentCount = commentCount;
	}

	public Integer getUnresolvedCommentCount() {
		return unresolvedCommentCount;
	}

	public void setUnresolvedCommentCount(Integer unresolvedCommentCount) {
		this.unresolvedCommentCount = unresolvedCommentCount;
	}

	public List<POIPropertyDetailDTO> getPropertyDetails() {
		return propertyDetails;
	}

	public void setPropertyDetails(List<POIPropertyDetailDTO> propertyDetails) {
		this.propertyDetails = propertyDetails;
	}
}