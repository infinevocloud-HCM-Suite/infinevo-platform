package com.phegondev.usersmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.phegondev.usersmanagementsystem.entity.useraccess.UserActionMapping;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "ourusers")
//@Data
public class OurUsers implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(unique = true)
    private String email;
    
    private String name;
    private String password;
    private String city;
    // private String role;

	@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
private List<Role> roles = new ArrayList<>();


    @Column(name = "emp_id", unique = true)
    private String empId;
    
    @Column(name = "blacklisted_token")
    private String blacklistedToken;

    @OneToOne
    @JoinColumn(name = "employee_id", referencedColumnName = "id") 
    // private personal personal;
    private Employee employee;
    
	@OneToMany(mappedBy="user" , cascade = CascadeType.ALL)
	private List<UserActionMapping> actionList = new ArrayList<>();
	
	@Column(name = "is_non_blocked")
	private boolean isNonBlocked = true;

	public boolean getIsNonBlocked() {
		return isNonBlocked;
	}


	public void setIsNonBlocked(boolean isNonBlocked) {
		this.isNonBlocked = isNonBlocked;
	}






	public String getEmployeeId() {
        // String e_id=this.personal.getEmpId();
        // Long p_id=this.personal.getId();
        if (this.employee != null && this.employee.getPersonal() != null) {
            return this.employee.getPersonal().getEmpId();
        }
        return this.empId;
    }
    
    
    
    
    

    public List<UserActionMapping> getActionList() {
		return actionList;
	}






	public void setActionList(List<UserActionMapping> actionList) {
		this.actionList = actionList;
	}


	@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    List<GrantedAuthority> authorities = new ArrayList<>();
    if (roles != null) {
        for (Role r : roles) {
            authorities.add(new SimpleGrantedAuthority(r.getRoleName()));
        }
    }
    return authorities;
}


    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isNonBlocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public List<Role> getRoles() {
    return roles;
}

public void setRoles(List<Role> roles) {
    this.roles = roles;
}

// Convenience methods for backward compatibility
public String getRole() {
    if (roles != null && !roles.isEmpty()) {
        return roles.get(0).getRoleName(); // return the first role as primary
    }
    return null;
}

public void setRole(String roleName) {
    if (roleName != null) {
        Role role = new Role();
        role.setRoleName(roleName);
        this.roles = List.of(role); // create a single-item list
    } else {
        this.roles = new ArrayList<>();
    }
}



	public String getEmpId() {
		return empId;
	}

	public void setEmpId(String empId) {
		this.empId = empId;
	}

	public String getBlacklistedToken() {
		return blacklistedToken;
	}

	public void setBlacklistedToken(String blacklistedToken) {
		this.blacklistedToken = blacklistedToken;
	}

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}

    // ✅ New helper method for multi-role support
public List<String> getRoleNames() {
    List<String> roleNames = new ArrayList<>();
    if (roles != null && !roles.isEmpty()) {
        for (Role role : roles) {
            if (role != null && role.getRoleName() != null) {
                roleNames.add(role.getRoleName());
            }
        }
    }
    return roleNames;
}

	@Override
public String toString() {
    return "OurUsers [id=" + id + ", email=" + email + ", name=" + name + ", password=" + password + ", city="
            + city + ", roles=" + roles + ", empId=" + empId + ", blacklistedToken=" + blacklistedToken + "]";
}

public String getSupervisorId() {
    return "OurUsers [id=" + id + ", email=" + email + ", name=" + name + ", password=" + password + ", city="
            + city + ", roles=" + roles + ", empId=" + empId + ", blacklistedToken=" + blacklistedToken + "]";
}


	
	
	
	
	
    
    
}