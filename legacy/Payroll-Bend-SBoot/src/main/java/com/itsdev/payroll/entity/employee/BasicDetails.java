package com.itsdev.payroll.entity.employee;

import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.List;

import com.itsdev.payroll.entity.organization.Department;
import com.itsdev.payroll.entity.organization.Designation;
import com.itsdev.payroll.entity.organization.WorkLocation;

@Entity
@Table(name = "employee")
public class BasicDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String employeeId;

    @Column(name = "employee_number", nullable = false, unique = true)
    @NotBlank(message = "Employee number cannot be blank")
    private String employeeNumber;

    @Column(name = "first_name", nullable = false)
    @NotBlank(message = "First name is required")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name", nullable = false)
    @NotBlank(message = "Last name is required")
    private String lastName;

    @Column(name = "gender")
    private String gender;

    @Column(name = "date_of_joining", nullable = false)
    @NotBlank(message = "Date of joining is required")
    private String dateOfJoining; // Can be changed to LocalDate if needed

    @Column(name = "hr_user")
    private String hrUser;



    // @Column(name = "department_id")
    // private String departmentId;
    //
    // @Column(name = "designation_id")
    // private String designationId;
    //
    // @Column(name = "work_location_id")
    // private String workLocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", referencedColumnName = "departmentId", nullable = true)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id", referencedColumnName = "designationId", nullable = true)
    private Designation designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_location_id", referencedColumnName = "workLocationId", nullable = true)
    private WorkLocation workLocation;

    @Column(name = "employee_status")
    private String employeeStatus;

    @Column(name = "is_portal_enabled")
    private Boolean isPortalEnabled;

    @Column(name = "is_eligible_for_pf")
    private Boolean isEligibleForPf;

    @Column(name = "is_eligible_for_pt")
    private Boolean isEligibleForPt;

    @Column(name = "is_eligible_for_lwf")
    private Boolean isEligibleForLwf;

    @Column(name = "is_director")
    private Boolean director;

    @Column(name = "is_eligible_for_eps")
    private Boolean eligibleForEps;

    @Column(name = "can_contribute_to_eps_on_higher_wages")
    private Boolean canContributeToEpsOnHigherWages;

    @Column(name = "esi_number", length = 10)
    @Size(min = 10, max = 10, message = "ESI Number must be exactly 10 characters")
    @Pattern(regexp = "\\d{10}", message = "ESI Number must contain only digits")
    private String esiNumber;

    @Column(name = "mobile")
    @Pattern(regexp = "\\d{10}", message = "Mobile number must be 10 digits")
    private String mobile;

    @Column(name = "work_mail")
    @Email(message = "Invalid email format")
    private String workMail;

    @Column(name = "pf_account_number")
    private String pfAccountNumber;

    @Column(name = "uan", length = 12)
    @Size(min = 12, max = 12, message = "UAN must be exactly 12 characters")
    @Pattern(regexp = "\\d{12}", message = "UAN must contain only digits")
    private String uan;

    @ElementCollection
    @CollectionTable(name = "employee_tags", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "tag")
    private List<String> tags;

    @Column(name = "amount_in_percentage")
    private Double amountInPercentage; // nullable: when null, treat as fixed

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", nullable = false)
    private Organization organization;



    // BasicDetails.java

    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private EmployeePersonalDetail personalDetail;

    @Column(name = "is_eligible_for_esi")
    private Boolean eligibleForEsi;

    @Column(name = "employee_unique_id", unique = true)
    private String employeeUniqueId;

    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private EmployeeBankDetail bankDetail;

