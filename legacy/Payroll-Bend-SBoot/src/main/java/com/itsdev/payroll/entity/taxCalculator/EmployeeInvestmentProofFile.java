package com.itsdev.payroll.entity.taxCalculator;


import com.itsdev.payroll.entity.employee.BasicDetails;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
//@Table(name = "employee_investment_proof_file")
public class EmployeeInvestmentProofFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proof_id", nullable = false)
    private EmployeeInvestmentProof proof;

    @Column(name = "declared_item_name", nullable = false)
    private String declaredItemName;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public EmployeeInvestmentProof getProof() {
		return proof;
	}

	public void setProof(EmployeeInvestmentProof proof) {
		this.proof = proof;
	}

	public String getDeclaredItemName() {
		return declaredItemName;
	}

	public void setDeclaredItemName(String declaredItemName) {
		this.declaredItemName = declaredItemName;
	}

	public String getDocumentType() {
		return documentType;
	}

	public void setDocumentType(String documentType) {
		this.documentType = documentType;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileUrl() {
		return fileUrl;
	}

	public void setFileUrl(String fileUrl) {
		this.fileUrl = fileUrl;
	}

	public String getPublicId() {
		return publicId;
	}

	public void setPublicId(String publicId) {
		this.publicId = publicId;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
    
    
    
}
