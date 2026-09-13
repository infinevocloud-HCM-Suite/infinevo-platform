//package com.phegondev.usersmanagementsystem.service;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import com.phegondev.usersmanagementsystem.entity.LeaveRequest;
//import com.phegondev.usersmanagementsystem.repository.LeaveRequestRepository;
//
//import java.util.List;
//
//@Service
//public class LeaveRequestService {
//
//    @Autowired
//    private LeaveRequestRepository leaveRequestRepository;
//
//    public List<LeaveRequest> getAllLeaveRequests() {
//        return leaveRequestRepository.findAll();
//    }
//
//    public LeaveRequest getLeaveRequestById(Long id) {
//        return leaveRequestRepository.findById(id).orElse(null);
//    }
//
//    public LeaveRequest createLeaveRequest(LeaveRequest leaveRequest) {
//        return leaveRequestRepository.save(leaveRequest);
//    }
//
//    public LeaveRequest updateLeaveRequest(Long id, LeaveRequest leaveRequest) {
//        LeaveRequest existingLeave = leaveRequestRepository.findById(id).orElse(null);
//        if (existingLeave != null) {
//            existingLeave.setEmpId(leaveRequest.getEmpId());
//            existingLeave.setName(leaveRequest.getName());
//            existingLeave.setLeaveType(leaveRequest.getLeaveType());
//            existingLeave.setFromDate(leaveRequest.getFromDate());
//            existingLeave.setToDate(leaveRequest.getToDate());
//            existingLeave.setReason(leaveRequest.getReason());
//            return leaveRequestRepository.save(existingLeave);
//        }
//        return null;
//    }
//
//    public void deleteLeaveRequest(Long id) {
//        leaveRequestRepository.deleteById(id);
//    }
//}

