package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.ProjectDTO;
import com.phegondev.usersmanagementsystem.dto.ProjectWithTeamDTO;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectDTO>> getAllProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        List<String> roles = user.getRoleNames().stream().map(String::toLowerCase).toList();
        String empId = user.getEmpId();

        List<Project> projects;

        if (roles.contains("manager")) {
            projects = projectService.getProjectsByManagerId(empId);
        } else if (roles.contains("admin") || roles.contains("hr")) {
            if (status != null && !status.isEmpty()) {
                projects = projectService.getProjectsByStatus(status);
            } else if (search != null && !search.isEmpty()) {
                projects = projectService.searchProjectsByName(search);
            } else {
                projects = projectService.getAllProjects();
            }
        } else {
            projects = Collections.emptyList();
        }

        List<ProjectDTO> projectDTOs = projects.stream().map(project -> {
            ProjectDTO dto = new ProjectDTO();
            dto.setId(project.getId());
            dto.setName(project.getName());
            dto.setCategory(project.getCategory());
            dto.setStartDate(project.getStartDate());
            dto.setEndDate(project.getEndDate());
            dto.setPriority(project.getPriority());
            dto.setBudget(project.getBudget());
            dto.setDescription(project.getDescription());
            dto.setStatus(project.getStatus());
            dto.setProgress(project.getProgress());
            dto.setManagerId(project.getManagerId());
            dto.setManagerName(project.getManagerName());

            List<com.phegondev.usersmanagementsystem.dto.AssignmentDTO> assignmentDTOs =
                    project.getAssignments().stream().map(assignment -> {
                        com.phegondev.usersmanagementsystem.dto.AssignmentDTO adto =
                                new com.phegondev.usersmanagementsystem.dto.AssignmentDTO();
                        adto.setId(assignment.getId());
                        adto.setEmpId(assignment.getEmpId());
                        adto.setEmpName(assignment.getEmpName());
                        adto.setProjectId(project.getId());
                        return adto;
                    }).collect(Collectors.toList());

            dto.setAssignments(assignmentDTOs);
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(projectDTOs);
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<Project> getProjectById(@PathVariable Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        List<String> roles = user.getRoleNames().stream().map(String::toLowerCase).toList();

        Project project = projectService.getProjectById(id);

        if (roles.contains("manager") &&
                !project.getManagerId().equals(user.getEmpId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(project);
    }

    @PostMapping("/projects/add")
    public ResponseEntity<Project> createProject(@RequestBody ProjectDTO projectDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        List<String> roles = user.getRoleNames().stream().map(String::toLowerCase).toList();

        // ✅ Only admin and HR can create projects
        if (!roles.contains("admin") && !roles.contains("hr")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Project project = projectService.createProject(projectDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<Project> updateProject(
            @PathVariable Long id,
            @RequestBody ProjectDTO projectDTO) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        List<String> roles = user.getRoleNames().stream().map(String::toLowerCase).toList();

        Project existingProject = projectService.getProjectById(id);

        if (roles.contains("manager") &&
                !existingProject.getManagerId().equals(user.getEmpId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Project project = projectService.updateProject(id, projectDTO);
        return ResponseEntity.ok(project);
    }

    @PatchMapping("/projects/{id}/status")
    public ResponseEntity<Project> updateProjectStatus(
            @PathVariable Long id,
            @RequestBody String status) {
        Project project = projectService.updateProjectStatus(id, status);
        return ResponseEntity.ok(project);
    }

    @PatchMapping("/projects/{id}/progress")
    public ResponseEntity<Project> updateProjectProgress(
            @PathVariable Long id,
            @RequestBody Integer progress) {
        Project project = projectService.updateProjectProgress(id, progress);
        return ResponseEntity.ok(project);
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/count")
    public ResponseEntity<Long> countProjects() {
        Long count = projectService.countProjects();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/projects/status-count")
    public ResponseEntity<Map<String, Long>> countProjectsByStatus() {
        Map<String, Long> statusCounts = projectService.countProjectsByStatus();
        return ResponseEntity.ok(statusCounts);
    }

    @GetMapping("/projects/by-emp")
    public ResponseEntity<List<ProjectDTO>> getProjectsByEmpId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String empId = user.getEmpId();
        List<ProjectDTO> projects = projectService.getProjectsByEmpId(empId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/manager/projects")
    public ResponseEntity<List<Project>> getManagerProjects() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        List<String> roles = user.getRoleNames().stream().map(String::toLowerCase).toList();
        String managerId = user.getEmpId();

        // ✅ Verify the user is actually a manager
        if (!roles.contains("manager")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Project> projects = projectService.getProjectsByManagerId(managerId);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/projects/by-manager/{managerId}")
    public ResponseEntity<List<ProjectWithTeamDTO>> getProjectsWithTeamsByManager(@PathVariable String managerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        List<String> roles = user.getRoleNames().stream().map(String::toLowerCase).toList();

        // ✅ Only allow if admin/HR or the manager is requesting their own data
        if (!roles.contains("admin") && !roles.contains("hr") && !managerId.equals(user.getEmpId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<ProjectWithTeamDTO> projectsWithTeams = projectService.getProjectsWithTeamsByManager(managerId);
        return ResponseEntity.ok(projectsWithTeams);
    }

    @GetMapping("/projects/by-employee/{empId}")
    public ResponseEntity<List<ProjectDTO>> getProjectsByEmpId(@PathVariable String empId) {
        List<ProjectDTO> projects = projectService.getProjectsByEmpId(empId);
        return ResponseEntity.ok(projects);
    }
}
