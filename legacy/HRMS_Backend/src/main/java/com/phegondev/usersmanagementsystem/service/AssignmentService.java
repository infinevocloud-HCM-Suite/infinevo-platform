package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.AssignmentDTO;
import com.phegondev.usersmanagementsystem.entity.Assignment;
import com.phegondev.usersmanagementsystem.entity.Employee;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.repository.AssignmentRepository;
import com.phegondev.usersmanagementsystem.repository.EmployeeRepository;
import com.phegondev.usersmanagementsystem.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AssignmentService {
    private final AssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public AssignmentService(AssignmentRepository assignmentRepository, 
                           ProjectRepository projectRepository,
                           EmployeeRepository employeeRepository) {
        this.assignmentRepository = assignmentRepository;
        this.projectRepository = projectRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<Assignment> getAssignmentsByProjectId(Long projectId) {
        return assignmentRepository.findByProjectId(projectId);
    }

    public Assignment createAssignment(AssignmentDTO assignmentDTO) {
        System.out.println("Starting assignment creation...");
        System.out.println("Looking for project with ID: " + assignmentDTO.getProjectId());

        Optional<Project> projectOptional = projectRepository.findById(assignmentDTO.getProjectId());
        if (projectOptional.isEmpty()) {
            System.out.println("Project not found with ID: " + assignmentDTO.getProjectId());
            throw new RuntimeException("Project not found with id: " + assignmentDTO.getProjectId());
        }
        System.out.println("Project found: " + projectOptional.get());

        System.out.println("Looking for employee with ID: " + assignmentDTO.getEmpId());
        Optional<Employee> employeeOptional = employeeRepository.findByPersonalEmpId(assignmentDTO.getEmpId());
        if (employeeOptional.isEmpty()) {
            System.out.println("Employee not found with ID: " + assignmentDTO.getEmpId());
            throw new RuntimeException("Employee not found with ID: " + assignmentDTO.getEmpId());
        }
        Employee employee = employeeOptional.get();
        System.out.println("Employee found: " + employee);

        String fullName = employee.getPersonal().getFirstName() + " " +
                          (employee.getPersonal().getMiddleName() != null ? employee.getPersonal().getMiddleName() + " " : "") +
                          employee.getPersonal().getLastName();
        System.out.println("Constructed employee full name: " + fullName);

        Assignment assignment = new Assignment();
        assignment.setEmpId(assignmentDTO.getEmpId());
        assignment.setEmpName(fullName);
        assignment.setProject(projectOptional.get());
        
        System.out.println("Saving assignment to repository: " + assignment);
        Assignment savedAssignment = assignmentRepository.save(assignment);
        System.out.println("Assignment saved successfully: " + savedAssignment);

        return savedAssignment;
    }


    public Assignment updateAssignment(Long id, AssignmentDTO assignmentDTO) {
        Optional<Assignment> assignmentOptional = assignmentRepository.findById(id);
        if (assignmentOptional.isPresent()) {
            Assignment assignment = assignmentOptional.get();
            assignment.setEmpId(assignmentDTO.getEmpId());
            assignment.setEmpName(assignmentDTO.getEmpName());
            return assignmentRepository.save(assignment);
        }
        throw new RuntimeException("Assignment not found with id: " + id);
    }

    public void deleteAssignment(Long id) {
        assignmentRepository.deleteById(id);
    }
}