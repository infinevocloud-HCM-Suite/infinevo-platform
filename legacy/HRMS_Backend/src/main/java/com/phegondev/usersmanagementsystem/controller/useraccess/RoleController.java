package com.phegondev.usersmanagementsystem.controller.useraccess;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phegondev.usersmanagementsystem.dto.useraccess.ActionIdNameDTO;
import com.phegondev.usersmanagementsystem.dto.useraccess.MapActionRequestDto;
import com.phegondev.usersmanagementsystem.dto.useraccess.RoleDTO;
import com.phegondev.usersmanagementsystem.service.useraccess.RoleService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    

  //  @PreAuthorize("hasAuthority('Create Role')")
    @PostMapping
    public ResponseEntity<String> createRole(@Valid @RequestBody RoleDTO roleDTO) {
        
            roleService.createRole(roleDTO);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Role created successfully!");
       
    }


  //  @PreAuthorize("hasAuthority('List Roles')")
    @GetMapping
    public ResponseEntity<List<RoleDTO>> getAllRoles() {
        List<RoleDTO> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }


   @GetMapping("/{roleId}")
    public ResponseEntity<RoleDTO> getRoleById(@PathVariable Long roleId) {
        RoleDTO role = roleService.getRoleById(roleId);
        return ResponseEntity.ok(role);
    }

    

  //  @PreAuthorize("hasAuthority('Edit Role')")
    @PutMapping("/{roleId}")
    public ResponseEntity<RoleDTO> updateRole(
            @PathVariable Long roleId, 
            @Valid @RequestBody RoleDTO roleDTO) {
        RoleDTO updatedRole = roleService.updateRole(roleId, roleDTO);
        return ResponseEntity.ok(updatedRole);
    }
    
   
 //   @PreAuthorize("hasAuthority('Role Mapping')")
    @PostMapping("/mapActions")
    public ResponseEntity<?> mapActionToRole(@RequestBody MapActionRequestDto request) {

        boolean isSuccess = roleService.mapActionToRole(request.getRoleId(), request.getActionList());
        if (isSuccess) {
            return ResponseEntity.ok("Mapping updated successfully");
        } else {       
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Some problem occurred while updating the mapping.");
        }
    }
    
    

    @GetMapping("/getActions/{roleId}")
    public ResponseEntity<?> getActionsByRole(@PathVariable Long roleId) {      
            List<ActionIdNameDTO> actionIdNames = roleService.getActionsByRole(roleId);
            return ResponseEntity.ok(actionIdNames);
     
    }
    
    

  //  @PreAuthorize("hasAuthority('Delete Role')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRole(@PathVariable Long id) {
        System.out.println("Initiating deletion for Role ID: " + id);
        roleService.deleteRoleById(id);
        return ResponseEntity.ok("Role deleted successfully.");
    }

}

