package com.itsdev.payroll.service.organization;

import com.itsdev.payroll.dto.organization.WorkLocationDTO;

import java.util.List;

public interface WorkLocationService {
    WorkLocationDTO createWorkLocationForOrg(String organizationId,WorkLocationDTO dto);
    WorkLocationDTO updateWorkLocationForOrg(String organizationId, String workLocationId, WorkLocationDTO dto);
    WorkLocationDTO getWorkLocationForOrg(String organizationId, String workLocationId);
    void deleteWorkLocationForOrg(String organizationId, String workLocationId);
    List<WorkLocationDTO> getAllWorkLocationsForOrg(String organizationId);
    void saveAll(String organizationId, List<WorkLocationDTO> workLocations);
}
