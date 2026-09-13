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

import com.phegondev.usersmanagementsystem.dto.useraccess.ActionDTO;
import com.phegondev.usersmanagementsystem.service.useraccess.ActionService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/auth/actions")
public class ActionController {

    private final ActionService actionService;

    public ActionController(ActionService actionService) {
        this.actionService = actionService;
    }

    

  // @PreAuthorize("hasAuthority('VIEW_LIST_ACTIONS')")
    @PostMapping
    public ResponseEntity<String> createAction(@Valid @RequestBody ActionDTO actionDTO) {
       
            actionService.createAction(actionDTO);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Action created successfully!");
     
    }


    // @PreAuthorize("hasAuthority('VIEW_LIST_ACTIONS')")
    @GetMapping
    public ResponseEntity<List<ActionDTO>> getAllActions() {
        List<ActionDTO> actions = actionService.getAllActions();
        return ResponseEntity.ok(actions);
    }


   @GetMapping("/{actionId}")
    public ResponseEntity<ActionDTO> getActionById(@PathVariable Long actionId) {
        ActionDTO action = actionService.getActionById(actionId);
        return ResponseEntity.ok(action);
    }

    
 
 //   @PreAuthorize("hasAuthority('Edit Action')")
    @PutMapping("/{actionId}")
    public ResponseEntity<ActionDTO> updateAction(
            @PathVariable Long actionId,
            @Valid @RequestBody ActionDTO actionDTO) {
        ActionDTO updatedAction = actionService.updateAction(actionId, actionDTO);
        return ResponseEntity.ok(updatedAction);
    }
    

 //   @PreAuthorize("hasAuthority('Delete Action')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteAction(@PathVariable Long id) { 
    	 System.out.println("Initiating deletion for Action ID: " + id);
    	 actionService.deleteActionById(id);
    	 return ResponseEntity.ok("Action deleted successfully.");
    }
}

