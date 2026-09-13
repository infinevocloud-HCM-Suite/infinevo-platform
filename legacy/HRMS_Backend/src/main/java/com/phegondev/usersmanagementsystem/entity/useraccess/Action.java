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
@Table(name = "action")
public class Action {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "action_id")
	private long actionId;

	@Column(name = "action_name", unique = true)
	private String actionName;
	
	@Column(name = "alias")
	private String alias;

	@Column(name = "description")
	private String description;

	@ManyToMany(cascade = CascadeType.ALL, targetEntity = Role.class)
	@JoinTable(name = "role_action_mapping", joinColumns = { @JoinColumn(name = "action_id") }, inverseJoinColumns = { @JoinColumn(name = "role_id") })
	private List<Role> roleList = new ArrayList<>();


	@OneToMany(mappedBy="action" , cascade = CascadeType.ALL)
	private List<UserActionMapping> userList = new ArrayList<>();

	public long getActionId() {
		return actionId;
	}


	public void setActionId(long actionId) {
		this.actionId = actionId;
	}


	public String getActionName() {
		return actionName;
	}


	public void setActionName(String actionName) {
		this.actionName = actionName;
	}


	public String getAlias() {
		return alias;
	}


	public void setAlias(String alias) {
		this.alias = alias;
	}


	public String getDescription() {
		return description;
	}


	public void setDescription(String description) {
		this.description = description;
	}


	public List<Role> getRoleList() {
		return roleList;
	}


	public void setRoleList(List<Role> roleList) {
		this.roleList = roleList;
	}


	public List<UserActionMapping> getUserList() {
		return userList;
	}


	public void setUserList(List<UserActionMapping> userList) {
		this.userList = userList;
	}


	public Action() {
		super();
		// TODO Auto-generated constructor stub
	}


	public Action(String actionName, String alias, String description) {
		super();
		this.actionName = actionName;
		this.alias = alias;
		this.description = description;
	}
	
	
	

}
