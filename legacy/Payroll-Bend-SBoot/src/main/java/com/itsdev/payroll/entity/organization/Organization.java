package com.itsdev.payroll.entity.organization;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.itsdev.payroll.entity.*;
import com.itsdev.payroll.entity.claimsanddeclarations.FBP;
import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.claimsanddeclarations.ReimbursementClaim;
import com.itsdev.payroll.entity.salarycomponents.Benefit;
import com.itsdev.payroll.entity.salarycomponents.Deduction;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;
import com.itsdev.payroll.entity.statutorycomponents.Epf;
import com.itsdev.payroll.entity.statutorycomponents.Esi;
import com.itsdev.payroll.entity.statutorycomponents.ProfessionalTax;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;


@Entity
@Table(name = "organization")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organizationId", unique = true, nullable = false)
    private String organizationId;

    @Column(name = "organizationName", nullable = false)
    private String organizationName;

    @Column(name = "businessLocation")
    private String businessLocation;

    @Column(name = "industry")
    private String industry;

    @Column(name = "addressLine1")
    private String addressLine1;

    @Column(name = "addressLine2")
    private String addressLine2;

    @Column(name = "state")
    private String state;

    @Column(name = "city")
    private String city;

    @Column(name = "hasRunPayroll")
    private Boolean hasRunPayroll;

    @Column(name = "timezone")
    private String timezone;

	@Column(name = "pinCode")
	private String pinCode;
    
    @Column(name = "createdBy")
    private String createdBy;

    @Column(name = "updatedBy")
    private String updatedBy;

    @Column(name = "createdDate")
    @CreationTimestamp
    private LocalDateTime createdDate;

    @Column(name = "updatedDate")
    @UpdateTimestamp
    private LocalDateTime updatedDate;

	@Column(name="isOrgActive" ,nullable = false)
	private Boolean isOrgActive = false;

	@Column(name="isDeleted", nullable = false)
	private Boolean isDeleted = false;

	@Column(name = "email")
	private String email;
	
	

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<WorkLocation> workLocations = new ArrayList<>();;

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Department> departments = new ArrayList<>();

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Designation> designations = new ArrayList<>();

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private IncomeTaxDetails incomeTaxDetails;

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private PaySchedule paySchedule;

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Epf epf;

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private Esi esi;

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProfessionalTax> professionalTaxes = new ArrayList<>();

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Earning> earnings = new ArrayList<>();

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Deduction> deductions = new ArrayList<>();

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private List<Benefit> benefits = new ArrayList<>();

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Reimbursement> reimbursements = new ArrayList<>();

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private FBP fbp;

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private ReimbursementClaim reimbursementClaim;

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private IncomeTaxDeclaration incomeTaxDeclaration;

	@OneToOne(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private ProofOfInvestment proofOfInvestment;

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<OrganizationRole> roles = new HashSet<>();

	@OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserInvitation> users = new ArrayList<>();



	
	@Column(name = "fileName")
	private String fileName;

	@Column(name = "fileUrl")
	private String fileUrl;

	@Column(name = "filePublicId")
	private String filePublicId;


	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(String organizationId) {
		this.organizationId = organizationId;
	}

	public String getOrganizationName() {
		return organizationName;
	}

	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}

	public String getBusinessLocation() {
		return businessLocation;
	}

	public void setBusinessLocation(String businessLocation) {
		this.businessLocation = businessLocation;
	}

	public String getIndustry() {
		return industry;
	}

	public void setIndustry(String industry) {
		this.industry = industry;
	}

	public String getAddressLine1() {
		return addressLine1;
	}

	public void setAddressLine1(String addressLine1) {
		this.addressLine1 = addressLine1;
	}

	public String getAddressLine2() {
		return addressLine2;
	}

	public void setAddressLine2(String addressLine2) {
		this.addressLine2 = addressLine2;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public Boolean getHasRunPayroll() {
		return hasRunPayroll;
	}

	public void setHasRunPayroll(Boolean hasRunPayroll) {
		this.hasRunPayroll = hasRunPayroll;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getPinCode() { return pinCode; }
	public void setPinCode(String pinCode) { this.pinCode = pinCode; }

	public String getcreatedBy() {
		return createdBy;
	}

	public void setcreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getupdatedBy() {
		return updatedBy;
	}

	public void setupdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

	public LocalDateTime getcreatedDate() {
		return createdDate;
	}

	public void setcreatedDate(LocalDateTime createdDate) {
		this.createdDate = createdDate;
	}

	public LocalDateTime getupdatedDate() {
		return updatedDate;
	}

	public void setupdatedDate(LocalDateTime updatedDate) {
		this.updatedDate = updatedDate;
	}

	public Boolean getIsOrgActive() {
		return isOrgActive;
	}

	public void setIsOrgActive(Boolean isOrgActive) {
		this.isOrgActive = isOrgActive;
	}

	public Boolean getIsDeleted() {
		return isDeleted;
	}

	public void setIsDeleted(Boolean isDeleted) {
		this.isDeleted = isDeleted;
	}


	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
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

	public String getFilePublicId() {
		return filePublicId;
	}

	public void setFilePublicId(String filePublicId) {
		this.filePublicId = filePublicId;
	}

    public List<WorkLocation> getWorkLocations() { return workLocations; }
    public void setWorkLocations(List<WorkLocation> workLocations) { this.workLocations = workLocations; }

	public List<Department> getDepartments() {
		return departments;
	}

	public void setDepartments(List<Department> departments) {
		this.departments = departments;
	}

	public List<Designation> getDesignations() {
		return designations;
	}

	public void setDesignations(List<Designation> designations) {
		this.designations = designations;
	}

	public IncomeTaxDetails getIncomeTaxDetails() {
		return incomeTaxDetails;
	}
	public void setIncomeTaxDetails(IncomeTaxDetails incomeTaxDetails) {
		this.incomeTaxDetails = incomeTaxDetails;
	}

	public PaySchedule getPaySchedule() {
		return paySchedule;
	}
	public void setPaySchedule(PaySchedule paySchedule) {
		this.paySchedule = paySchedule;
	}

	public Epf getEpf() { return epf; }
	public void setEpf(Epf epf) { this.epf = epf; }

	public Esi getEsi() { return esi; }
	public void setEsi(Esi esi) { this.esi = esi; }

	public List<ProfessionalTax> getProfessionalTaxes() {
		return professionalTaxes;
	}
	public void setProfessionalTaxes(List<ProfessionalTax> professionalTaxes) {
		this.professionalTaxes = professionalTaxes;
	}

	public List<Earning> getEarnings() {
		return earnings;
	}
	public void setEarnings(List<Earning> earnings) {
		this.earnings = earnings;
	}

	public List<Deduction> getDeductions() {
		return deductions;
	}
	public void setDeductions(List<Deduction> deductions) {
		this.deductions = deductions;
	}

	public List<Benefit> getBenefits() {
		return benefits;
	}
	public void setBenefits(List<Benefit> benefits) {
		this.benefits = benefits;
	}

    public List<Reimbursement> getReimbursements() {
        return reimbursements;
    }
    public void setReimbursements(List<Reimbursement> reimbursements) {
        this.reimbursements = reimbursements;
    }

	public FBP getFbp() { return fbp; }
	public void setFbp(FBP fbp) { this.fbp = fbp; }

	public ReimbursementClaim getReimbursementClaim() { return reimbursementClaim; }
	public void setReimbursementClaim(ReimbursementClaim reimbursementClaim) { this.reimbursementClaim = reimbursementClaim; }

	public IncomeTaxDeclaration getIncomeTaxDeclaration() { return incomeTaxDeclaration; }
	public void setIncomeTaxDeclaration(IncomeTaxDeclaration incomeTaxDeclaration) { this.incomeTaxDeclaration = incomeTaxDeclaration; }

	public ProofOfInvestment getProofOfInvestment() { return proofOfInvestment; }
	public void setProofOfInvestment(ProofOfInvestment proofOfInvestment) { this.proofOfInvestment = proofOfInvestment; }

	public Set<OrganizationRole> getRoles() { return roles; }
	public void setRoles(Set<OrganizationRole> roles) { this.roles = roles; }

	public List<UserInvitation> getUsers() {
		return users;
	}

	public void setUsers(List<UserInvitation> users) {
		this.users = users;
	}
}
