package com.phegondev.usersmanagementsystem.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.phegondev.usersmanagementsystem.entity.EmployeeLeaveBalance;
import com.phegondev.usersmanagementsystem.entity.EmployeeMonthlyLop;
import com.phegondev.usersmanagementsystem.repository.EmployeeLeaveBalanceRepo;
import com.phegondev.usersmanagementsystem.repository.EmployeeMonthlyLopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.phegondev.usersmanagementsystem.config.HashUtil;
import com.phegondev.usersmanagementsystem.dto.IntegrateWithPayroll.EmployeeLeaveRequestDTO;
import com.phegondev.usersmanagementsystem.dto.IntegrateWithPayroll.EmployeeLeaveResponseDTO;
import com.phegondev.usersmanagementsystem.entity.LeaveRequests;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.leaverequest.LeaveRequestRepo;

@RestController
@RequestMapping("/public")
public class IntegrateWithPayroll {

    @Value("${security.api.key}")
	private String validApiKey;

    @Autowired
    private UsersRepo ourUsersRepository;

    @Autowired
    private LeaveRequestRepo leaveRequestsRepository;

    @Autowired
    private EmployeeLeaveBalanceRepo employeeLeaveBalanceRepo;

    @Autowired
    private EmployeeMonthlyLopRepository employeeMonthlyLopRepository;

    @GetMapping("/test-hrms")
    public String sendResponse() {
        return "Hello Payroll! Response from HRMS Service.";
    }

    // @PostMapping("/get-leave")
    // public List<EmployeeLeaveResponseDTO> getLeaveData(@RequestBody EmployeeLeaveRequestDTO request) {
    //     return request.getEmployeeEmails().stream()
    //             .map(email -> {
    //                 EmployeeLeaveResponseDTO dto = new EmployeeLeaveResponseDTO();
    //                 dto.setEmployeeEmail(email);
    //                 dto.setTotalLeaves((int) (Math.random() * 5)); // random test value
    //                 return dto;
    //             })
    //             .collect(Collectors.toList());
    // }

    @PostMapping("/get-employee-leaves")
    public ResponseEntity<?> getLeaveData(
        @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
        @RequestBody EmployeeLeaveRequestDTO request) {

    // 🔐 Step 1: Verify API key
    System.out.println("Received request with API Key: " + apiKey);

    String expectedHash = HashUtil.md5(validApiKey);

    System.out.println("Expected = " + expectedHash);

    if (apiKey == null || !apiKey.equals(expectedHash)) {
        System.out.println("❌ Unauthorized access: Invalid or missing API key");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid or missing API key");
    }

    System.out.println("✅ API key verified successfully.");

    // 🧾 Step 2: Log request details
    System.out.println("📥 Received request for " + request.getEmployeeEmails().size() +
            " employees, payPeriod=" + request.getPayPeriod());

    // 🗓 Step 3: Parse payPeriod (e.g., "July 2025")
    String payPeriod = request.getPayPeriod().trim();
    YearMonth payMonth;
    try {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
        payMonth = YearMonth.parse(payPeriod, formatter);
    } catch (Exception e) {
        System.out.println("❌ Invalid payPeriod format: " + payPeriod + " (expected 'MMMM yyyy')");
        return ResponseEntity.badRequest()
                .body("Invalid payPeriod format. Expected format like 'July 2025'");
    }

    LocalDate monthStart = payMonth.atDay(1);
    LocalDate monthEnd = payMonth.atEndOfMonth();
    System.out.println("🗓 Calculating leaves between " + monthStart + " and " + monthEnd);

    // 👥 Step 4: Process each employee
    List<EmployeeLeaveResponseDTO> responses = request.getEmployeeEmails().stream()
            .map(email -> {
                EmployeeLeaveResponseDTO dto = new EmployeeLeaveResponseDTO();
                dto.setEmployeeEmail(email);

                // Find empId by email
                Optional<OurUsers> userOpt = ourUsersRepository.findByEmail(email);
                if (userOpt.isEmpty()) {
                    System.out.println("⚠️ No user found for email: " + email);
                    dto.setTotalLeaves(0.0);
                    return dto;
                }

                String empId = userOpt.get().getEmpId();
                System.out.println("✅ Found empId=" + empId + " for email=" + email);

                // Fetch leave requests
                List<LeaveRequests> leaves = leaveRequestsRepository.findByEmployeeId(empId);

//                // Filter approved leaves overlapping the pay period
//                // 1️⃣ Collect approved leaves overlapping pay period
//                List<LeaveRequests> approvedLeaves = leaves.stream()
//                        .filter(lr -> lr.getReportingManagerStatus() == LeaveRequestStatus.APPROVED
//                                && lr.getHrStatus() == LeaveRequestStatus.APPROVED)
//                        .filter(lr -> !(lr.getToDate().isBefore(monthStart) || lr.getFromDate().isAfter(monthEnd)))
//                        .toList();

//// 2️⃣ Sum leave days per leaveTypeId (from manualDaysAllocation)
//                Map<Long, Double> usedLeaveByType = new HashMap<>();
//
//                for (LeaveRequests lr : approvedLeaves) {
//                    if (lr.getManualDaysAllocation() != null) {
//                        lr.getManualDaysAllocation().forEach((leaveTypeId, days) -> {
//                            usedLeaveByType.merge(leaveTypeId, days.doubleValue(), Double::sum);
//                        });
//                    }
//                }
//
//                List<EmployeeLeaveBalance> balances =
//                        employeeLeaveBalanceRepo.findByEmployeeId(empId);
//
//                Map<Long, Double> balanceByType = new HashMap<>();
//                for (EmployeeLeaveBalance b : balances) {
//                    balanceByType.put(b.getLeaveTypeId(), b.getRemainingDays().doubleValue());
//                }

//                // 3️⃣ Calculate LOP days (excess leave beyond balance)
//                double lopDays = 0.0;
//
//                for (Map.Entry<Long, Double> entry : usedLeaveByType.entrySet()) {
//                    Long leaveTypeId = entry.getKey();
//                    double usedDays = entry.getValue();
//
//                    double availableDays = balanceByType.getOrDefault(leaveTypeId, 0.0);
//
//                    if (usedDays > availableDays) {
//                        lopDays += (usedDays - availableDays);
//                    }
//                }
//
//
//                dto.setTotalLeaves((int) Math.ceil(lopDays));
                List<EmployeeMonthlyLop> lops =
                        employeeMonthlyLopRepository
                                .findByEmployeeIdAndYearAndMonth(
                                        empId,
                                        payMonth.getYear(),
                                        payMonth.getMonthValue());

                double totalLop =
                        lops.stream()
                                .mapToDouble(EmployeeMonthlyLop::getLopDays)
                                .sum();

                dto.setTotalLeaves(totalLop);
                return dto;

            })
            .collect(Collectors.toList());

    // ✅ Step 5: Return response
    return ResponseEntity.ok(responses);
}



}
