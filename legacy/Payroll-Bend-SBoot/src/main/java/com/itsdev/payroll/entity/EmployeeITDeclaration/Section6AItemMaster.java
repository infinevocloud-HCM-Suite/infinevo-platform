package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "section6a_item_master")
public class Section6AItemMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category")
    private String category;

    @Column(name = "category_formatted")
    private String categoryFormatted;

    @Column(name = "type")
    private String type;

    @Column(name = "type_formatted")
    private String typeFormatted;

    @Column(name = "max_limit", precision = 15, scale = 2)
    private BigDecimal maxLimit;

    @Column(name = "max_limit_formatted")
    private String maxLimitFormatted;

    @Column(name = "is_80c")
    private Boolean is80c = Boolean.FALSE;

    @Column(name = "is_80d")
    private Boolean is80d = Boolean.FALSE;

    @Column(name = "is_other_section")
    private Boolean isOtherSection = Boolean.FALSE;

    @Column(name = "is_active")
    private Boolean isActive = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public Section6AItemMaster() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategoryFormatted() {
        return categoryFormatted;
    }

    public void setCategoryFormatted(String categoryFormatted) {
        this.categoryFormatted = categoryFormatted;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeFormatted() {
        return typeFormatted;
    }

    public void setTypeFormatted(String typeFormatted) {
        this.typeFormatted = typeFormatted;
    }

    public BigDecimal getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(BigDecimal maxLimit) {
        this.maxLimit = maxLimit;
    }

    public String getMaxLimitFormatted() {
        return maxLimitFormatted;
    }

    public void setMaxLimitFormatted(String maxLimitFormatted) {
        this.maxLimitFormatted = maxLimitFormatted;
    }

    public Boolean getIs80c() {
        return is80c;
    }

    public void setIs80c(Boolean is80c) {
        this.is80c = is80c;
    }

    public Boolean getIs80d() {
        return is80d;
    }

    public void setIs80d(Boolean is80d) {
        this.is80d = is80d;
    }

    public Boolean getIsOtherSection() {
        return isOtherSection;
    }

    public void setIsOtherSection(Boolean otherSection) {
        isOtherSection = otherSection;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
