package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.entity.LeaveBalance;
import com.phegondev.usersmanagementsystem.entity.LeaveRequest;
import com.phegondev.usersmanagementsystem.repository.LeaveBalanceRepository;
import com.phegondev.usersmanagementsystem.repository.LeaveRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

@Service
public class LeaveBalanceService {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    // Get or create leave balance for employee for current year
    public LeaveBalance getOrCreateLeaveBalance(String employeeId) {
        int currentYear = Year.now().getValue();
        Optional<LeaveBalance> existingBalance = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, currentYear);
        
        if (existingBalance.isPresent()) {
            return existingBalance.get();
        } else {
            LeaveBalance newBalance = new LeaveBalance();
            newBalance.setEmployeeId(employeeId);
            return leaveBalanceRepository.save(newBalance);
        }
    }

    // Get leave balance for specific employee and year
    public LeaveBalance getLeaveBalance(String employeeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseThrow(() -> new RuntimeException("Leave balance not found"));
    }

    // Get all leave balances for an employee
    public List<LeaveBalance> getLeaveBalancesByEmployee(String employeeId) {
        return leaveBalanceRepository.findByEmployeeId(employeeId);
    }

    // Get all leave balances for a year
    public List<LeaveBalance> getLeaveBalancesByYear(int year) {
        return leaveBalanceRepository.findByYear(year);
    }

    // Search leave balances with filters
    public List<LeaveBalance> searchLeaveBalances(Integer year, String employeeId) {
        return leaveBalanceRepository.findByYearAndEmployeeId(year, employeeId);
    }

    // Update leave balance when a leave is approved
    @Transactional
    public void updateLeaveBalanceOnApproval(LeaveRequest leaveRequest) {
        if (!"Approved".equalsIgnoreCase(leaveRequest.getStatus())) {
            return;
        }

        LocalDate fromDate = leaveRequest.getFromDate();
        int year = fromDate.getYear();
        String employeeId = leaveRequest.getEmployeeId();
        String leaveType = leaveRequest.getLeaveType();
        long days = leaveRequest.getFromDate().datesUntil(leaveRequest.getToDate()).count() + 1;

        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> {
                    LeaveBalance newBalance = new LeaveBalance();
                    newBalance.setEmployeeId(employeeId);
                    newBalance.setYear(year);
                    return leaveBalanceRepository.save(newBalance);
                });

        switch (leaveType.toLowerCase()) {
            case "annual":
                balance.setAnnualUsed(balance.getAnnualUsed() + (int) days);
                break;
            case "sick":
                balance.setSickUsed(balance.getSickUsed() + (int) days);
                break;
            case "casual":
                balance.setCasualUsed(balance.getCasualUsed() + (int) days);
                break;
            case "maternity":
                balance.setMaternityUsed(balance.getMaternityUsed() + (int) days);
                break;
            case "paternity":
                balance.setPaternityUsed(balance.getPaternityUsed() + (int) days);
                break;
        }

        leaveBalanceRepository.save(balance);
    }
}