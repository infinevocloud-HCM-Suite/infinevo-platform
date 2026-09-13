package com.phegondev.usersmanagementsystem.service.useraccess;

import java.util.List;

import com.phegondev.usersmanagementsystem.dto.useraccess.ActionDTO;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.useraccess.Action;



public interface ActionService {
	
	  void createAction(ActionDTO actionDTO);
	  List<ActionDTO> getAllActions();
	  ActionDTO getActionById(Long actionId);
	  ActionDTO updateAction(Long actionId, ActionDTO actionDTO);
	  public void deleteActionById(Long id);
	  void createActionIfNotExist();
	  void mapDefaultActionsToRoles();
	  void mapAllActionsToAdminUser(OurUsers adminUser);
	  void mapDefaultActionsToUser(OurUsers user);

}
