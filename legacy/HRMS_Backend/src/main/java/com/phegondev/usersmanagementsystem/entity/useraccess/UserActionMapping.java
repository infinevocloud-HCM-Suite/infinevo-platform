package com.phegondev.usersmanagementsystem.entity.useraccess;

import com.phegondev.usersmanagementsystem.entity.OurUsers;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.AssociationOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;



@Entity
@Table(name="user_action_mapping" )
@AssociationOverrides({@AssociationOverride(name="user",joinColumns=@JoinColumn(name="user_id")),		
					   @AssociationOverride(name="role",joinColumns=@JoinColumn(name="role_id")),
					   @AssociationOverride(name="action",joinColumns=@JoinColumn(name="action_id"))})
public class UserActionMapping{
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name = "mapping_id")
	private long mappingId;
	
	@ManyToOne
	private OurUsers user;
	

	
	@ManyToOne
	private Role role;
	
	@ManyToOne
	private Action action;



	public long getMappingId() {
		return mappingId;
	}

	public void setMappingId(long mappingId) {
		this.mappingId = mappingId;
	}

	public OurUsers getUser() {
		return user;
	}

	public void setUser(OurUsers user) {
		this.user = user;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}
	
	

}
