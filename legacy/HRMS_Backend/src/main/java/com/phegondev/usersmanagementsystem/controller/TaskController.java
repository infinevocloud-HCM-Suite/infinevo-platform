package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.TaskDTO;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Task;
import com.phegondev.usersmanagementsystem.service.TaskService;

import io.jsonwebtoken.lang.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

import java.util.List;

@RestController
public class TaskController {
    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

@GetMapping("/alltasks")
public ResponseEntity<List<Task>> getAllTasks() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    OurUsers user = (OurUsers) authentication.getPrincipal();

    // ✅ Get all role names (multi-role support)
    List<String> roles = user.getRoleNames().stream()
                             .map(String::toLowerCase)
                             .collect(Collectors.toList());

    List<Task> tasks;

    // ✅ Check role membership instead of single equals()
    if (roles.contains("manager")) {
        // Managers can see their own project's tasks
        tasks = taskService.getTasksByManager(user.getEmpId());
    } else if (roles.contains("admin") || roles.contains("hr")) {
        // Admins and HR can see all tasks
        tasks = taskService.getAllTasks();
    } else {
        // Other roles see no tasks or limited scope
        tasks = java.util.Collections.emptyList();
    }

    return ResponseEntity.ok(tasks);
}


    @GetMapping("/tasks/project/{projectId}")
    public ResponseEntity<List<Task>> getTasksByProjectId(@PathVariable Long projectId) {
        List<Task> tasks = taskService.getTasksByProjectId(projectId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/tasks/status/{status}")
    public ResponseEntity<List<Task>> getTasksByStatus(@PathVariable String status) {
        List<Task> tasks = taskService.getTasksByStatus(status);
        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/tasks/add")
    public ResponseEntity<Task> createTask(@RequestBody TaskDTO taskDTO) {
        Task task = taskService.createTask(taskDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody TaskDTO taskDTO) {
        Task task = taskService.updateTask(id, taskDTO);
        return ResponseEntity.ok(task);
    }

    @PatchMapping("/tasks/{id}/status")
    public ResponseEntity<Task> updateTaskStatus(@PathVariable Long id, @RequestBody String status) {
        Task task = taskService.updateTaskStatus(id, status);
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> getAllUsersTasks() {
    	   Authentication authentication = SecurityContextHolder.getContext().getAuthentication();      
           
           OurUsers user = (OurUsers) authentication.getPrincipal();
           String empId = user.getEmpId();
           System.out.println(" user emp id: " + user.getEmpId());
        List<Task> tasks = taskService.getAllUsersTasks(empId);
        return ResponseEntity.ok(tasks);
    }

        // ✅ NEW: Get tasks for projects assigned to this manager
    @GetMapping("/tasks/by-manager")
    public ResponseEntity<List<Task>> getTasksByManager() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String empId = user.getEmpId();

        List<Task> tasks = taskService.getTasksByManager(empId);
        return ResponseEntity.ok(tasks);
    }
}