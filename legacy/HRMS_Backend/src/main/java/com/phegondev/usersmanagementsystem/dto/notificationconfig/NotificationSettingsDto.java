package com.phegondev.usersmanagementsystem.dto.notificationconfig;

import java.util.List;

public class NotificationSettingsDto {
	
    private List<ApprovalReminderDto> approvalReminders;
    private List<EmployeeReminderDto> employeeReminders;
    private EscalationReminderDto escalationSettings;
    private List<HrReminderDto> hrReminders;
    private List<SupervisorReminderDto> supervisorReminders;
    
    
	public List<ApprovalReminderDto> getApprovalReminders() {
		return approvalReminders;
	}
	
	public void setApprovalReminders(List<ApprovalReminderDto> approvalReminders) {
		this.approvalReminders = approvalReminders;
	}
	
	public List<EmployeeReminderDto> getEmployeeReminders() {
		return employeeReminders;
	}
	public void setEmployeeReminders(List<EmployeeReminderDto> employeeReminders) {
		this.employeeReminders = employeeReminders;
	}
	
	public EscalationReminderDto getEscalationSettings() {
		return escalationSettings;
	}
	
	public void setEscalationSettings(EscalationReminderDto escalationSettings) {
		this.escalationSettings = escalationSettings;
	}
	
	public List<HrReminderDto> getHrReminders() {
		return hrReminders;
	}
	
	public void setHrReminders(List<HrReminderDto> hrReminders) {
		this.hrReminders = hrReminders;
	}
	
	public List<SupervisorReminderDto> getSupervisorReminders() {
		return supervisorReminders;
	}
	
	public void setSupervisorReminders(List<SupervisorReminderDto> supervisorReminders) {
		this.supervisorReminders = supervisorReminders;
	}
	
	
	public NotificationSettingsDto() {
		super();
		// TODO Auto-generated constructor stub
	}

	public NotificationSettingsDto(List<ApprovalReminderDto> approvalReminders,
			List<EmployeeReminderDto> employeeReminders, EscalationReminderDto escalationSettings,
			List<HrReminderDto> hrReminders, List<SupervisorReminderDto> supervisorReminders) {
		super();
		this.approvalReminders = approvalReminders;
		this.employeeReminders = employeeReminders;
		this.escalationSettings = escalationSettings;
		this.hrReminders = hrReminders;
		this.supervisorReminders = supervisorReminders;
	}

	@Override
	public String toString() {
		return "NotificationSettingsDto [approvalReminders=" + approvalReminders + ", employeeReminders="
				+ employeeReminders + ", escalationSettings=" + escalationSettings + ", hrReminders=" + hrReminders
				+ ", supervisorReminders=" + supervisorReminders + "]";
	}
	
	

    
}
