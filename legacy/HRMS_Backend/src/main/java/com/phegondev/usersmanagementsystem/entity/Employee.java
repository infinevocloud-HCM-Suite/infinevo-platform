package com.phegondev.usersmanagementsystem.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
//@Data
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "personal_id", referencedColumnName = "id")
    @JsonManagedReference
    @NotNull(message = "Personal information is required")
    private personal personal;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "identification_id", referencedColumnName = "id")
    @JsonManagedReference
    @NotNull(message = "Identification information is required")
    private Identification identification;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "work_id", referencedColumnName = "id")
    @JsonManagedReference
    @NotNull(message = "Work information is required")
    private Work work;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "contact_id", referencedColumnName = "id")
    @JsonManagedReference
    @NotNull(message = "Contact information is required")
    private Contact contact;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "report_id", referencedColumnName = "id")
    @JsonManagedReference
    private Report report;

    @OneToOne(mappedBy = "employee")
    @JsonBackReference
    private OurUsers ourUser;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonManagedReference
	private List<EmployeeDocument> documents = new ArrayList<>();


    public List<EmployeeDocument> getDocuments() {
		return documents;
	}

	public void setDocuments(List<EmployeeDocument> documents) {
		this.documents = documents;
	}

	public String getEmpId() {
        
        if (this.personal == null) {
            return null;
        }
        return this.personal.getEmpId();
    }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public personal getPersonal() {
		return personal;
	}

	public void setPersonal(personal personal) {
		this.personal = personal;
	}

	public Identification getIdentification() {
		return identification;
	}

	public void setIdentification(Identification identification) {
		this.identification = identification;
	}

	public Work getWork() {
		return work;
	}

	public void setWork(Work work) {
		this.work = work;
	}

	public Contact getContact() {
		return contact;
	}

	public void setContact(Contact contact) {
		this.contact = contact;
	}

	public Report getReport() {
		return report;
	}

	public void setReport(Report report) {
		this.report = report;
	}

	public OurUsers getOurUser() {
		return ourUser;
	}

	public void setOurUser(OurUsers ourUser) {
		this.ourUser = ourUser;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
    
    
}