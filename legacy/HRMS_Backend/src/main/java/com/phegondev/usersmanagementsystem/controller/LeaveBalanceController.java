package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.entity.LeaveBalance;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.service.LeaveBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leave-balance")
public class LeaveBalanceController {

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    // Get current user's leave balance for current year
    @GetMapping("/my-balance")
    public ResponseEntity<LeaveBalance> getMyLeaveBalance() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OurUsers user = (OurUsers) authentication.getPrincipal();
        String empId = user.getEmpId();
        
        LeaveBalance balance = leaveBalanceService.getOrCreateLeaveBalance(empId);
        return ResponseEntity.ok(balance);
    }

    // Get leave balance for specific employee and year
    @GetMapping("/{empId}/{year}")
    public ResponseEntity<LeaveBalance> getLeaveBalance(
            @PathVariable String empId,
            @PathVariable int year) {
        LeaveBalance balance = leaveBalanceService.getLeaveBalance(empId, year);
        return ResponseEntity.ok(balance);
    }

    // Get all leave balances for an employee
    @GetMapping("/employee/{empId}")
    public ResponseEntity<List<LeaveBalance>> getLeaveBalancesByEmployee(
            @PathVariable String empId) {
        List<LeaveBalance> balances = leaveBalanceService.getLeaveBalancesByEmployee(empId);
        return ResponseEntity.ok(balances);
    }

    // Search leave balances with filters
    @GetMapping("/search")
    public ResponseEntity<List<LeaveBalance>> searchLeaveBalances(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String employeeId) {
        List<LeaveBalance> balances = leaveBalanceService.searchLeaveBalances(year, employeeId);
        return ResponseEntity.ok(balances);
    }
}