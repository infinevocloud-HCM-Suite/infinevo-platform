package com.itsdev.payroll.service.organization;

import com.itsdev.payroll.dto.organization.DesignationDTO;

import java.util.List;

public interface DesignationService {
    DesignationDTO createDesignation(String organizationId, DesignationDTO dto);
    DesignationDTO updateDesignation(String organizationId, String designationId, DesignationDTO dto);
    DesignationDTO getDesignation(String organizationId, String designationId);
    List<DesignationDTO> getAllDesignations(String organizationId);
    void deleteDesignation(String organizationId, String designationId);
    void saveAll(String organizationId, List<DesignationDTO> designations);
}