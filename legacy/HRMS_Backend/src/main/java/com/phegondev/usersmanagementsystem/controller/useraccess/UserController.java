package com.phegondev.usersmanagementsystem.controller.useraccess;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phegondev.usersmanagementsystem.dto.useraccess.MapUserRequestDto;
import com.phegondev.usersmanagementsystem.dto.useraccess.UserActionDTO;
import com.phegondev.usersmanagementsystem.service.UsersManagementService;
import com.phegondev.usersmanagementsystem.service.useraccess.UserService;


@RestController
@RequestMapping("/users")
public class UserController {
	
	  private final UserService usersService;

	    public UserController(UserService usersService) {
	        this.usersService = usersService;
	    }
	
    @PostMapping("/mapActionsToUser")
    public ResponseEntity<?> mapActionsToUser(@RequestBody MapUserRequestDto request) {
    	usersService.mapActionsToUser(request);	        
       return ResponseEntity.ok("Mapping updated successfully");
      
    }
    
    @GetMapping("{id}/actions")
    public ResponseEntity<List<UserActionDTO>> getUserActions(@PathVariable Integer id ) {	               
       return ResponseEntity.ok(usersService.getUserActions(id));
      
    }

    

}
