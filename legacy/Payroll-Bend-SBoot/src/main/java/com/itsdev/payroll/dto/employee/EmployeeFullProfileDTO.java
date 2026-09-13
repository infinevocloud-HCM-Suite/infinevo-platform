package com.itsdev.payroll.dto.employee;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class EmployeeFullProfileDTO {
    private BasicDetailsDTO basicDetails;
    private EmployeeCTCDTO ctc;
    private EmployeePersonalDetailDTO personalDetail;
    private EmployeeBankDetailDTO bankDetail;
    private Map<String, Boolean> completionStatus = new HashMap<>();
    private BigDecimal totalEarnings;
    private Boolean invitationAccepted;

	public Boolean getInvitationAccepted() {
		return invitationAccepted;
	}

	public void setInvitationAccepted(Boolean invitationAccepted) {
		this.invitationAccepted = invitationAccepted;
	}

	private EmployeeCTCDTO latestRevisionCtc;

	public EmployeeCTCDTO getLatestRevisionCtc() {
		return latestRevisionCtc;
	}

	public void setLatestRevisionCtc(EmployeeCTCDTO latestRevisionCtc) {
		this.latestRevisionCtc = latestRevisionCtc;
	}

	public BigDecimal getTotalEarnings() {
		return totalEarnings;
	}

	public void setTotalEarnings(BigDecimal totalEarnings) {
		this.totalEarnings = totalEarnings;
	}

	public void setCompletionStatus(Map<String, Boolean> completionStatus) {
		this.completionStatus = completionStatus;
	}

	public void markStepComplete(String step, boolean isComplete) {
        completionStatus.put(step, isComplete);
    }

	public Map<String, Boolean> getCompletionStatus() {
    return completionStatus;
}

    
	public BasicDetailsDTO getBasicDetails() {
		return basicDetails;
	}
	public void setBasicDetails(BasicDetailsDTO basicDetails) {
		this.basicDetails = basicDetails;
	}
	public EmployeeCTCDTO getCtc() {
		return ctc;
	}
	public void setCtc(EmployeeCTCDTO ctc) {
		this.ctc = ctc;
	}
	public EmployeePersonalDetailDTO getPersonalDetail() {
		return personalDetail;
	}
	public void setPersonalDetail(EmployeePersonalDetailDTO personalDetail) {
		this.personalDetail = personalDetail;
	}
	public EmployeeBankDetailDTO getBankDetail() {
		return bankDetail;
	}
	public void setBankDetail(EmployeeBankDetailDTO bankDetail) {
		this.bankDetail = bankDetail;
	}

    
}