package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.LeaveRequestDTO;
import com.phegondev.usersmanagementsystem.entity.EmployeeLeaveBalance;
import com.phegondev.usersmanagementsystem.entity.LeaveRequest;
import com.phegondev.usersmanagementsystem.entity.LeaveType;
import com.phegondev.usersmanagementsystem.repository.EmployeeLeaveBalanceRepo;
import com.phegondev.usersmanagementsystem.repository.LeaveRequestRepository;
import com.phegondev.usersmanagementsystem.repository.LeaveTypeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveRequestService {

    @Autowired
    private LeaveRequestRepository repository;
    
    @Autowired
    private LeaveTypeRepository  leaveTypeRepo;
    
    @Autowired
    private EmployeeLeaveBalanceRepo employeeLeaveBalanceRepo;

    // Convert status to Title Case (e.g., "PENDING" → "Pending")
    private String formatStatus(String status) {
        if (status == null || status.isEmpty()) return status;
        return status.substring(0, 1).toUpperCase() + status.substring(1).toLowerCase();
    }

    // Fetch all leave requests in descending order of createdAt
    public List<LeaveRequest> getAll() {
        List<LeaveRequest> leaveRequests = repository.findAllByOrderByCreatedAtDesc();
        leaveRequests.forEach(req -> req.setStatus(formatStatus(req.getStatus())));
        return leaveRequests;
    }

    // Save a new or updated leave request
    // Save a new or updated leave request
    public LeaveRequest save(LeaveRequestDTO dto) {
        System.out.println("[INFO] Starting save process for leave request of employee: " + dto.getEmployeeId());

        // 1. Validate date range
        if (dto.getFromDate() != null && dto.getFromDate().isBefore(LocalDate.now())) {
            System.out.println("[ERROR] Start date is in the past: " + dto.getFromDate());
            throw new IllegalArgumentException("Start date cannot be in the past.");
        }

        if (dto.getToDate().isBefore(dto.getFromDate())) {
            System.out.println("[ERROR] To date is before from date.");
            throw new IllegalArgumentException("To date cannot be before From date.");
        }

        long daysRequested = ChronoUnit.DAYS.between(dto.getFromDate(), dto.getToDate()) + 1;
        System.out.println("[INFO] Leave days requested: " + daysRequested);

        // 2. Find LeaveType
        LeaveType leaveType = leaveTypeRepo.findById(dto.getLeaveTypeId())
                .orElseThrow(() -> {
                    System.out.println("[ERROR] Invalid leave type ID: " + dto.getLeaveTypeId());
                    return new IllegalArgumentException("Invalid leave type ID.");
                });
        System.out.println("[INFO] Found leave type: " + leaveType.getName());

        // 3. Check existing balance
        Optional<EmployeeLeaveBalance> balanceOpt = employeeLeaveBalanceRepo
                .findByEmployeeIdAndLeaveTypeId(dto.getEmployeeId(), dto.getLeaveTypeId());

        EmployeeLeaveBalance balance = balanceOpt.orElseGet(() -> {
            System.out.println("[INFO] No existing leave balance found, creating new...");
            if (leaveType.getDefaultDays() < daysRequested) {
                System.out.println("[ERROR] Insufficient leave balance for new entry.");
                throw new IllegalStateException("Insufficient leave balance for new entry.");
            }
            EmployeeLeaveBalance newBalance = new EmployeeLeaveBalance();
            newBalance.setEmployeeId(dto.getEmployeeId());
            newBalance.setLeaveType(leaveType.getName());
            newBalance.setLeaveTypeId(leaveType.getId());
            newBalance.setRemainingDays((float) (leaveType.getDefaultDays() - daysRequested));
            System.out.println("[INFO] New leave balance created with days: " + newBalance.getRemainingDays());
            return newBalance;
        });

        // 4. If balance existed, ensure there's enough leave
        if (balanceOpt.isPresent()) {
            System.out.println("[INFO] Existing leave balance found: " + balance.getRemainingDays());
            if (balance.getRemainingDays() < daysRequested) {
                System.out.println("[ERROR] Insufficient balance: Requested = " + daysRequested +
                                   ", Available = " + balance.getRemainingDays());
                throw new IllegalStateException("Insufficient leave balance.");
            }
            balance.setRemainingDays(balance.getRemainingDays() - daysRequested);
            System.out.println("[INFO] Deducted days. New balance: " + balance.getRemainingDays());
        }

        // 5. Save updated or new balance
        employeeLeaveBalanceRepo.save(balance);
        System.out.println("[INFO] Leave balance saved/updated successfully.");

        // 6. Convert DTO to Entity and save leave request
        LeaveRequest request = convertToLeaveRequestEntity(dto);
        LeaveRequest savedRequest = repository.save(request);
        System.out.println("[INFO] Leave request saved with ID: " + savedRequest.getId());

        return savedRequest;
    }

    
    private LeaveRequest convertToLeaveRequestEntity(LeaveRequestDTO dto) {
        System.out.println("[INFO] Converting DTO to LeaveRequest entity...");

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setEmployeeName(dto.getEmployeeName());
        request.setLeaveType(dto.getLeaveType()); // Should be human-readable name (could be dto.getLeaveTypeName())
        request.setFromDate(dto.getFromDate());
        request.setToDate(dto.getToDate());
        request.setReason(dto.getReason());
        request.setLeaveTypeId(dto.getLeaveTypeId());
      

        System.out.println("[INFO] Conversion complete for employee: " + dto.getEmployeeId());
        return request;
    }
    
 
    public LeaveRequest updateLeaveRequestStatus(Long requestId, String newStatus) {
        System.out.println("[INFO] Updating leave request status. ID: " + requestId + ", New Status: " + newStatus);

        LeaveRequest request = repository.findById(requestId)
            .orElseThrow(() -> {
                System.out.println("[ERROR] Leave request not found: " + requestId);
                return new IllegalArgumentException("Leave request not found.");
            });

        String oldStatus = request.getStatus();
        System.out.println("[INFO] Current status: " + oldStatus + ", Requested new status: " + newStatus);

        if (oldStatus.equals(newStatus)) {
            System.out.println("[INFO] Status is already " + newStatus + ". No changes needed.");
            return request;
        }

        long daysRequested = ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;

        // Fetch employee leave balance
        Optional<EmployeeLeaveBalance> balanceOpt = employeeLeaveBalanceRepo
                .findByEmployeeIdAndLeaveTypeId(request.getEmployeeId(), request.getLeaveTypeId());

        if (!balanceOpt.isPresent()) {
            System.out.println("[ERROR] Leave balance not found for employee and leave type.");
            throw new IllegalStateException("Leave balance not found.");
        }

        EmployeeLeaveBalance balance = balanceOpt.get();

        // Adjust balance based on status transition
        if (oldStatus.equals("REJECTED") && newStatus.equals("APPROVED")) {
            if (balance.getRemainingDays() < daysRequested) {
                System.out.println("[ERROR] Insufficient leave balance for approval.");
                throw new IllegalStateException("Insufficient leave balance to approve this request.");
            }
            balance.setRemainingDays(balance.getRemainingDays() - daysRequested);
            System.out.println("[INFO] Approved request after rejection. Deducted " + daysRequested + " days.");
        }   else  if (oldStatus.equals("REJECTED") && newStatus.equals("PENDING")) {
            if (balance.getRemainingDays() < daysRequested) {
                System.out.println("[ERROR] Insufficient leave balance for pending.");
                throw new IllegalStateException("Insufficient leave balance to pending this request.");
            }
            balance.setRemainingDays(balance.getRemainingDays() - daysRequested);
            System.out.println("[INFO] Pending request after rejection. Deducted " + daysRequested + " days.");
        }else if (oldStatus.equals("APPROVED") && newStatus.equals("REJECTED")) {
            balance.setRemainingDays(balance.getRemainingDays() + daysRequested);
            System.out.println("[INFO] Rejected approved request. Restored " + daysRequested + " days.");
        }else if (oldStatus.equals("PENDING") && newStatus.equals("REJECTED")) {
            balance.setRemainingDays(balance.getRemainingDays() + daysRequested);
            System.out.println("[INFO] Rejected pending request. Restored " + daysRequested + " days.");
        } else {
            System.out.println("[INFO] No balance adjustment needed.");
        }

        // Save updated balance
        employeeLeaveBalanceRepo.save(balance);

        // Update request status
        request.setStatus(newStatus);
        LeaveRequest updatedRequest = repository.save(request);
        System.out.println("[INFO] Leave request status updated successfully.");

        return updatedRequest;
    }




    

    // Update only the status of a leave request
    public Optional<LeaveRequest> updateStatus(Long id, String status) {
        return repository.findById(id).map(req -> {
            req.setStatus(formatStatus(status));
            return repository.save(req);
        });
    }

    // Delete a leave request by ID
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    // Get leave requests by employee name
    public List<LeaveRequest> getByEmployeeName(String employeeName) {
        return repository.findByEmployeeNameIgnoreCaseOrderByCreatedAtDesc(employeeName);
    }

    // Get leave requests by leave type
    public List<LeaveRequest> getByLeaveType(String leaveType) {
        return repository.findByLeaveTypeIgnoreCaseOrderByCreatedAtDesc(leaveType);
    }

    // Get leave requests by employee name and leave type
    public List<LeaveRequest> getByEmployeeNameAndLeaveType(String employeeName, String leaveType) {
        return repository.findByEmployeeNameIgnoreCaseAndLeaveTypeIgnoreCaseOrderByCreatedAtDesc(employeeName, leaveType);
    }
    
    public List<LeaveRequest> getByEmployeeId(String employeeId) {
        return repository.findByEmployeeId(employeeId);
    }
    
    // Add these methods to LeaveRequestService.java

public Optional<LeaveRequest> findById(Long id) {
    return repository.findById(id);
}

public LeaveRequest updateLeaveRequest(Long id, LeaveRequest leaveRequestDetails) {
    return repository.findById(id).map(leaveRequest -> {
        if (leaveRequestDetails.getEmployeeName() != null) {
            leaveRequest.setEmployeeName(leaveRequestDetails.getEmployeeName());
        }
        if (leaveRequestDetails.getLeaveType() != null) {
            leaveRequest.setLeaveType(leaveRequestDetails.getLeaveType());
        }
        if (leaveRequestDetails.getFromDate() != null) {
            leaveRequest.setFromDate(leaveRequestDetails.getFromDate());
        }
        if (leaveRequestDetails.getToDate() != null) {
            leaveRequest.setToDate(leaveRequestDetails.getToDate());
        }
        if (leaveRequestDetails.getReason() != null) {
            leaveRequest.setReason(leaveRequestDetails.getReason());
        }
        if (leaveRequestDetails.getStatus() != null) {
            leaveRequest.setStatus(formatStatus(leaveRequestDetails.getStatus()));
        }
        return repository.save(leaveRequest);
    }).orElseThrow(() -> new RuntimeException("LeaveRequest not found with id: " + id));
}
    
    
    
    
    
    

    
}