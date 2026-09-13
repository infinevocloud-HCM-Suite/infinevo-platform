package com.itsdev.payroll.entity.EmployeeITDeclaration.poi;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee_poi_document")
public class EmployeePOIDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poi_item_id", nullable = false)
    private EmployeePOIItem poiItem;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "document_url", length = 2000)
    private String documentUrl;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_time", updatable = false)
    private LocalDateTime uploadedTime;

    /* ================= GETTERS & SETTERS ================= */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmployeePOIItem getPoiItem() { return poiItem; }
    public void setPoiItem(EmployeePOIItem poiItem) { this.poiItem = poiItem; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getUploadedTime() { return uploadedTime; }
}
