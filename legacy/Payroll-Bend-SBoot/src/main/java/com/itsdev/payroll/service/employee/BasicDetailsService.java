package com.itsdev.payroll.service.employee;

import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.employee.EmployeeDetailsDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BasicDetailsService {
    BasicDetailsDTO createBasicDetails(String organizationId, BasicDetailsDTO dto);
    BasicDetailsDTO updateBasicDetails(String organizationId, String basicDetailsId, BasicDetailsDTO dto);
    BasicDetailsDTO getBasicDetails(String organizationId, String basicDetailsId);
   // List<BasicDetailsDTO> getAllBasicDetails(String organizationId);
    void deleteBasicDetails(String organizationId, String basicDetailsId);
    void saveAll(String organizationId, List<BasicDetailsDTO> basicDetailsList);


    Page<BasicDetailsDTO> getAllBasicDetails(String organizationId, Pageable pageable,
                                             String workLocationId, String departmentId, String designationId);



    EmployeeDetailsDTO getEmployeeByWorkMail(String email);
    
    public BasicDetailsDTO mapToDto(BasicDetails entity);

    // BasicDetailsService.java


    void importStatutory(String organizationId, List<BasicDetailsDTO> dtos);




}

