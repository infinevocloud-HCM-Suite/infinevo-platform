package com.phegondev.usersmanagementsystem.dto;

public class UserSummaryDTO {
	
	  private String email;
	  private String empId;
	  private String name;
	  
	  
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getEmpId() {
		return empId;
	}
	public void setEmpId(String empId) {
		this.empId = empId;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public UserSummaryDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	public UserSummaryDTO(String email, String empId, String name) {
		super();
		this.email = email;
		this.empId = empId;
		this.name = name;
	}
	@Override
	public String toString() {
		return "UserSummaryDTO [email=" + email + ", empId=" + empId + ", name=" + name + "]";
	}
	  
	  

}
