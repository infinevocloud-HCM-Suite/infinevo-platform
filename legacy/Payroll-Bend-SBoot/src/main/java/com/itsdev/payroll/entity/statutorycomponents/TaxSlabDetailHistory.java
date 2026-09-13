package com.itsdev.payroll.entity.statutorycomponents;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;


@Entity
public class TaxSlabDetailHistory {
	
	    @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;

	    private String taxId;   

	    private Double oldStartAmount;
	    private Double oldEndAmount;
	    private Double oldPayAmount;
	    
	    private Double startAmount;
	    private Double endAmount;
	    private Double payAmount;
	    private String organizationId;


	    private String actionType; // INSERT, UPDATE, DELETE

	    private LocalDateTime changedAt;
	    private String changedBy; // optional
	    
	    
	    
	    
		public Double getOldStartAmount() {
			return oldStartAmount;
		}
		public void setOldStartAmount(Double oldStartAmount) {
			this.oldStartAmount = oldStartAmount;
		}
		public Double getOldEndAmount() {
			return oldEndAmount;
		}
		public void setOldEndAmount(Double oldEndAmount) {
			this.oldEndAmount = oldEndAmount;
		}
		public Double getOldPayAmount() {
			return oldPayAmount;
		}
		public void setOldPayAmount(Double oldPayAmount) {
			this.oldPayAmount = oldPayAmount;
		}
		public Long getId() {
			return id;
		}
		public void setId(Long id) {
			this.id = id;
		}
		public String getTaxId() {
			return taxId;
		}
		public void setTaxId(String taxId) {
			this.taxId = taxId;
		}
		public Double getStartAmount() {
			return startAmount;
		}
		public void setStartAmount(Double startAmount) {
			this.startAmount = startAmount;
		}
		public Double getEndAmount() {
			return endAmount;
		}
		public void setEndAmount(Double endAmount) {
			this.endAmount = endAmount;
		}
		public Double getPayAmount() {
			return payAmount;
		}
		public void setPayAmount(Double payAmount) {
			this.payAmount = payAmount;
		}
		public String getOrganizationId() {
			return organizationId;
		}
		public void setOrganizationId(String organizationId) {
			this.organizationId = organizationId;
		}
		public String getActionType() {
			return actionType;
		}
		public void setActionType(String actionType) {
			this.actionType = actionType;
		}
		public LocalDateTime getChangedAt() {
			return changedAt;
		}
		public void setChangedAt(LocalDateTime changedAt) {
			this.changedAt = changedAt;
		}
		public String getChangedBy() {
			return changedBy;
		}
		public void setChangedBy(String changedBy) {
			this.changedBy = changedBy;
		}
	    
	    

}
