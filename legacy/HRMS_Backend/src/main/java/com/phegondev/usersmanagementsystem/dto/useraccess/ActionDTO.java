package com.phegondev.usersmanagementsystem.dto.useraccess;

import jakarta.validation.constraints.NotBlank;

public class ActionDTO {
	
	  private long actionId;
	  
	  @NotBlank(message = "Action name is required")
	  private String actionName;
	  
	  private String alias;
	   
	  @NotBlank(message = "Description is required")
	  private String description;

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

	public ActionDTO() {
		super();
		// TODO Auto-generated constructor stub
	}

	public ActionDTO(long actionId, String actionName,
			 String alias,
			String description) {
		super();
		this.actionId = actionId;
		this.actionName = actionName;
		this.alias = alias;
		this.description = description;
	}
	  
	  
	  

}
