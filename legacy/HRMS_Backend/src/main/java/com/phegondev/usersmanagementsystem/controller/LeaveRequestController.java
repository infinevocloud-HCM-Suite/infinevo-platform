package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.dto.EmployeeLeaveBalanceDTO;
import com.phegondev.usersmanagementsystem.dto.LeaveRequestDTO;
import com.phegondev.usersmanagementsystem.dto.LeaveRequestsDTO;
import com.phegondev.usersmanagementsystem.entity.*;
import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import com.phegondev.usersmanagementsystem.service.LeaveBalanceService;
import com.phegondev.usersmanagementsystem.service.LeaveRequestService;
import com.phegondev.usersmanagementsystem.service.LeaveTypeService;

import com.phegondev.usersmanagementsystem.service.leaverequest.LeaveRequestsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Collectors;


import java.util.List;
import java.util.Optional;

@RestController
public class LeaveRequestController {



	private final LeaveBalanceService leaveBalanceService;
	private final LeaveTypeService leaveTypeService;
	private final LeaveRequestService service;
	private final LeaveRequestsService leaveRequestsService;

	public LeaveRequestController(
				LeaveBalanceService leaveBalanceService,
				LeaveTypeService leaveTypeService,
				LeaveRequestService service,
				LeaveRequestsService leaveRequestsService) {
			this.leaveBalanceService = leaveBalanceService;
			this.leaveTypeService = leaveTypeService;
			this.service = service;
			this.leaveRequestsService = leaveRequestsService;
		}

	// Get all leave requests
	//@GetMapping("/leaves/all")
	public List<LeaveRequest> getAllLeaveRequestss(
			@RequestParam(required = false) String employeeName,
			@RequestParam(required = false) String leaveType) {

		if (employeeName != null && leaveType != null) {
			return service.getByEmployeeNameAndLeaveType(employeeName, leaveType);
		} else if (employeeName != null) {
			return service.getByEmployeeName(employeeName);
		} else if (leaveType != null) {
			return service.getByLeaveType(leaveType);
		} else {
			return service.getAll();
		}
	}

	@GetMapping("/leaves/all")
	public List<LeaveRequestsDTO> getAllLeaveRequests(
			@RequestParam(required = false) String employeeName,
			@RequestParam(required = false) String leaveType) {

		return leaveRequestsService.getAllLeaveRequest();

	}

	@GetMapping("/leaves/reporting-manager")
	public List<LeaveRequestsDTO> getLeaveRequestsByReportingManager() {
		System.out.println("[CONTROLLER] Entered /leaves/reporting-manager endpoint.");

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		System.out.println("[AUTH] Retrieved authentication: " + authentication.getName());

		OurUsers user = (OurUsers) authentication.getPrincipal();
		String reportingManagerEmployeeId = user.getEmpId();
		System.out.println("[AUTH] Logged-in user ID (reporting manager): " + reportingManagerEmployeeId);

		System.out.println("[INFO] Calling leaveRequestsService.getLeaveRequestsByReportingManager() for reporting manager: " + reportingManagerEmployeeId);
		List<LeaveRequestsDTO> result = leaveRequestsService.getLeaveRequestsByReportingManager(reportingManagerEmployeeId);

		System.out.println("[SUCCESS] Retrieved " + result.size() + " leave requests for reporting manager: " + reportingManagerEmployeeId);
		return result;
	}



	// Create a new leave request
//	@PostMapping("/leaves/add")
	public ResponseEntity<LeaveRequest> createLeaveRequest(@RequestBody LeaveRequestDTO dto) {
		return ResponseEntity.ok(service.save(dto));
	}

	@PostMapping(value = "/leaves/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<LeaveRequests> createLeaveRequest(
			@RequestPart("leaveRequest") LeaveRequestsDTO dto,
			@RequestPart(value = "file", required = false) MultipartFile file) {

		System.out.println("[CONTROLLER] Received leave request from employee ID: " + dto.getEmployeeId());
		if (file != null && !file.isEmpty()) {
			System.out.println("[CONTROLLER] Received file: " + file.getOriginalFilename() + " (type: " + file.getContentType() + ")");
		} else {
			System.out.println("[CONTROLLER] No medical certificate file uploaded.");
		}

		System.out.println("[REQUEST DATA] : " + dto);
		LeaveRequests saved = leaveRequestsService.saveLeaveRequest(dto, file);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}


