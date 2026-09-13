package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.employee.EmployeeFullProfileDTO;

public interface EmployyePortalService {
	
	 public EmployeeFullProfileDTO getEmployeeProfile(String organizationId, String employeeId);
	 
	 public BasicDetailsDTO activateEmployee(String organizationId, String employeeId);
	 
	    public BasicDetailsDTO deactivateEmployee(String organizationId, String employeeId);
	    
	    public BasicDetailsDTO softDeleteEmployee(String organizationId, String employeeId);
	    
	    public void enablePortal(String organizationId, String employeeId);
	    
	    public void resendInvitation(String organizationId, String employeeId);

}
