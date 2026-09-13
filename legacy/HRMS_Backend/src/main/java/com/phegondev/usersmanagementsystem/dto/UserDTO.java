package com.phegondev.usersmanagementsystem.dto;

import java.util.List;


public class UserDTO {
	  
	private Integer userId;
    private String email;    
    private String name;
    private String city;
    // private String role;

	private List<String> roles;

    private String empId;
	private boolean isNonBlocked;

	public boolean getIsNonBlocked() {
		return isNonBlocked;
	}


	public void setIsNonBlocked(boolean isNonBlocked) {
		this.isNonBlocked = isNonBlocked;
	}

    
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
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
public String getRole() {
    if (roles != null && !roles.isEmpty()) {
        return roles.get(0); // return first role as primary
    }
    return null;
}

public void setRole(String role) {
    if (role != null) {
        this.roles = List.of(role); // store as single-item list
    } else {
        this.roles = null;
    }
}


	public String getEmpId() {
		return empId;
	}
	public void setEmpId(String empId) {
		this.empId = empId;
	}


	public Integer getUserId() {
		return this.userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	
	
    public UserDTO() {
		super();
		// TODO Auto-generated constructor stub
	}
	public UserDTO(Integer userId, String email, String name, String city, List<String> roles, String empId) {
        this.userId = userId;
		this.email = email;
        this.name = name;
        this.city = city;
        this.roles = roles;
        this.empId = empId;
    }

		public UserDTO(Integer userId, String email, String name, String city, List<String> roles, String empId,boolean isNonBlocked) {
        this.userId = userId;
		this.email = email;
        this.name = name;
        this.city = city;
        this.roles = roles;
        this.empId = empId;
		this.isNonBlocked = isNonBlocked;
    }

	
	@Override
public String toString() {
    return "UserDTO [email=" + email + ", name=" + name + ", city=" + city + ", roles=" + roles 
           + ", empId=" + empId + ", isNonBlocked=" + isNonBlocked + "]";
}

    
    
    
    

}
