package com.itsdev.payroll.dto.employee;

import java.util.ArrayList;
import java.util.List;


public class CtcStructureDTO {

    private Double ctc; 
    private Double monthlySalary;

    private List<EmployeeEarningDTO> earnings = new ArrayList<>();
    private List<EmployeeBenefitDTO> benefits = new ArrayList<>();
    private List<EmployeeReimbursementDTO> reimbursements = new ArrayList<>();

	private List<EpfComponentDTO> epfComponents;
	private List<EsiComponentDTO> esiComponents;
    
    private String employeeNumber;
    private String firstName;
    private String middleName;
    private String lastName;
    
    private String department;
    private String workLocation;
    private String designation; 
    
    private String dateOfJoining;

	public Double getCtc() {
		return ctc;
	}

	public void setCtc(Double ctc) {
		this.ctc = ctc;
	}

	public Double getMonthlySalary() {
		return monthlySalary;
	}

	public void setMonthlySalary(Double monthlySalary) {
		this.monthlySalary = monthlySalary;
	}

	public List<EmployeeEarningDTO> getEarnings() {
		return earnings;
	}

	public void setEarnings(List<EmployeeEarningDTO> earnings) {
		this.earnings = earnings;
	}

	public List<EmployeeBenefitDTO> getBenefits() {
		return benefits;
	}

	public void setBenefits(List<EmployeeBenefitDTO> benefits) {
		this.benefits = benefits;
	}

	public List<EmployeeReimbursementDTO> getReimbursements() {
		return reimbursements;
	}

	public void setReimbursements(List<EmployeeReimbursementDTO> reimbursements) {
		this.reimbursements = reimbursements;
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

	public String getDepartment() {
		return department;
	}

	public void setDepartment(String department) {
		this.department = department;
	}

	public String getWorkLocation() {
		return workLocation;
	}

	public void setWorkLocation(String workLocation) {
		this.workLocation = workLocation;
	}

	public String getDesignation() {
		return designation;
	}

	public void setDesignation(String designation) {
		this.designation = designation;
	}

	public String getDateOfJoining() {
		return dateOfJoining;
	}

	public void setDateOfJoining(String dateOfJoining) {
		this.dateOfJoining = dateOfJoining;
	}


	public List<EpfComponentDTO> getEpfComponents() {
		return epfComponents;
	}

	public void setEpfComponents(List<EpfComponentDTO> epfComponents) {
		this.epfComponents = epfComponents;
	}

	public List<EsiComponentDTO> getEsiComponents() {
		return esiComponents;
	}

	public void setEsiComponents(List<EsiComponentDTO> esiComponents) {
		this.esiComponents = esiComponents;
	}
}
