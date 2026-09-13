package com.itsdev.payroll.controller.leave;



import com.itsdev.payroll.dto.leave.BulkLeaveAllocationRequestDTO;

import com.itsdev.payroll.dto.leave.EmployeeLeaveAllocationRequestDTO;

import com.itsdev.payroll.dto.leave.EmployeeLeaveAllocationResponseDTO;

import com.itsdev.payroll.dto.leave.LeaveAllocationImportResultDTO;
import com.itsdev.payroll.service.leave.EmployeeLeaveAllocationService;
import org.springframework.web.multipart.MultipartFile;

import com.itsdev.payroll.util.JWTUtil;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.*;



import java.util.HashMap;

import java.util.List;

import java.util.Map;



@RestController

@RequestMapping("/api/leave-allocation")

@CrossOrigin(origins = "*")

public class EmployeeLeaveAllocationController {



    private final EmployeeLeaveAllocationService leaveAllocationService;



    public EmployeeLeaveAllocationController(EmployeeLeaveAllocationService leaveAllocationService) {

        this.leaveAllocationService = leaveAllocationService;

    }



    @PostMapping("/bulk")

    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")

    public ResponseEntity<Map<String, Object>> saveBulkAllocations(

            @RequestHeader(value = "organizationId", required = true) String organizationId,

            @Valid @RequestBody BulkLeaveAllocationRequestDTO request) {



        String createdBy = JWTUtil.getCurrentUserName();

        List<EmployeeLeaveAllocationResponseDTO> result = leaveAllocationService.saveBulkAllocations(organizationId, request, createdBy);



        Map<String, Object> response = new HashMap<>();

        response.put("status", 200);

        response.put("message", "Leave allocations saved successfully");

        response.put("data", result);



        return ResponseEntity.ok(response);

    }



    @PutMapping("/{employeeId}")

    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")

    public ResponseEntity<Map<String, Object>> updateEmployeeAllocation(

            @RequestHeader(value = "organizationId", required = true) String organizationId,

            @PathVariable("employeeId") String employeeId,

            @RequestParam(value = "year", required = true) String year,

            @Valid @RequestBody EmployeeLeaveAllocationRequestDTO request) {



        String updatedBy = JWTUtil.getCurrentUserName();

        EmployeeLeaveAllocationResponseDTO result = leaveAllocationService.updateEmployeeAllocation(

                organizationId, employeeId, year, request.getLeaveTypes(), updatedBy);



        Map<String, Object> response = new HashMap<>();

        response.put("status", 200);

        response.put("message", "Leave allocation updated successfully");

        response.put("data", result);



        return ResponseEntity.ok(response);

    }



    @GetMapping

    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")

    public ResponseEntity<Map<String, Object>> getAllocations(

            @RequestHeader(value = "organizationId", required = true) String organizationId,

            @RequestParam(value = "year", required = false) String year,

            @RequestParam(value = "page", required = false) Integer page,

            @RequestParam(value = "size", required = false) Integer size,

            @RequestParam(value = "search", required = false) String search) {



        Map<String, Object> response = new HashMap<>();

        response.put("status", 200);

        response.put("message", "Leave allocations fetched successfully");



        if (page != null && size != null) {

            Map<String, Object> paginated = leaveAllocationService.getAllocationsPaginated(organizationId, year, search, page, size);

            response.put("data", paginated);

        } else {

            List<EmployeeLeaveAllocationResponseDTO> result = leaveAllocationService.getAllocations(organizationId, year);

            response.put("data", result);

        }



        return ResponseEntity.ok(response);

    }



    @GetMapping("/{employeeId}")

    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin') or #employeeId == authentication.name")

    public ResponseEntity<Map<String, Object>> getEmployeeAllocation(

            @RequestHeader(value = "organizationId", required = true) String organizationId,

            @PathVariable("employeeId") String employeeId,

            @RequestParam(value = "year", required = false) String year) {



        EmployeeLeaveAllocationResponseDTO result = leaveAllocationService.getEmployeeAllocation(organizationId, employeeId, year);



        Map<String, Object> response = new HashMap<>();

        response.put("status", 200);

        response.put("message", "Employee leave allocation fetched successfully");

        response.put("data", result);



        return ResponseEntity.ok(response);

    }



    @DeleteMapping("/{employeeId}")

    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")

    public ResponseEntity<Map<String, Object>> deleteEmployeeAllocation(

            @RequestHeader(value = "organizationId", required = true) String organizationId,

            @PathVariable("employeeId") String employeeId,

            @RequestParam(value = "year", required = false) String year) {



        leaveAllocationService.deleteEmployeeAllocation(organizationId, employeeId, year);



        Map<String, Object> response = new HashMap<>();

        response.put("status", 200);

        response.put("message", "Employee leave allocations deleted successfully");



        return ResponseEntity.ok(response);

    }



    @DeleteMapping("/{employeeId}/type/{leaveType}")

    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")

    public ResponseEntity<Map<String, Object>> deleteSingleLeaveType(

            @RequestHeader(value = "organizationId", required = true) String organizationId,

            @PathVariable("employeeId") String employeeId,

            @PathVariable("leaveType") String leaveType,

            @RequestParam(value = "year", required = false) String year) {



        leaveAllocationService.deleteSingleLeaveTypeAllocation(organizationId, employeeId, leaveType, year);



        Map<String, Object> response = new HashMap<>();

        response.put("status", 200);

        response.put("message", "Leave allocation for " + leaveType + " deleted successfully");



        return ResponseEntity.ok(response);

    }


    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN') or hasRole('HR') or hasRole('payroll_admin')")
    public ResponseEntity<Map<String, Object>> importNewAllocations(
            @RequestHeader(value = "organizationId", required = true) String organizationId,
            @RequestParam("file") MultipartFile file) {

        String createdBy = JWTUtil.getCurrentUserName();
        LeaveAllocationImportResultDTO result = leaveAllocationService.importNewAllocations(organizationId, file, createdBy);

        Map<String, Object> response = new HashMap<>();
        response.put("status", 200);
        response.put("message", "Leave allocations import processed");
        response.put("data", result);

        return ResponseEntity.ok(response);
    }

}
