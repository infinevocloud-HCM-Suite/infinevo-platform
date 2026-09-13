package com.phegondev.usersmanagementsystem.service.useraccess;
import java.util.List;

import com.phegondev.usersmanagementsystem.dto.useraccess.ActionIdNameDTO;
import com.phegondev.usersmanagementsystem.dto.useraccess.RoleDTO;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;


public interface RoleService {
	
	  void createRole(RoleDTO roleDTO);
	  List<RoleDTO> getAllRoles();
	  RoleDTO getRoleById(Long roleId);
	  RoleDTO updateRole(Long roleId, RoleDTO roleDTO);
	  public boolean mapActionToRole(Long roleId, List<Long> actionList);
	  public List<ActionIdNameDTO> getActionsByRole(Long roleId);
	  public void deleteRoleById(Long id);
	  void createRoleIfNotExist();
Role getRoleEntityByName(String roleName);
Role findByRoleName(String roleName);
List<String> findActionNamesByRoleId(Long roleId);
List<String> findActionAliasesByRoleId(Long roleId);


}