	// Update the status of a leave request by ID
	// @PutMapping("/{id}/status")
	// public ResponseEntity<LeaveRequest> updateStatus(
	// @PathVariable Long id,
	// @RequestParam String status) {
	// return service.updateStatus(id, status)
	// .map(ResponseEntity::ok)
	// .orElse(ResponseEntity.notFound().build());
	// }

// //   @PutMapping("/admin/leaves/{id}/status")
//  public ResponseEntity<LeaveRequest> updateStatus(
//          @PathVariable Long id,
//          @RequestParam String status) {
//      Optional<LeaveRequest> updatedRequest = service.updateStatus(id, status);

//      if (updatedRequest.isPresent() && "Approved".equalsIgnoreCase(status)) {
//          leaveBalanceService.updateLeaveBalanceOnApproval(updatedRequest.get());
//      }

//      return updatedRequest
//              .map(ResponseEntity::ok)
//              .orElse(ResponseEntity.notFound().build());
//  }

	//@PutMapping("/leaves/{id}/status")
	public ResponseEntity<LeaveRequest> updateLeaveRequestStatus(
			@PathVariable("id") Long requestId,
			@RequestParam("status") String newStatus) {
		try {
			LeaveRequest updatedRequest = service.updateLeaveRequestStatus(requestId, newStatus.toUpperCase());
			return ResponseEntity.ok(updatedRequest);
		} catch (IllegalArgumentException | IllegalStateException e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

@PutMapping("/leaves/{id}/status")
public ResponseEntity<LeaveRequestsDTO> updateLeaveRequestStatus(
        @PathVariable Long id,
        @RequestParam("status") LeaveRequestStatus newStatus,
        @RequestParam(required = false) String comment
) {
    System.out.println("[CONTROLLER] PUT /leaves/" + id + "/status endpoint.");

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    OurUsers user = (OurUsers) authentication.getPrincipal();

    // Get all role names and normalize to lowercase for comparison
    List<String> roles = user.getRoleNames().stream()
            .map(String::toLowerCase)
            .collect(Collectors.toList());

    String employeeId = user.getEmpId();

    System.out.println("[AUTH] Authenticated user: " + user.getUsername());
    System.out.println("[AUTH] User roles: " + roles);
    System.out.println("[AUTH] User employee ID: " + employeeId);

    // 1) Load current leave state
    LeaveRequestsDTO current = leaveRequestsService.getLeaveRequestById(id);
    if (current == null) {
        System.out.println("[ERROR] Leave request ID " + id + " not found.");
        return ResponseEntity.notFound().build();
    }

    // 2) Normalize role checks
    boolean hasRmRole = roles.contains("reporting manager")
            || roles.contains("reporting_manager")
            || roles.contains("reporting-manager");

    boolean hasHrRole = roles.contains("hr");

    // 3) Determine actingRole based on stage + roles
    String actingRole = null;

    // If RM stage is still pending and user is RM → act as reporting manager
    if (hasRmRole
            && current.getReportingManagerStatus() == LeaveRequestStatus.PENDING) {
        actingRole = "reporting manager";
    }
	// HR can act once RM has taken ANY decision
    else if (hasHrRole
            && current.getReportingManagerStatus() != LeaveRequestStatus.PENDING
            && current.getHrStatus() == LeaveRequestStatus.PENDING) {
        actingRole = "hr";
    }

    if (actingRole == null) {
        System.out.println("[AUTH] User not allowed to update this leave in current state. Roles: " + roles
                + ", RM Status: " + current.getReportingManagerStatus()
                + ", HR Status: " + current.getHrStatus());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // 4) Call service with resolved actingRole
    System.out.println("[API] Request to update leave request ID: " + id + " to status: "
            + newStatus + " by role: " + actingRole);

    LeaveRequestsDTO dto = leaveRequestsService.updateLeaveRequestStatus(id, newStatus, actingRole, comment);

    System.out.println("[SUCCESS] Leave request ID " + id + " updated successfully with status: "
            + newStatus + " by role: " + actingRole);
    return ResponseEntity.ok(dto);
}




	// Delete a leave request by ID
	// @DeleteMapping("/{id}")
	// public ResponseEntity<Void> deleteLeaveRequest(@PathVariable Long id) {
	// service.deleteById(id);
	// return ResponseEntity.noContent().build();
	// }

	// Get by employee ID
	//@GetMapping("/leaves")
	public ResponseEntity<List<LeaveRequest>> getLeaveRequestsByEmployeeIds() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		OurUsers user = (OurUsers) authentication.getPrincipal();
		String empId = user.getEmpId();
		System.out.println("🔐 Authenticated user empId: " + empId);
		List<LeaveRequest> requests = service.getByEmployeeId(empId);
		return requests.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(requests);
	}

	@GetMapping("/leaves")
	public ResponseEntity<List<LeaveRequestsDTO>> getLeaveRequestsByEmployeeId() {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		OurUsers user = (OurUsers) authentication.getPrincipal();
		String employeeId = user.getEmpId();

		System.out.println("[INFO] Fetching leave requests for employee: " + employeeId);
		List<LeaveRequestsDTO> dtos = leaveRequestsService.getLeaveRequestsByEmployeeId(employeeId);

		if (dtos.isEmpty()) {
			System.out.println("[INFO] No leave requests found for employee: " + employeeId);
			return ResponseEntity.noContent().build();
		}

		System.out.println("[INFO] Found " + dtos.size() + " leave requests for employee: " + employeeId);
		return ResponseEntity.ok(dtos);
	}

	//    @GetMapping("/leave-types")
	//     public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
	//         return ResponseEntity.ok(leaveTypeService.getAllLeaveTypes());
	//     }


	//@GetMapping("/leaves/{id}")
	public ResponseEntity<LeaveRequest> getLeaveRequestByIdS(@PathVariable Long id) {
		Optional<LeaveRequest> leaveRequest = service.findById(id);
		return leaveRequest.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/leaves/{id}")
	public ResponseEntity<LeaveRequestsDTO> getLeaveRequestById(@PathVariable Long id) {
		LeaveRequestsDTO dto = leaveRequestsService.getLeaveRequestById(id);
		if (dto != null) {
			return ResponseEntity.ok(dto);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@PutMapping("/leaves/{id}")
	public ResponseEntity<LeaveRequest> updateLeaveRequest(
			@PathVariable Long id,
			@RequestBody LeaveRequest leaveRequest) {
		try {
			LeaveRequest updatedRequest = service.updateLeaveRequest(id, leaveRequest);
			return ResponseEntity.ok(updatedRequest);
		} catch (Exception e) {
			return ResponseEntity.badRequest().build();
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteLeaveRequest(@PathVariable Long id) {
		service.deleteById(id);
		return ResponseEntity.noContent().build();
	}


	//leaves-types controller

	@GetMapping("/my-leave-types")
	public ResponseEntity<List<EmployeeLeaveBalanceDTO>> getMyApplicableLeaveTypes() {
		String employeeId = getCurrentEmployeeId(); // e.g., from JWT or SecurityContext
		System.out.println("[GET] Fetching leave types for current employeeId: " + employeeId);
		List<EmployeeLeaveBalanceDTO> applicableLeaveTypes = leaveTypeService.getLeaveBalancesForEmployee(employeeId);
		return ResponseEntity.ok(applicableLeaveTypes);
	}

	@GetMapping("/employee-leave-types/{employeeId}")
	public ResponseEntity<List<EmployeeLeaveBalanceDTO>> getEmployeeApplicableLeaveTypes(@PathVariable String employeeId) {
		System.out.println("[GET] Fetching employee leave types for current employeeId: " + employeeId);
		List<EmployeeLeaveBalanceDTO> applicableLeaveTypes = leaveTypeService.getLeaveBalancesForEmployee(employeeId);
		return ResponseEntity.ok(applicableLeaveTypes);
	}


	private String getCurrentEmployeeId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		OurUsers user = (OurUsers) authentication.getPrincipal();
		String empId = user.getEmpId();
		System.out.println(" user emp id: " + user.getEmpId());
		return empId;
	}



/*
	@GetMapping("/leaves/{id}/medical-certificate")
	public ResponseEntity<byte[]> getMedicalCertificate(@PathVariable Long id) {
		try {
			LeaveDocument document = leaveRequestsService.getMedicalCertificate(id);
			if (document == null) {
				return ResponseEntity.notFound().build();
			}

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.parseMediaType(document.getFileType()));
			headers.setContentDisposition(
					ContentDisposition.builder("attachment")
							.filename(document.getFileName())
							.build());

			return new ResponseEntity<>(document.getFileData(), headers, HttpStatus.OK);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	} */





























}
