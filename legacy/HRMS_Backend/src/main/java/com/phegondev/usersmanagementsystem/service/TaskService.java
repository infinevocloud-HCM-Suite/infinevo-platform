package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.TaskDTO;
import com.phegondev.usersmanagementsystem.entity.Task;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.repository.TaskRepository;
import com.phegondev.usersmanagementsystem.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public List<Task> getTasksByProjectId(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    public List<Task> getTasksByStatus(String status) {
        return taskRepository.findByStatus(status);
    }

    public Task createTask(TaskDTO taskDTO) {
        Optional<Project> projectOptional = projectRepository.findById(taskDTO.getProjectId());
        if (projectOptional.isPresent()) {
            Task task = new Task();
            mapTaskDTOToEntity(taskDTO, task);
            task.setProject(projectOptional.get());
            return taskRepository.save(task);
        }
        throw new RuntimeException("Project not found with id: " + taskDTO.getProjectId());
    }

    public Task updateTask(Long id, TaskDTO taskDTO) {
        Optional<Task> taskOptional = taskRepository.findById(id);
        if (taskOptional.isPresent()) {
            Task task = taskOptional.get();
            mapTaskDTOToEntity(taskDTO, task);
            
            // Update project if changed
            if (!task.getProject().getId().equals(taskDTO.getProjectId())) {
                Optional<Project> projectOptional = projectRepository.findById(taskDTO.getProjectId());
                projectOptional.ifPresent(task::setProject);
            }
            
            return taskRepository.save(task);
        }
        throw new RuntimeException("Task not found with id: " + id);
    }

    public Task updateTaskStatus(Long id, String status) {
        Optional<Task> taskOptional = taskRepository.findById(id);
        if (taskOptional.isPresent()) {
            Task task = taskOptional.get();
            task.setStatus(status);
            return taskRepository.save(task);
        }
        throw new RuntimeException("Task not found with id: " + id);
    }

    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    public List<Task> getAllUsersTasks(String empId) {
        return taskRepository.findTasksByAssignedEmpId(empId);
    }

    // ✅ NEW: Get all tasks from projects assigned to this manager
    public List<Task> getTasksByManager(String managerEmpId) {
        List<Project> projects = projectRepository.findByManagerId(managerEmpId);
        List<Long> projectIds = projects.stream()
                                        .map(Project::getId)
                                        .collect(Collectors.toList());
        return taskRepository.findByProjectIdIn(projectIds);
    }

    private void mapTaskDTOToEntity(TaskDTO dto, Task entity) {
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setDueDate(dto.getDueDate());
        entity.setPriority(dto.getPriority());
        entity.setStatus(dto.getStatus());
        entity.setEstimatedHours(dto.getEstimatedHours());
    }
}
