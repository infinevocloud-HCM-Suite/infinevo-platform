package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.ProjectWithTeamDTO;
import com.phegondev.usersmanagementsystem.dto.TeamMemberDTO;
import com.phegondev.usersmanagementsystem.dto.TaskDTO;
import com.phegondev.usersmanagementsystem.dto.timesheet.TimesheetDto;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.entity.Task;
import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;
import com.phegondev.usersmanagementsystem.service.ProjectService;
import com.phegondev.usersmanagementsystem.service.TaskService;
import com.phegondev.usersmanagementsystem.service.timesheet.TimesheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")
public class ManagerDashboardController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TimesheetService timesheetService;

    @GetMapping("/manager")
    public ResponseEntity<Map<String, Object>> getManagerDashboard() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String managerId = ((com.phegondev.usersmanagementsystem.entity.OurUsers) authentication.getPrincipal()).getEmpId();

        Map<String, Object> response = new HashMap<>();

        // 1. Get all projects for manager
        List<ProjectWithTeamDTO> projectsWithTeams = projectService.getProjectsWithTeamsByManager(managerId);
        response.put("projects", projectsWithTeams);

        // 2. Extract team members
        Set<String> teamMemberIds = projectsWithTeams.stream()
                .flatMap(p -> p.getTeamMembers().stream())
                .map(TeamMemberDTO::getEmpId)
                .collect(Collectors.toSet());
        response.put("teamMembers", teamMemberIds);

        // 3. Get active projects (not completed)
        List<ProjectWithTeamDTO> activeProjects = projectsWithTeams.stream()
                .filter(p -> !"COMPLETED".equalsIgnoreCase(p.getProjectStatus()))
                .collect(Collectors.toList());
        response.put("activeProjects", activeProjects);
        response.put("recentActiveProjects", activeProjects.stream().limit(5).collect(Collectors.toList()));

        // 4. Get tasks for manager
        List<Task> allTasks = taskService.getTasksByManager(managerId);
        List<TaskDTO> taskDTOs = allTasks.stream().map(task -> {
            TaskDTO dto = new TaskDTO();
            dto.setId(task.getId());
            dto.setTitle(task.getTitle());
            dto.setDescription(task.getDescription());
            dto.setProjectId(task.getProject().getId());
            dto.setAssigneeId(task.getAssigneeId());
            dto.setDueDate(task.getDueDate());
            dto.setPriority(task.getPriority());
            dto.setStatus(task.getStatus());
            dto.setEstimatedHours(task.getEstimatedHours());
            return dto;
        }).collect(Collectors.toList());
        response.put("tasks", taskDTOs);
        response.put("recentTasks", taskDTOs.stream().limit(5).collect(Collectors.toList()));

        // 5. Get pending timesheets (status NOT APPROVED or REJECTED)
        List<Long> projectIds = projectsWithTeams.stream()
                .map(ProjectWithTeamDTO::getProjectId)
                .collect(Collectors.toList());

        List<TimesheetDto> allTimesheets = timesheetService.getTimesheetsByProjectIds(projectIds, null, null, null);
        List<TimesheetDto> pendingTimesheets = allTimesheets.stream()
                .filter(t -> t.getStatus() != TimesheetStatus.APPROVED && t.getStatus() != TimesheetStatus.REJECTED)
                .collect(Collectors.toList());

        response.put("pendingTimesheets", pendingTimesheets);
        response.put("recentPendingTimesheets", pendingTimesheets.stream().limit(5).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }
}
