package com.phegondev.usersmanagementsystem.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.phegondev.usersmanagementsystem.enumuration.DocumentType;

import jakarta.persistence.*;

@Entity
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String fileName;
    private String fileType;

//	@Lob
//	@Column(name = "document_content", columnDefinition = "MEDIUMBLOB")
//	@JsonIgnore
	//private byte[] documentContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
	@JsonBackReference
    private Employee employee;


	@Column(name = "company_index")
	private Integer companyIndex;
	
	private String fileUrl;

	private String publicId;
	


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


	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(DocumentType documentType) {
		this.documentType = documentType;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileType() {
		return fileType;
	}

	public void setFileType(String fileType) {
		this.fileType = fileType;
	}


	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}



	// Getter and Setter
	public Integer getCompanyIndex() {
		return companyIndex;
	}

	public void setCompanyIndex(Integer companyIndex) {
		this.companyIndex = companyIndex;
	}

}

