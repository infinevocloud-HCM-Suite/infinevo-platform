package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.OvertimeRequestDTO;
import com.phegondev.usersmanagementsystem.entity.LeaveType;
import com.phegondev.usersmanagementsystem.entity.OvertimeRequest;
import com.phegondev.usersmanagementsystem.enumuration.OvertimeStatus;
import com.phegondev.usersmanagementsystem.repository.LeaveTypeRepository;
import com.phegondev.usersmanagementsystem.repository.OvertimeRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OvertimeRequestService {

    @Autowired
    private OvertimeRequestRepository repository;

    @Autowired
    private  LeaveTypeRepository leaveTypeRepository;

    private String formatStatus(String status) {
        if (status == null || status.isEmpty()) return status;
        return status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
    }

    public List<OvertimeRequest> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public OvertimeRequest save(OvertimeRequestDTO dto) {
        return repository.save(toEntity(dto));
    }

    public OvertimeRequest toEntity(OvertimeRequestDTO dto) {
        System.out.println("[Mapper] Converting DTO to Entity using setters...");

        OvertimeRequest entity = new OvertimeRequest();

        entity.setEmployeeId(dto.getEmployeeId());
        entity.setEmployeeName(dto.getEmployeeName());
        entity.setCategory(dto.getCategory());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setProject(dto.getProject());
        entity.setNotes(dto.getNotes());
        entity.setManagerEmployeeId(dto.getManagerEmployeeId());
        return entity;
    }


        public OvertimeRequest updateStatus(Long overtimeId, String role, OvertimeStatus newStatus, String comment) {
            Optional<OvertimeRequest> optional = repository.findById(overtimeId);

            if (optional.isEmpty()) {
                throw new RuntimeException("Overtime request not found with ID: " + overtimeId);
            }

            OvertimeRequest request = optional.get();


            if (role.equalsIgnoreCase("manager")) {
                request.setManagerStatus(newStatus);
                request.setManagerUpdatedAt(LocalDateTime.now());
                request.setManagerComment(comment); // Save manager comment
                System.out.println("[STATUS] Manager updated status to " + newStatus);
            } else if (role.equalsIgnoreCase("hr")) {
                request.setHrStatus(newStatus);
                request.setHrUpdatedAt(LocalDateTime.now());
                request.setHrComment(comment); // Save HR comment
                System.out.println("[STATUS] HR updated status to " + newStatus);
            } else {
                throw new RuntimeException("Invalid role: " + role);
            }

            // If both approved, create Comp Off leave type
            //   if (request.getManagerStatus() == OvertimeStatus.APPROVED && request.getHrStatus() == OvertimeStatus.APPROVED) {
            if (!request.getCompOffCreated()
                    && request.getManagerStatus() == OvertimeStatus.APPROVED
                    && request.getHrStatus() == OvertimeStatus.APPROVED) {

                createCompOffLeaveTypeForEmployee(request);
                //   }
                request.setCompOffCreated(true);
                System.out.println("[COMPOFF] Comp Off created for employeeId=" + request.getEmployeeId());
            }

            return repository.save(request);
        }


        private void createCompOffLeaveTypeForEmployee(OvertimeRequest request) {
            String empId = request.getEmployeeId();
            System.out.println("[COMPOFF] Creating Comp Off leave type for employee: " + empId);

            LeaveType compOff = new LeaveType();
            compOff.setName("Comp Off - " + empId + " - " + request.getId());
            compOff.setDescription("Comp Off earned from approved overtime on project " + request.getProject());// Assign hours as leave days
            compOff.setLeaveCarriedForward(false);
            compOff.setApplyToAllEmployees(false);
            compOff.setStartDate(LocalDate.now()); // Optional: set window
          //  compOff.setEndDate(LocalDate.now().plusDays(90));
            compOff.setStartDate(request.getStartTime().toLocalDate());
            compOff.setEndDate(request.getStartTime().toLocalDate().plusDays(90));
            compOff.getEmployeeIds().add(empId);

            double durationHours = request.getDurationHours();
            double leaveDays = 0.0;

            if (durationHours >= 6) {
                leaveDays = 1.0;
            } else if (durationHours >= 4) {
                leaveDays = 0.5;
            }

            //else {
             //   throw new IllegalArgumentException("Overtime duration must be at least 4 hours to qualify for Comp Off.");
            //    leaveDays = durationHours;
           // }// else leaveDays remains 0.0 (no leave)

            compOff.setDefaultDays(leaveDays);

            leaveTypeRepository.save(compOff);
            System.out.println("[COMPOFF] Comp Off leave type created successfully for employee: " + empId);
        }




    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<OvertimeRequestDTO> getDtosByEmployeeId(String employeeId) {
        System.out.println("[SERVICE] Fetching overtime requests for employee ID: " + employeeId);

        List<OvertimeRequest> requests = repository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);

        System.out.println("[SERVICE] Found " + requests.size() + " records");

        return requests.stream()
                .map(this::toDto)
                .toList();
    }

    private OvertimeRequestDTO toDto(OvertimeRequest entity) {
        System.out.println("[MAPPER] Converting entity to DTO for ID: " + entity.getId());

        OvertimeRequestDTO dto = new OvertimeRequestDTO();
        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeName(entity.getEmployeeName());
        dto.setCategory(entity.getCategory());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setProject(entity.getProject());
        dto.setNotes(entity.getNotes());
        dto.setManagerStatus(entity.getManagerStatus());
        dto.setHrStatus(entity.getHrStatus());
        dto.setManagerUpdatedAt(entity.getManagerUpdatedAt());
        dto.setHrUpdatedAt(entity.getHrUpdatedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setHrComment(entity.getHrComment());
        dto.setManagerComment(entity.getManagerComment());
        return dto;
    }


    public List<OvertimeRequest> getByStatus(String status) {
       // return repository.findByStatusOrderByCreatedAtDesc(status);
        return null;
    }

    public Optional<OvertimeRequest> findById(Long id) {
        return repository.findById(id);
    }

    public OvertimeRequest updateOvertimeRequest(Long id, OvertimeRequest requestDetails) {
        return repository.findById(id).map(request -> {
            if (requestDetails.getCategory() != null) {
                request.setCategory(requestDetails.getCategory());
            }
            if (requestDetails.getStartTime() != null) {
                request.setStartTime(requestDetails.getStartTime());
            }
            if (requestDetails.getEndTime() != null) {
                request.setEndTime(requestDetails.getEndTime());
            }
            if (requestDetails.getProject() != null) {
                request.setProject(requestDetails.getProject());
            }
            if (requestDetails.getNotes() != null) {
                request.setNotes(requestDetails.getNotes());
            }
        //    if (requestDetails.getStatus() != null) {
      //          request.setStatus(formatStatus(requestDetails.getStatus()));
       //     }
            return repository.save(request);
        }).orElseThrow(() -> new RuntimeException("OvertimeRequest not found with id: " + id));
    }

    public Optional<OvertimeRequest> getById(Long id) {
        return repository.findById(id);
    }


    public List<OvertimeRequestDTO> getOvertimeRequestByManager(String managerEmployeeId) {
        List<OvertimeRequest> list = repository.findByManagerEmployeeIdOrderByCreatedAtDesc(managerEmployeeId);
        return list.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }













}