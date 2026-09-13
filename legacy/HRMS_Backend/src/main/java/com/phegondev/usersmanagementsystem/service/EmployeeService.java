package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.entity.Employee;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.repository.EmployeeRepository;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UsersRepo usersRepo;

    @Transactional
    public Employee saveEmployee(Employee employee) {
        // Establish bidirectional relationships
        if (employee.getPersonal() != null) {
            employee.getPersonal().setEmployee(employee);
        }
        if (employee.getIdentification() != null) {
            employee.getIdentification().setEmployee(employee);
        }
        if (employee.getWork() != null) {
            employee.getWork().setEmployee(employee);
        }
        if (employee.getContact() != null) {
            employee.getContact().setEmployee(employee);
        }
        if (employee.getReport() != null) {
            employee.getReport().setEmployee(employee);
        }

        return employeeRepository.save(employee);
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public List<String> getAllEmployeeIds() {
        return employeeRepository.findAllEmployeeIds();
    }

    // ✅ NEW METHOD ADDED HERE
    public List<Map<String, String>> searchEmployees(String query) {
        return employeeRepository.findAll().stream()
                .filter(employee -> employee.getEmpId().toLowerCase().contains(query.toLowerCase()) ||
                        (employee.getPersonal().getFirstName() + " " +
                                (employee.getPersonal().getMiddleName() != null
                                        ? employee.getPersonal().getMiddleName() + " "
                                        : "")
                                +
                                employee.getPersonal().getLastName())
                                .toLowerCase().contains(query.toLowerCase()))
                .map(employee -> {
                    Map<String, String> empInfo = new HashMap<>();
                    empInfo.put("id", employee.getId().toString());
                    empInfo.put("empId", employee.getEmpId());
                    empInfo.put("name",
                            employee.getPersonal().getFirstName() + " " +
                                    (employee.getPersonal().getMiddleName() != null
                                            ? employee.getPersonal().getMiddleName() + " "
                                            : "")
                                    +
                                    employee.getPersonal().getLastName());
                    return empInfo;
                })
                .collect(Collectors.toList());
    }

    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    public boolean existsByEmpId(String empId) {
        return employeeRepository.existsByPersonalEmpId(empId);
    }

    @Transactional
    public void linkEmployeeToUser(String empId, OurUsers user) {
        Employee employee = employeeRepository.findByPersonalEmpId(empId)
                .orElseThrow(() -> new RuntimeException("Employee not found with empId: " + empId));

        // Verify empId matches
        if (!empId.equals(user.getEmpId())) {
            throw new RuntimeException("Employee ID mismatch between user and employee records");
        }

        // Establish bidirectional relationship
        employee.setOurUser(user);
        user.setEmployee(employee);

        employeeRepository.save(employee);
    }

    public Optional<Employee> getEmployeeByEmpId(String empId) {
        return employeeRepository.findByPersonalEmpId(empId);
    }

    public String generateNextEmpId(String employmentStatus) {
        List<String> existingIds = employeeRepository.findAllEmployeeIds();

        String prefix;
        switch (employmentStatus) {
            case "Full-Time Permanent":
                prefix = "P";
                break;
            case "Contract":
                prefix = "C";
                break;
            case "Internship":
                prefix = "I";
                break;
            case "Part-Time":
                prefix = "T";
                break;
            default:
                prefix = "E"; // default case
        }

        if (existingIds.isEmpty()) {
            return prefix + "001";
        }

        // Filter IDs with the same prefix and get the highest number
        int maxNumber = existingIds.stream()
                .filter(id -> id.startsWith(prefix))
                .map(id -> id.replace(prefix, ""))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);

        return String.format("%s%03d", prefix, maxNumber + 1);
    }

    public String updateUserAccess(Integer userId, boolean isNonBlocked) {
        System.out.println("Attempting to update user access. User ID: " + userId + ", isNonBlocked: " + isNonBlocked);

        Optional<OurUsers> optionalUser = usersRepo.findById(userId);

        if (optionalUser.isEmpty()) {
            System.out.println("User not found with ID: " + userId);
            throw new RuntimeException("User not found with ID: " + userId);
        }

        OurUsers user = optionalUser.get();
        user.setIsNonBlocked(isNonBlocked);
        usersRepo.save(user);

        String status = isNonBlocked ? "unblocked" : "blocked";
        System.out.println("User " + user.getEmail() + " has been " + status + ".");

        return "User has been " + status + ".";
    }

    public List<UserDTO> getEmployeesByReportingManagerId(String reportingManagerId) {
        System.out.println("📨 Fetching employees from DB for reportingManagerId: " + reportingManagerId);

        List<Employee> employees = employeeRepository.findByReport_ReportingManagerId(reportingManagerId);
        System.out.println("✅ Number of employees retrieved from DB: " + employees.size());

        List<UserDTO> dtos = employees.stream().map(this::mapToUserDTO).collect(Collectors.toList());

        System.out.println("📦 Converted " + dtos.size() + " employees to UserDTO");

        return dtos;
    }

    private UserDTO mapToUserDTO(Employee employee) {
        String fullName = employee.getPersonal().getFirstName() + " " + employee.getPersonal().getLastName();
        String empId = employee.getEmpId();

        System.out.println("🔄 Mapping employee to DTO: Name = " + fullName + ", EmpID = " + empId);

        UserDTO dto = new UserDTO();
        dto.setName(fullName);
        dto.setEmpId(empId);
        dto.setEmail(employee.getContact().getWorkEmail());

        return dto;
    }

    public List<String> getEmployeeIdsByReportingManager(String reportingManagerId) {
        return employeeRepository.findByReport_ReportingManagerId(reportingManagerId)
                .stream()
                .map(Employee::getEmpId)
                .collect(Collectors.toList());
    }

    public String getEmployeeEmail(String employeeId){
        OurUsers employee = usersRepo.findByEmpId(employeeId);
        return employee.getEmail();
    }

    
    public List<UserDTO> getUsersExcludingRole(String role) {
        System.out.println("#####[Service]##### Fetching users from repository (excluding role 'USER')");

        List<OurUsers> users = usersRepo.findByRoleNot(role);

        System.out.println("#####[Service]##### Retrieved users: " + users.size());

        List<UserDTO> dtoList = users.stream().map(user -> {
            UserDTO dto = new UserDTO();          
            dto.setEmail(user.getEmail());
            dto.setName(user.getName());       
            dto.setRole(user.getRole());
            dto.setEmpId(user.getEmpId());
            return dto;
        }).collect(Collectors.toList());

        System.out.println("#####[Service]##### Mapped users to DTOs: " + dtoList.size());
        return dtoList;
    }

}
