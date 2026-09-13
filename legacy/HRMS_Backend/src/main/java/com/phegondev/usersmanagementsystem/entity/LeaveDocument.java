package com.phegondev.usersmanagementsystem.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "leave_documents")
public class LeaveDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_request_id", nullable = false, unique = true)
    private LeaveRequests leaveRequest;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

  //  @Lob
  ////  @Column(name = "file_data", columnDefinition = "MEDIUMBLOB", nullable = false)
 //   private byte[] fileData; // Or use file path/url instead
    
	private String fileUrl;

	private String publicId;


    @CreationTimestamp
    private LocalDateTime uploadedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LeaveRequests getLeaveRequest() {
        return leaveRequest;
    }

    public void setLeaveRequest(LeaveRequests leaveRequest) {
        this.leaveRequest = leaveRequest;
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

	public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}

