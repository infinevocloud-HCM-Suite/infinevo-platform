
package com.phegondev.usersmanagementsystem.service.leaverequest;

import com.phegondev.usersmanagementsystem.dto.LeaveRequestsDTO;
import com.phegondev.usersmanagementsystem.entity.LeaveDocument;
import com.phegondev.usersmanagementsystem.entity.LeaveRequests;
import com.phegondev.usersmanagementsystem.enumuration.LeaveRequestStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LeaveRequestsService {

    public LeaveRequests saveLeaveRequest(LeaveRequestsDTO dto, MultipartFile file);

    public List<LeaveRequestsDTO> getLeaveRequestsByEmployeeId(String employeeId);

    LeaveDocument getMedicalCertificate(Long leaveRequestId);

    //  List<LeaveRequestsDTO> getByEmployeeNameAndLeaveType(String employeeName, String leaveType);

    //   List<LeaveRequestsDTO> getByEmployeeName(String employeeName);

    //  List<LeaveRequestsDTO> getByLeaveType(String leaveType);

    List<LeaveRequestsDTO> getAllLeaveRequest();

    LeaveRequestsDTO getLeaveRequestById(Long id);


    LeaveRequestsDTO updateLeaveRequestStatus(Long requestId, LeaveRequestStatus newStatus, String role,  String comment);


    List<LeaveRequestsDTO> getLeaveRequestsByReportingManager(String reportingManagerEmployeeId);
}
