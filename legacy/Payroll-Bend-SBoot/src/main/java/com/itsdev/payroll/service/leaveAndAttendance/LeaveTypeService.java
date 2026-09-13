package com.itsdev.payroll.service.leaveAndAttendance;



import com.itsdev.payroll.dto.leaveAndAttendance.LeaveTypeDTO;

import java.util.List;

public interface LeaveTypeService {

    LeaveTypeDTO createLeaveTypeForOrg(String organizationId, LeaveTypeDTO dto);

    LeaveTypeDTO updateLeaveTypeForOrg(String organizationId, Long leaveTypeId, LeaveTypeDTO dto);

    LeaveTypeDTO getLeaveTypeForOrg(String organizationId, Long leaveTypeId);

    void deleteLeaveTypeForOrg(String organizationId, Long leaveTypeId);

    List<LeaveTypeDTO> getAllLeaveTypesForOrg(String organizationId);

    void saveAll(String organizationId, List<LeaveTypeDTO> leaveTypes);

    LeaveTypeDTO updateLeaveTypeStatus(String organizationId, Long leaveTypeId, String status);
}

