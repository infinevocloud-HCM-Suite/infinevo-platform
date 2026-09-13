package com.itsdev.payroll.service.organization;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.itsdev.payroll.dto.organization.OrganizationDTO;

public interface OrganizationService {

    List<OrganizationDTO> getAllOrganizations(String userId);

    OrganizationDTO getOrganizationByOrganizationId(String organizationId);

    OrganizationDTO createOrganization(OrganizationDTO dto);
    
    public OrganizationDTO updateOrganization(String organizationId, OrganizationDTO dto, MultipartFile file) throws Exception;

    void deleteOrganization(String organizationId);

    List<Map<String, Object>> getActiveOrganizationsForUser(String userId);
    
    public String deleteOrganizationFile(String organizationId);

    OrganizationDTO updateOrganizationWithHeadOffice(String organizationId, OrganizationDTO dto);
    
    void setFilingAddress(String organizationId, String workLocationId);

}