//    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
//    private CtcStructure salaryStructure;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;


    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<CtcStructure> ctcStructures;


    public List<CtcStructure> getCtcStructures() {
        return ctcStructures;
    }

    public void setCtcStructures(List<CtcStructure> ctcStructures) {
        this.ctcStructures = ctcStructures;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

//    public CtcStructure getSalaryStructure() {
//        return salaryStructure;
//    }
//
//    public void setSalaryStructure(CtcStructure salaryStructure) {
//        this.salaryStructure = salaryStructure;
//    }

    public EmployeeBankDetail getBankDetail() {
        return bankDetail;
    }

    public void setBankDetail(EmployeeBankDetail bankDetail) {
        this.bankDetail = bankDetail;
    }

    public String getEmployeeUniqueId() {
        return employeeUniqueId;
    }

    public void setEmployeeUniqueId(String employeeUniqueId) {
        this.employeeUniqueId = employeeUniqueId;
    }

    public Boolean getDirector() {
        return director;
    }

    public void setDirector(Boolean director) {
        this.director = director;
    }

    public Boolean getEligibleForEps() {
        return eligibleForEps;
    }

    public void setEligibleForEps(Boolean eligibleForEps) {
        this.eligibleForEps = eligibleForEps;
    }

    public Boolean getCanContributeToEpsOnHigherWages() {
        return canContributeToEpsOnHigherWages;
    }

    public void setCanContributeToEpsOnHigherWages(Boolean canContributeToEpsOnHigherWages) {
        this.canContributeToEpsOnHigherWages = canContributeToEpsOnHigherWages;
    }

    public Double getAmountInPercentage() {
        return amountInPercentage;
    }

    public void setAmountInPercentage(Double amountInPercentage) {
        this.amountInPercentage = amountInPercentage;
    }

    public EmployeePersonalDetail getPersonalDetail() {
        return personalDetail;
    }

    public void setPersonalDetail(EmployeePersonalDetail personalDetail) {
        this.personalDetail = personalDetail;
    }

    public Boolean getEligibleForEsi() {
        return eligibleForEsi;
    }

    public void setEligibleForEsi(Boolean eligibleForEsi) {
        this.eligibleForEsi = eligibleForEsi;
    }

    public Boolean getPortalEnabled() {
        return isPortalEnabled;
    }

    public void setPortalEnabled(Boolean portalEnabled) {
        isPortalEnabled = portalEnabled;
    }

    public Boolean getEligibleForPf() {
        return isEligibleForPf;
    }

    public void setEligibleForPf(Boolean eligibleForPf) {
        isEligibleForPf = eligibleForPf;
    }

    public Boolean getEligibleForPt() {
        return isEligibleForPt;
    }

    public void setEligibleForPt(Boolean eligibleForPt) {
        isEligibleForPt = eligibleForPt;
    }

    public Boolean getEligibleForLwf() {
        return isEligibleForLwf;
    }

    public void setEligibleForLwf(Boolean eligibleForLwf) {
        isEligibleForLwf = eligibleForLwf;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    // ✅ Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(String dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    // public String getDepartmentId() { return departmentId; }
    // public void setDepartmentId(String departmentId) { this.departmentId =
    // departmentId; }
    //
    // public String getDesignationId() { return designationId; }
    // public void setDesignationId(String designationId) { this.designationId =
    // designationId; }
    //
    // public String getWorkLocationId() { return workLocationId; }
    // public void setWorkLocationId(String workLocationId) { this.workLocationId =
    // workLocationId; }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public WorkLocation getWorkLocation() {
        return workLocation;
    }

    public void setWorkLocation(WorkLocation workLocation) {
        this.workLocation = workLocation;
    }

    public String getEmployeeStatus() {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus) {
        this.employeeStatus = employeeStatus;
    }

    public Boolean getIsPortalEnabled() {
        return isPortalEnabled;
    }

    public void setIsPortalEnabled(Boolean isPortalEnabled) {
        this.isPortalEnabled = isPortalEnabled;
    }

    public Boolean getIsEligibleForPf() {
        return isEligibleForPf;
    }

    public void setIsEligibleForPf(Boolean isEligibleForPf) {
        this.isEligibleForPf = isEligibleForPf;
    }

    public Boolean getIsEligibleForPt() {
        return isEligibleForPt;
    }

    public void setIsEligibleForPt(Boolean isEligibleForPt) {
        this.isEligibleForPt = isEligibleForPt;
    }

    public Boolean getIsEligibleForLwf() {
        return isEligibleForLwf;
    }

    public void setIsEligibleForLwf(Boolean isEligibleForLwf) {
        this.isEligibleForLwf = isEligibleForLwf;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getWorkMail() {
        return workMail;
    }

    public void setWorkMail(String workMail) {
        this.workMail = workMail;
    }

    public String getPfAccountNumber() {
        return pfAccountNumber;
    }

    public void setPfAccountNumber(String pfAccountNumber) {
        this.pfAccountNumber = pfAccountNumber;
    }

    public String getUan() {
        return uan;
    }

    public void setUan(String uan) {
        this.uan = uan;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getHrUser() {
        return hrUser;
    }

    public void setHrUser(String hrUser) {
        this.hrUser = hrUser;
    }

    public String getEsiNumber() {
        return esiNumber;
    }

    public void setEsiNumber(String esiNumber) {
        this.esiNumber = esiNumber;
    }
}
