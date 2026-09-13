package com.phegondev.usersmanagementsystem.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import lombok.Data;

import java.util.List;
import java.util.Map;

//@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReqRes {

    private int statusCode;
    private String error;
    private String message;
    private String token;
    private String refreshToken;
    private String expirationTime;
    private String name;
    private String city;
    // private String role;

	private List<String> roles;

    private String email;
    private String empId;
    private String password;
    private OurUsers ourUsers;
    private List<OurUsers> ourUsersList;
    private Map<String, Object> employeeData;
    private List<String> actions;
    private List<UserDTO> userDTOList;
    private UserDTO user;
    

	private String activeRole;

public String getActiveRole() {
    return activeRole;
}

public void setActiveRole(String activeRole) {
    this.activeRole = activeRole;
}

    
    
    

    
    
	public UserDTO getUser() {
		return user;
	}
	public void setUser(UserDTO user) {
		this.user = user;
	}
	public List<UserDTO> getUserDTOList() {
		return userDTOList;
	}
	public void setUserDTOList(List<UserDTO> userDTOList) {
		this.userDTOList = userDTOList;
	}
	public List<String> getActions() {
		return actions;
	}
	public void setActions(List<String> actions) {
		this.actions = actions;
	}
	public int getStatusCode() {
		return statusCode;
	}
	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}
	public String getError() {
		return error;
	}
	public void setError(String error) {
		this.error = error;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	public String getRefreshToken() {
		return refreshToken;
	}
	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}
	public String getExpirationTime() {
		return expirationTime;
	}
	public void setExpirationTime(String expirationTime) {
		this.expirationTime = expirationTime;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getCity() {
		return city;
	}
	public void setCity(String city) {
		this.city = city;
	}
	public List<String> getRoles() {
    return roles;
}

public void setRoles(List<String> roles) {
    this.roles = roles;
}

// Convenience methods for backward compatibility
@JsonIgnore
public String getRole() {
    if (roles != null && !roles.isEmpty()) {
        return roles.get(0); // return the first role as primary
    }
    return null;
}

@JsonIgnore
public void setRole(String role) {
    // ✅ Instead of overwriting roles, just ensure backward compatibility
    if (role != null) {
        if (this.roles == null || this.roles.isEmpty()) {
            this.roles = new java.util.ArrayList<>();
        }
        if (!this.roles.contains(role)) {
            this.roles.add(role);
        }
    }
}



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
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public OurUsers getOurUsers() {
		return ourUsers;
	}
	public void setOurUsers(OurUsers ourUsers) {
		this.ourUsers = ourUsers;
	}
	public List<OurUsers> getOurUsersList() {
		return ourUsersList;
	}
	public void setOurUsersList(List<OurUsers> ourUsersList) {
		this.ourUsersList = ourUsersList;
	}
	public Map<String, Object> getEmployeeData() {
		return employeeData;
	}
	public void setEmployeeData(Map<String, Object> employeeData) {
		this.employeeData = employeeData;
	}


	// Add to ReqRes.java
private String oldPassword;
private String newPassword;

// Add getters and setters
public String getOldPassword() {
    return oldPassword;
}

public void setOldPassword(String oldPassword) {
    this.oldPassword = oldPassword;
}

public String getNewPassword() {
    return newPassword;
}

public void setNewPassword(String newPassword) {
    this.newPassword = newPassword;
}
	@Override
public String toString() {
    return "ReqRes [statusCode=" + statusCode + ", error=" + error + ", message=" + message + ", token=" + token
            + ", refreshToken=" + refreshToken + ", expirationTime=" + expirationTime + ", name=" + name + ", city="
            + city + ", roles=" + roles + ", email=" + email + ", empId=" + empId + ", password=" + password
            + ", actions=" + actions + "]";
}

    
    
}