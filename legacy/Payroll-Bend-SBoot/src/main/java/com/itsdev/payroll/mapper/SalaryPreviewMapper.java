package com.itsdev.payroll.mapper;

import com.itsdev.payroll.dto.employee.preview.*;
import com.itsdev.payroll.entity.employee.*;
import com.itsdev.payroll.entity.salarycomponents.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SalaryPreviewMapper {

    private SalaryPreviewMapper() {}

    // --- Earning mapper (employee row + master) ---
    public static PreviewEarningDTO toPreviewEarning(EmployeeEarning empEarning) {
        PreviewEarningDTO dto = new PreviewEarningDTO();
        if (empEarning == null) return dto;

        Earning master = empEarning.getEarning();

        if (master != null) {
            dto.setId(master.getEarningId());
            dto.setName(master.getDisplayName() != null ? master.getDisplayName() : master.getEarningName());
            dto.setIsAmountInPercentage(master.getIsAmountInPercentage());
            dto.setIsIncludedInPf(master.getIsIncludedInEpf());
            dto.setIsIncludedInEsi(master.getIsIncludedInEsi());
            dto.setIsAssociatedWithEmployee(master.getIsAssociatedWithEmployee());
            dto.setIsScheduledEarning(master.getIsScheduledEarning());
            dto.setIsVariable(master.getIsVariable());
            dto.setEarningFrequency(master.getEarningFrequency());
        } else {
            dto.setId(null);
            dto.setName(null);
            dto.setIsAmountInPercentage(false);
            dto.setIsIncludedInPf(false);
            dto.setIsIncludedInEsi(false);
            dto.setIsAssociatedWithEmployee(false);
            dto.setIsScheduledEarning(false);
            dto.setIsVariable(false);
            dto.setEarningFrequency(null);
        }

        dto.setEnabled(empEarning.getEnabled());
        dto.setAmount(empEarning.getAmount());
        dto.setAmountInPercentage(empEarning.getAmountInPercentage());

        return dto;
    }

    // --- Earning mapper (master only) ---
    public static PreviewEarningDTO toPreviewEarningFromMaster(Earning master) {
        PreviewEarningDTO dto = new PreviewEarningDTO();
        if (master == null) return dto;

        dto.setId(master.getEarningId());
        dto.setName(master.getDisplayName() != null ? master.getDisplayName() : master.getEarningName());
        dto.setIsAmountInPercentage(master.getIsAmountInPercentage());
        dto.setIsIncludedInPf(master.getIsIncludedInEpf());
        dto.setIsIncludedInEsi(master.getIsIncludedInEsi());
        dto.setIsAssociatedWithEmployee(master.getIsAssociatedWithEmployee());
        dto.setIsScheduledEarning(master.getIsScheduledEarning());
        dto.setIsVariable(master.getIsVariable());
        dto.setEarningFrequency(master.getEarningFrequency());

        dto.setEnabled(false);
        dto.setAmount(0.0);
        dto.setAmountInPercentage(null);

        return dto;
    }

    // --- Benefit mapper (employee row + master) ---
    public static PreviewBenefitDTO toPreviewBenefit(EmployeeBenefit empBenefit) {
        PreviewBenefitDTO dto = new PreviewBenefitDTO();
        if (empBenefit == null) return dto;

        Benefit master = empBenefit.getBenefit();
        if (master != null) {
            dto.setId(master.getBenefitId());
            dto.setName(master.getBenefitName());
        }

        dto.setEnabled(empBenefit.getEnabled());
        dto.setAmount(empBenefit.getAmount());
        dto.setAmountInPercentage(empBenefit.getAmountInPercentage());

        return dto;
    }

    // --- Benefit mapper (master only) ---
    public static PreviewBenefitDTO toPreviewBenefitFromMaster(Benefit master) {
        PreviewBenefitDTO dto = new PreviewBenefitDTO();
        if (master == null) return dto;

        dto.setId(master.getBenefitId());
        dto.setName(master.getBenefitName());
        dto.setEnabled(false);
        dto.setAmount(0.0);
        dto.setAmountInPercentage(null);

        return dto;
    }

    // --- Reimbursement mapper (employee row + master) ---
    public static PreviewReimbursementDTO toPreviewReimbursement(EmployeeReimbursement empReimbursement) {
        PreviewReimbursementDTO dto = new PreviewReimbursementDTO();
        if (empReimbursement == null) return dto;

        Reimbursement master = empReimbursement.getReimbursement();
        if (master != null) {
            dto.setId(master.getReimbursementId());
            dto.setName(master.getReimbursementName());
        }

        dto.setEnabled(empReimbursement.getEnabled());
        dto.setAmount(empReimbursement.getAmount());
        dto.setCarryForwardOption(empReimbursement.getCarryForwardOption());

        return dto;
    }

    // --- Reimbursement mapper (master only) ---
    public static PreviewReimbursementDTO toPreviewReimbursementFromMaster(Reimbursement master) {
        PreviewReimbursementDTO dto = new PreviewReimbursementDTO();
        if (master == null) return dto;

        dto.setId(master.getReimbursementId());
        dto.setName(master.getReimbursementName());
        dto.setEnabled(false);
        dto.setAmount(0.0);
        dto.setCarryForwardOption(null);

        return dto;
    }

    // --- Variable earning mapper (no master) ---
    public static PreviewVariableEarningDTO toPreviewVariable(VariableEarning v) {
        PreviewVariableEarningDTO dto = new PreviewVariableEarningDTO();
        if (v == null) return dto;

        dto.setId(v.getId() != null ? v.getId().toString() : null);
        dto.setVariableCode(v.getVariableCode());
        dto.setEnabled(v.getEnabled());
        dto.setAmount(v.getAmount());
        dto.setAmountInPercentage(v.getAmountInPercentage());

        return dto;
    }

    // --- FBP mapper (no master) ---
    public static PreviewFbpDTO toPreviewFbp(FbpComponent f) {
        PreviewFbpDTO dto = new PreviewFbpDTO();
        if (f == null) return dto;

        dto.setId(f.getId() != null ? f.getId().toString() : null);
        dto.setComponentCode(f.getComponentCode());
        dto.setEnabled(f.getEnabled());
        dto.setAmount(f.getAmount());

        return dto;
    }

    // --- Top-level: build EmployeePreviewDTO from a CtcStructure entity ---
    public static EmployeePreviewDTO toEmployeePreview(CtcStructure ctc) {
        EmployeePreviewDTO dto = new EmployeePreviewDTO();
        if (ctc == null) return dto;

        if (ctc.getEmployee() != null) {
            dto.setEmployeeId(ctc.getEmployee().getEmployeeId());
            dto.setEmployeeNumber(ctc.getEmployee().getEmployeeNumber());
            dto.setEmployeeName(ctc.getEmployee().getFirstName() + " " + ctc.getEmployee().getLastName());
            dto.setEmployeeStatus(ctc.getEmployee().getEmployeeStatus());
        }

        dto.setCtc(ctc.getAnnualCtc());

        List<PreviewEarningDTO> earnings = Optional.ofNullable(ctc.getEarnings())
                .orElse(Collections.emptyList())
                .stream()
                .map(SalaryPreviewMapper::toPreviewEarning)
                .collect(Collectors.toList());
        dto.setEarnings(earnings);

        List<PreviewBenefitDTO> benefits = Optional.ofNullable(ctc.getBenefits())
                .orElse(Collections.emptyList())
                .stream()
                .map(SalaryPreviewMapper::toPreviewBenefit)
                .collect(Collectors.toList());
        dto.setBenefits(benefits);

        List<PreviewReimbursementDTO> reimbursements = Optional.ofNullable(ctc.getReimbursements())
                .orElse(Collections.emptyList())
                .stream()
                .map(SalaryPreviewMapper::toPreviewReimbursement)
                .collect(Collectors.toList());
        dto.setReimbursements(reimbursements);

        List<PreviewVariableEarningDTO> variableEarnings = Optional.ofNullable(ctc.getVariableEarnings())
                .orElse(Collections.emptyList())
                .stream()
                .map(SalaryPreviewMapper::toPreviewVariable)
                .collect(Collectors.toList());
        dto.setVariableEarnings(variableEarnings);

        List<PreviewFbpDTO> fbp = Optional.ofNullable(ctc.getFbpComponents())
                .orElse(Collections.emptyList())
                .stream()
                .map(SalaryPreviewMapper::toPreviewFbp)
                .collect(Collectors.toList());
        dto.setFbpComponents(fbp);

        dto.setEarningsTotal(earnings.stream().mapToDouble(e -> e.getAmount() == null ? 0.0 : e.getAmount()).sum());
        dto.setBenefitsTotal(benefits.stream().mapToDouble(b -> b.getAmount() == null ? 0.0 : b.getAmount()).sum());
        dto.setReimbursementsTotal(reimbursements.stream().mapToDouble(r -> r.getAmount() == null ? 0.0 : r.getAmount()).sum());
        dto.setFbpTotal(fbp.stream().mapToDouble(f -> f.getAmount() == null ? 0.0 : f.getAmount()).sum());

        dto.setGrossAmount(dto.getEarningsTotal() + dto.getBenefitsTotal() + dto.getFbpTotal() + dto.getReimbursementsTotal());
        dto.setMonthlySalary(dto.getGrossAmount() / 12.0);

        return dto;
    }
}
