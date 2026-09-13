package com.itsdev.payroll.mapper.payruns;

import com.itsdev.payroll.dto.payruns.PayRunDTO;
import com.itsdev.payroll.dto.payruns.PersistedPayRunDTO;
import com.itsdev.payroll.dto.payruns.ReadyPayRunDTO;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PayRunMapper {

    private static String computeProcessingPeriod(LocalDate payPeriodStartDate) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM yyyy");
        return payPeriodStartDate.format(fmt);
    }

    public static PayRunDTO toDto(PayRun entity) {
        if (entity == null) return null;
        PayRunDTO dto = new PayRunDTO();
        dto.setPayrunId(entity.getPayrunId());
        dto.setType(entity.getType());
        dto.setStatus(entity.getStatus());
        dto.setPaymentDue(entity.getPaymentDue());
        dto.setPaymentStatus(entity.getPaymentStatus());
        dto.setPayPeriodStartDate(entity.getPayPeriodStartDate());
        dto.setPayPeriodEndDate(entity.getPayPeriodEndDate());
        dto.setPayDate(entity.getPayDate());
        dto.setProcessingPeriod(entity.getProcessingPeriod());
        dto.setPayrollTotal(entity.getPayrollTotal());
        dto.setStatusInfo(entity.getStatusInfo());
        dto.setNoOfEmployees(entity.getNoOfEmployees());
        dto.setApprovalType(entity.getApprovalType());
        dto.setApprovalDetails(entity.getApprovalDetails());
        dto.setApprovedDate(entity.getApprovedDate());
        dto.setCompensationName(entity.getCompensationName());

        dto.setTotalNetPay(entity.getTotalNetPay());
        dto.setTotalTaxes(entity.getTotalTaxes());
        dto.setTotalBenefits(entity.getTotalBenefits());
        dto.setTotalDonations(entity.getTotalDonations());
        dto.setTotalDeductions(entity.getTotalDeductions());
        dto.setTotalPayrollCost(entity.getTotalPayrollCost());
        dto.setTotalBonus(entity.getTotalBonus());

        dto.setTotalClaimDeduction(entity.getTotalClaimDeduction());
        dto.setTotalClaimReimbursement(entity.getTotalClaimReimbursement());

        dto.setCanEditPaydate(entity.getCanEditPaydate());
        dto.setCanPostPayrunTransactions(entity.getCanPostPayrunTransactions());
        dto.setHasDirectDepositPayments(entity.getHasDirectDepositPayments());
        dto.setHasNonDirectDepositPayments(entity.getHasNonDirectDepositPayments());

        dto.setEarningJson(entity.getEarningJson());
        dto.setVariablePayEarningsListJson(entity.getVariablePayEarningsListJson());
        dto.setDeductionsJson(entity.getDeductionsJson());
        dto.setExpenseBatchesDetailsJson(entity.getExpenseBatchesDetailsJson());

        dto.setRejectedReason(entity.getRejectedReason());
        
        // ⭐ Newly added EPF/ESI totals
        dto.setTotalEpfContribution(entity.getTotalEpfContribution());
        dto.setTotalEsiContribution(entity.getTotalEsiContribution());
        dto.setTotalEdliContribution(entity.getTotalEdliContribution());
        dto.setTotalEpfAdminCharges(entity.getTotalEpfAdminCharges());

        return dto;
    }

    public static PayRun toEntity(PayRunDTO dto) {
        if (dto == null) return null;
        PayRun e = new PayRun();
        e.setPayrunId(dto.getPayrunId());
        e.setType(dto.getType());
        e.setStatus(dto.getStatus());
        e.setPaymentDue(dto.getPaymentDue());
        e.setPaymentStatus(dto.getPaymentStatus());
        e.setPayPeriodStartDate(dto.getPayPeriodStartDate());
        e.setPayPeriodEndDate(dto.getPayPeriodEndDate());
        e.setPayDate(dto.getPayDate());
        e.setProcessingPeriod(dto.getProcessingPeriod());
        e.setPayrollTotal(dto.getPayrollTotal());
        e.setStatusInfo(dto.getStatusInfo());
        e.setNoOfEmployees(dto.getNoOfEmployees());
        e.setApprovalType(dto.getApprovalType());
        e.setApprovalDetails(dto.getApprovalDetails());
        e.setCompensationName(dto.getCompensationName());

        e.setTotalNetPay(dto.getTotalNetPay());
        e.setTotalTaxes(dto.getTotalTaxes());
        e.setTotalBenefits(dto.getTotalBenefits());
        e.setTotalDonations(dto.getTotalDonations());
        e.setTotalDeductions(dto.getTotalDeductions());
        e.setTotalPayrollCost(dto.getTotalPayrollCost());
        e.setTotalBonus(dto.getTotalBonus());

        e.setCanEditPaydate(dto.getCanEditPaydate());
        e.setCanPostPayrunTransactions(dto.getCanPostPayrunTransactions());
        e.setHasDirectDepositPayments(dto.getHasDirectDepositPayments());
        e.setHasNonDirectDepositPayments(dto.getHasNonDirectDepositPayments());

        e.setEarningJson(dto.getEarningJson());
        e.setVariablePayEarningsListJson(dto.getVariablePayEarningsListJson());
        e.setDeductionsJson(dto.getDeductionsJson());
        e.setExpenseBatchesDetailsJson(dto.getExpenseBatchesDetailsJson());

        return e;
    }

    // Existing mapper (for persisted payruns) use only in get all api
    public static PersistedPayRunDTO toDTO(PayRun entity) {
        if (entity == null) return null;

        PersistedPayRunDTO dto = new PersistedPayRunDTO();
        dto.setPayrunId(entity.getPayrunId());
        dto.setType(entity.getType());
        dto.setStatus(entity.getStatus());
        dto.setPayPeriodStartDate(entity.getPayPeriodStartDate());
        dto.setPayPeriodEndDate(entity.getPayPeriodEndDate());
        dto.setPayDate(entity.getPayDate());
        dto.setProcessingPeriod(entity.getProcessingPeriod());
        dto.setPayrollTotal(entity.getPayrollTotal() != null ? entity.getPayrollTotal() : BigDecimal.ZERO);
        dto.setStatusInfo(entity.getStatusInfo());
        dto.setNoOfEmployees(entity.getNoOfEmployees());
        dto.setApprovalType(entity.getApprovalType());
        dto.setApprovalDetails(entity.getApprovalDetails());
        dto.setCompensationName(entity.getCompensationName());
        dto.setPaymentDue(entity.getPaymentDue());
        dto.setPaymentStatus(entity.getPaymentStatus());
        return dto;
    }

    //Used only for readyStatePayrun
    public static ReadyPayRunDTO toReadyDto(LocalDate start, LocalDate end, LocalDate payDate,
                                            int completeEmployeesCount) {

        ReadyPayRunDTO dto = new ReadyPayRunDTO();
        dto.setType(PayRunType.REGULAR);
        dto.setStatus(PayRunStatus.READY);
        dto.setPayPeriodStartDate(start);
        dto.setPayPeriodEndDate(end);
        dto.setPayDate(payDate);
        dto.setProcessingPeriod(computeProcessingPeriod(start));
        dto.setNoOfEmployees(completeEmployeesCount);
        dto.setPaymentDue(true);
        dto.setStatusInfo("You haven't processed this pay run and it's past the pay day.");
        return dto;
    }

}