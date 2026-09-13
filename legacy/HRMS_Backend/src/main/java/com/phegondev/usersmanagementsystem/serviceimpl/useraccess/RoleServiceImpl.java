package com.phegondev.usersmanagementsystem.serviceimpl.useraccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.phegondev.usersmanagementsystem.dto.useraccess.ActionIdNameDTO;
import com.phegondev.usersmanagementsystem.dto.useraccess.RoleDTO;
import com.phegondev.usersmanagementsystem.entity.useraccess.Action;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;
import com.phegondev.usersmanagementsystem.exceptions.useraccess.RoleAlreadyExistsException;
import com.phegondev.usersmanagementsystem.exceptions.useraccess.RoleDeletionException;
import com.phegondev.usersmanagementsystem.exceptions.useraccess.RoleNotFoundException;
import com.phegondev.usersmanagementsystem.repository.useraccess.ActionRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.RoleRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.UserActionMappingRepository;
import com.phegondev.usersmanagementsystem.service.useraccess.RoleService;



@Service
@Transactional(rollbackFor = Exception.class)
public class RoleServiceImpl implements RoleService {

    private final RoleRepo roleRepository;
    private final ActionRepo actionRepository;
    private final UserActionMappingRepository userActionMappingRepository;

    public RoleServiceImpl(RoleRepo roleRepository, 
            ActionRepo actionRepository, UserActionMappingRepository userActionMappingRepository) {
     this.roleRepository = roleRepository;
     this.actionRepository = actionRepository;
     this.userActionMappingRepository = userActionMappingRepository;
    }

@Override
public Role getRoleEntityByName(String roleName) {
    return roleRepository.findByRoleName(roleName);
}

    @Override
    public void createRole(RoleDTO roleDTO) {
        if (roleRepository.existsByRoleName(roleDTO.getRoleName())) {
            throw new RoleAlreadyExistsException("Role already exists with name: " + roleDTO.getRoleName());
        }

        Role role = new Role();
        role.setRoleName(roleDTO.getRoleName());
        role.setDescription(roleDTO.getDescription());

        roleRepository.save(role);
    }

