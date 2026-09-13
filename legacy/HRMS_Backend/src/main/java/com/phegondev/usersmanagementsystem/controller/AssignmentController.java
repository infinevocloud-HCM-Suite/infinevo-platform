package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.AssignmentDTO;
import com.phegondev.usersmanagementsystem.entity.Assignment;
import com.phegondev.usersmanagementsystem.service.AssignmentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AssignmentController {
    private final AssignmentService assignmentService;

    @Autowired
    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/assignments/project/{projectId}")
    public ResponseEntity<List<Assignment>> getAssignmentsByProjectId(@PathVariable Long projectId) {
        List<Assignment> assignments = assignmentService.getAssignmentsByProjectId(projectId);
        return ResponseEntity.ok(assignments);
    }

    @PostMapping("/assignments")
    public ResponseEntity<Assignment> createAssignment(@RequestBody AssignmentDTO assignmentDTO) {
        System.out.println("Received request to create assignment with DTO: " + assignmentDTO);
        Assignment assignment = assignmentService.createAssignment(assignmentDTO);
        System.out.println("Assignment created successfully: " + assignment);
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment);
    }


    @PutMapping("/assignments/{id}")
    public ResponseEntity<Assignment> updateAssignment(
            @PathVariable Long id,
            @RequestBody AssignmentDTO assignmentDTO) {
        Assignment assignment = assignmentService.updateAssignment(id, assignmentDTO);
        return ResponseEntity.ok(assignment);
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }
}