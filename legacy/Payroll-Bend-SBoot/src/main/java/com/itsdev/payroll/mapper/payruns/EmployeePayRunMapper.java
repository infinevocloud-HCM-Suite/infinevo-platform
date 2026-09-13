package com.itsdev.payroll.mapper.payruns;

import com.itsdev.payroll.dto.employee.EpfComponentDTO;
import com.itsdev.payroll.dto.payruns.EmployeePayRunDTO;
import com.itsdev.payroll.entity.employee.CtcEpfComponent;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.payruns.EmployeePayRun;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EmployeePayRunMapper {

    public static EmployeePayRun toEntity(EmployeePayRunDTO dto) {
        EmployeePayRun e = new EmployeePayRun();
        e.setEmployeeId(dto.getEmployeeId());
        e.setEmployeeNumber(dto.getEmployeeNumber());
        e.setEmployeeName(dto.getEmployeeName());
        e.setFullName(dto.getFullName());
        e.setPaymentStatus(dto.getPaymentStatus());
        e.setPaymentMode(dto.getPaymentMode());
        e.setTotalEarnings(dto.getTotalEarnings());
        e.setTotalDeductions(dto.getTotalDeductions());
        e.setTotalTaxes(dto.getTotalTaxes());
        e.setTotalBenefits(dto.getTotalBenefits());
        e.setTotalReimbursements(dto.getTotalReimbursements());
        e.setNetPay(dto.getNetPay());
        e.setMonthlySalary(dto.getMonthlySalary());
        e.setPaidDays(dto.getPaidDays());
        e.setEmployeeStatus(dto.getEmployeeStatus());
        return e;
    }

    // ✅ UPDATED: pass ACTIVE CTC as parameter
    public static EmployeePayRunDTO toDto(EmployeePayRun e, CtcStructure activeCtc) {

        EmployeePayRunDTO dto = new EmployeePayRunDTO();
        dto.setEmployeeId(e.getEmployeeId());
        dto.setEmployeeNumber(e.getEmployeeNumber());
        dto.setEmployeeName(e.getEmployeeName());
        dto.setFullName(e.getFullName());
        dto.setPaymentStatus(e.getPaymentStatus());
        dto.setPaymentMode(e.getPaymentMode());
        dto.setTotalEarnings(e.getTotalEarnings());
        dto.setTotalDeductions(e.getTotalDeductions());
        dto.setTotalTaxes(e.getTotalTaxes());
        dto.setTotalBenefits(e.getTotalBenefits());
        dto.setTotalReimbursements(e.getTotalReimbursements());
        dto.setNetPay(e.getNetPay());
        dto.setMonthlySalary(e.getMonthlySalary());
        dto.setPaidDays(e.getPaidDays());
        dto.setMonthlyTds(e.getMonthlyTds());
        dto.setBonus(e.getBonus());
        dto.setBonusEarningExistForEmployee(e.getBonusEarningExistForEmployee());

        if (e.getEmployee() != null) {
            dto.setEligibleForPt(e.getEmployee().getEligibleForPt());
        }

        dto.setEmployeeStatus(e.getEmployeeStatus());
        dto.setTotalNoOfLeaves(e.getTotalNoOfLeaves());
        dto.setLOP(e.getLOP());
        dto.setClaimDeduction(e.getClaimDeduction());
        dto.setClaimReimbursement(e.getClaimReimbursement());
        dto.setClaimDeductionStatus(e.getClaimDeductionStatus());
        dto.setClaimReimbursementStatus(e.getClaimReimbursementStatus());

        // ✅ EPF components mapping (read-only, from ACTIVE CTC)
        if (activeCtc != null
                && activeCtc.getEpfComponents() != null
                && !activeCtc.getEpfComponents().isEmpty()) {

            List<EpfComponentDTO> epfList = activeCtc.getEpfComponents().stream()
                    .map((CtcEpfComponent epf) -> {
                        EpfComponentDTO epfDto = new EpfComponentDTO();
                        epfDto.setComponentCode(epf.getComponentCode());
                        epfDto.setComponentLabel(epf.getComponentLabel());
                        epfDto.setMonthlyAmount(epf.getMonthlyAmount());
                        epfDto.setAnnualAmount(epf.getAnnualAmount());

                        // optional fields
                        try { epfDto.setPercentage(epf.getPercentage()); } catch (Exception ignore) {}
                        try { epfDto.setCalculationType(epf.getCalculationType()); } catch (Exception ignore) {}

                        return epfDto;
                    })
                    .collect(Collectors.toList());

            dto.setEpfComponents(epfList);

        } else {
            dto.setEpfComponents(Collections.emptyList());
        }

        return dto;
    }
}
