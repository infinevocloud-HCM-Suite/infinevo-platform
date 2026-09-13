package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.EmployeeLeaveBalanceDTO;
import com.phegondev.usersmanagementsystem.entity.EmployeeLeaveBalance;
import com.phegondev.usersmanagementsystem.entity.LeaveType;
import com.phegondev.usersmanagementsystem.repository.EmployeeLeaveBalanceRepo;
import com.phegondev.usersmanagementsystem.repository.LeaveTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository repository;
    
    private final  EmployeeLeaveBalanceRepo employeeLeaveBalanceRepo;

    @Autowired
    public LeaveTypeService(LeaveTypeRepository repository, EmployeeLeaveBalanceRepo employeeLeaveBalanceRepo) {
        this.repository = repository;
        this.employeeLeaveBalanceRepo = employeeLeaveBalanceRepo;
        
    }

    public List<LeaveType> getAllLeaveTypes() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

  //  public List<LeaveType> getAllActiveLeaveTypes() {
  //      return repository.findAllByIsActive(true);
  //  }

    public LeaveType getLeaveTypeById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public LeaveType createLeaveType(LeaveType leaveType) {
        System.out.println("[SERVICE] Creating LeaveType: " + leaveType.getName());

//        if (repository.existsByName(leaveType.getName())) {
//            System.out.println("[SERVICE] Duplicate name found: " + leaveType.getName());
//            throw new IllegalArgumentException("Leave type with this name already exists");
//        }

        if (leaveType.isApplyToAllEmployees()) {
            System.out.println("[SERVICE] Applying to all employees. Clearing employeeIds.");
            leaveType.setEmployeeIds(new HashSet<>());
        }

        LeaveType saved = repository.save(leaveType);
        System.out.println("[SERVICE] LeaveType saved with ID: " + saved.getId());
        return saved;
    }

    public LeaveType updateLeaveType(Long id, LeaveType updatedLeaveType) {
        System.out.println("[SERVICE] Updating LeaveType with ID: " + id);
        
        LeaveType existing = repository.findById(id)
                .orElseThrow(() -> {
                    System.out.println("[SERVICE] LeaveType not found: " + id);
                    return new RuntimeException("LeaveType not found with id: " + id);
                });

        if (!existing.getName().equals(updatedLeaveType.getName()) &&
            repository.existsByName(updatedLeaveType.getName())) {
            System.out.println("[SERVICE] Duplicate name on update: " + updatedLeaveType.getName());
            throw new IllegalArgumentException("Leave type with this name already exists");
        }

        System.out.println("[SERVICE] Updating fields for LeaveType ID: " + id);

        existing.setName(updatedLeaveType.getName());
        existing.setDescription(updatedLeaveType.getDescription());
        existing.setDefaultDays(updatedLeaveType.getDefaultDays());
        existing.setStartDate(updatedLeaveType.getStartDate());
        existing.setEndDate(updatedLeaveType.getEndDate());
        existing.setLeaveCarriedForward(updatedLeaveType.isLeaveCarriedForward());
        existing.setApplyToAllEmployees(updatedLeaveType.isApplyToAllEmployees());

        if (updatedLeaveType.isApplyToAllEmployees()) {
            System.out.println("[SERVICE] applyToAllEmployees = true; clearing employeeIds");
            existing.setEmployeeIds(new HashSet<>());
        } else {
            System.out.println("[SERVICE] Setting specific employeeIds: " + updatedLeaveType.getEmployeeIds());
            existing.setEmployeeIds(updatedLeaveType.getEmployeeIds());
        }

        LeaveType saved = repository.save(existing);
        System.out.println("[SERVICE] LeaveType updated and saved with ID: " + saved.getId());
        return saved;
    }


    public void deleteLeaveType(Long id) {
        repository.deleteById(id);
    }

    public LeaveType toggleLeaveTypeStatus(Long id) {
        LeaveType leaveType = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("LeaveType not found with id: " + id));
      //  leaveType.setActive(!leaveType.isActive());
        leaveType.setUpdatedAt(LocalDateTime.now());
        return repository.save(leaveType);
    }

    public List<EmployeeLeaveBalanceDTO> getLeaveBalancesForEmployee(String employeeId) {
        System.out.println("[SERVICE] Getting leave types and checking balances for employeeId: " + employeeId);

        // Step 1: Get applicable leave types
        List<LeaveType> applicableLeaveTypes = repository.findApplicableLeaveTypesForEmployee(employeeId);
        System.out.println("[DEBUG] Found " + applicableLeaveTypes.size() + " applicable leave types");

        // Step 2: Get all balances for employee
        List<EmployeeLeaveBalance> balances = employeeLeaveBalanceRepo.findByEmployeeId(employeeId);
        System.out.println("[DEBUG] Found " + balances.size() + " leave balance records for employee");

        Map<Long, EmployeeLeaveBalance> balanceMap = balances.stream()
                .peek(b -> System.out.println("[BALANCE] LeaveTypeId: " + b.getLeaveTypeId() + ", Remaining: " + b.getRemainingDays()))
                .collect(Collectors.toMap(EmployeeLeaveBalance::getLeaveTypeId, b -> b));

        // Step 3: Combine into DTOs
        List<EmployeeLeaveBalanceDTO> result = new ArrayList<>();

        for (LeaveType lt : applicableLeaveTypes) {
            System.out.println("[PROCESSING] LeaveType: " + lt.getName() + ", ID: " + lt.getId());

            EmployeeLeaveBalanceDTO dto = new EmployeeLeaveBalanceDTO();
            dto.setId(lt.getId());
            dto.setName(lt.getName());
            dto.setDescription(lt.getDescription());
            dto.setDefaultDays( lt.getDefaultDays());
            dto.setCarryForward(lt.isLeaveCarriedForward());
            dto.setEndDate(lt.getEndDate());
            dto.setCreatedAt(lt.getCreatedAt());  // <-- Add this line to include createdAt

            // Use actual balance if found, else use default
            EmployeeLeaveBalance balance = balanceMap.get(lt.getId());
            if (balance != null) {
                dto.setRemainingDays(balance.getRemainingDays());
                System.out.println("[INFO] Using saved balance: " + balance.getRemainingDays());
            } else {
                dto.setRemainingDays((float) lt.getDefaultDays());
                System.out.println("[INFO] No existing balance. Using defaultDays: " + lt.getDefaultDays());
            }

            result.add(dto);
        }

        System.out.println("[SERVICE] Final DTO list size: " + result.size());
        return result;
    }



}