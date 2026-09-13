package com.phegondev.usersmanagementsystem.entity.useraccess;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;


@Entity
@Table(name="role")
public class Role {
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="role_id")
	private long roleId;

	@Column(name="role_name", unique = true)
	private String roleName;
	
	@Column(name="description")
	private String description;
	
	@ManyToMany
	@JoinTable(name="role_action_mapping",joinColumns={@JoinColumn(name="role_id",referencedColumnName="role_id")},
		    inverseJoinColumns={@JoinColumn(name="action_id",referencedColumnName="action_id")})
	private List<Action> actionList = new ArrayList<>();
	
	
	@OneToMany(mappedBy="role" , cascade = CascadeType.ALL)
	private List<UserActionMapping> userList = new ArrayList<>();



	public long getRoleId() {
		return roleId;
	}

	public void setRoleId(long roleId) {
		this.roleId = roleId;
	}

	public String getRoleName() {
		return roleName;
	}

	public void setRoleName(String roleName) {
		this.roleName = roleName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public List<Action> getActionList() {
		return actionList;
	}

	public void setActionList(List<Action> actionList) {
		this.actionList = actionList;
	}


	public List<UserActionMapping> getUserList() {
		return userList;
	}

	public void setUserList(List<UserActionMapping> userList) {
		this.userList = userList;
	}
	
	

}