    @Override
    public List<RoleDTO> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleDTO(
                        role.getRoleId(),
                        role.getRoleName(),
                        role.getDescription()))
                .collect(Collectors.toList());
    }

    @Override
    public RoleDTO getRoleById(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found with id: " + roleId));
        return new RoleDTO(
                role.getRoleId(),
                role.getRoleName(),
                role.getDescription());
    }

    @Override
    public RoleDTO updateRole(Long roleId, RoleDTO roleDTO) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found with id: " + roleId));

        role.setRoleName(roleDTO.getRoleName());
        role.setDescription(roleDTO.getDescription());

        Role updatedRole = roleRepository.save(role);

        return new RoleDTO(
                updatedRole.getRoleId(),
                updatedRole.getRoleName(),
                updatedRole.getDescription());
    }


    @Override
    public boolean mapActionToRole(Long roleId, List<Long> actionList) {
        try {
    
        	Role role = roleRepository.findById(roleId)
        		    .orElseThrow(() -> new IllegalArgumentException("Role not found with ID: " + roleId));

            List<Action> actions = actionList.stream()
                .map(actionId -> actionRepository.findById(actionId)
                    .orElseThrow(() -> new IllegalArgumentException("Action not found with ID: " + actionId)))
                .collect(Collectors.toList());

            role.setActionList(actions);
            roleRepository.save(role);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
  
    @Override
    public List<ActionIdNameDTO> getActionsByRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found with ID: " + roleId));

        return role.getActionList().stream()
            .map(action -> new ActionIdNameDTO(action.getActionId(), action.getActionName()))
            .collect(Collectors.toList());
    }

	@Override
	public void deleteRoleById(Long id) {

		    System.out.println("Fetching Role with ID: " + id);
	        Role role = roleRepository.findById(id).orElseThrow(() -> 
	            new RoleNotFoundException("Role not found with ID: " + id));

	        System.out.println("Checking associations for Role ID: " + id);

	        List<String> associatedActions = roleRepository.findActioNamesByRoleId(id);
	        if (!associatedActions.isEmpty()) {
	            System.out.println("Associated Actions for Role ID " + id + ": " + associatedActions);
	        } else {
	            System.out.println("No associated actions for Role ID: " + id);
	        }

	      
	        List<String> associatedUsers = userActionMappingRepository.findUsernamesByRoleId(id);
	        if (!associatedUsers.isEmpty()) {
	            System.out.println("Associated Users for Role ID " + id + ": " + associatedUsers);
	        } else {
	            System.out.println("No associated users for Role ID: " + id);
	        }

	     
	        if (!associatedActions.isEmpty() ||   !associatedUsers.isEmpty()) {
	            String message = "Please remove mappings before deleting Role as it is associated with ";
	            if (!associatedActions.isEmpty()) message += "actions, ";	 
	            if (!associatedUsers.isEmpty()) message += "users, ";
	            message = message.substring(0, message.length() - 2) + ".";

	            System.out.println("Deletion blocked for Role ID " + id + ". " + message);
	            throw new RoleDeletionException(message, associatedActions, associatedUsers);
	        }

	        System.out.println("Deleting Role with ID: " + id);
	        roleRepository.delete(role);
	        System.out.println("Role deleted successfully for ID: " + id);
	        
		    }


	@Override
	public void createRoleIfNotExist() {
	    System.out.println("Checking and creating roles...");

	    if (!roleRepository.existsByRoleName("admin")) {
	        Role adminRole = new Role();
	        adminRole.setRoleName("admin");
	        adminRole.setDescription("Administrator role with all privileges");
	        roleRepository.save(adminRole);
	        System.out.println("ADMIN role created.");
	    }

	    if (!roleRepository.existsByRoleName("hr")) {
	        Role hrRole = new Role();
	        hrRole.setRoleName("hr");
	        hrRole.setDescription("Human Resources role");
	        roleRepository.save(hrRole);
	        System.out.println("HR role created.");
	    }

        if (!roleRepository.existsByRoleName("reporting manager")) {
	        Role reportingManagerRole = new Role();
	        reportingManagerRole.setRoleName("reporting manager");
	        reportingManagerRole.setDescription("Reporting manager role");
	        roleRepository.save(reportingManagerRole);
	        System.out.println("reporting manager role created.");
	    }

	    if (!roleRepository.existsByRoleName("user")) {
	        Role userRole = new Role();
	        userRole.setRoleName("user");
	        userRole.setDescription("Standard user role");
	        roleRepository.save(userRole);
	        System.out.println("USER role created.");
	    }

	    if (!roleRepository.existsByRoleName("supervisor")) {
	        Role supervisorRole = new Role();
	        supervisorRole.setRoleName("supervisor");
	        supervisorRole.setDescription("Supervisor role");
	        roleRepository.save(supervisorRole);
	        System.out.println("SUPERVISOR role created.");
	    }

        if (!roleRepository.existsByRoleName("manager")) {
	        Role managerRole = new Role();
	        managerRole.setRoleName("manager");
	        managerRole.setDescription("MANAGER role");
	        roleRepository.save(managerRole);
	        System.out.println("MANAGER role created.");
	    }
	}

	@Override
public Role findByRoleName(String roleName) {
    return roleRepository.findByRoleName(roleName);
}

@Override
public List<String> findActionNamesByRoleId(Long roleId) {
    return roleRepository.findActioNamesByRoleId(roleId);
}

@Override
public List<String> findActionAliasesByRoleId(Long roleId) {
    Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("Role not found with ID: " + roleId));
    if (role.getActionList() == null) return List.of();
    return role.getActionList()
            .stream()
            .map(Action::getAlias)
            .collect(Collectors.toList());
}


		
		
	

}

