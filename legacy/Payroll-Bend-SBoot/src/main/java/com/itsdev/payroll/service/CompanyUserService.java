package com.itsdev.payroll.service;

import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.entity.CompanyUser;

import java.util.List;

public interface CompanyUserService {

    CompanyUser saveUser(CompanyUser user);

    boolean isEmailTaken(String email);

    void registerUser(CompanyUserDTO userDTO);

    void organizationUserRegistration(CompanyUserDTO userDTO);

    List<CompanyUserDTO> getAllUsers();

    void assignOrUpdateRoleToUserInOrganization(CompanyUserDTO dto);

    void organizationEmployeeRegistration(CompanyUserDTO userDTO);

    void toggleEmployeePortalAccess(CompanyUserDTO dto);

    List<CompanyUserDTO> getHrUsersByOrganization(String organizationId);

    String organizationEmployeeRegistration(BasicDetailsDTO employeeDTO);
}
