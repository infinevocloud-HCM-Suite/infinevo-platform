package com.itsdev.payroll.mapper.organization;

import java.util.List;
import java.util.stream.Collectors;

import com.itsdev.payroll.dto.organization.OrganizationDTO;
import com.itsdev.payroll.dto.organization.WorkLocationDTO;
import com.itsdev.payroll.entity.organization.Organization;

public class OrganizationMapper {

    public static OrganizationDTO toDTO(Organization entity) {
        OrganizationDTO dto = new OrganizationDTO();
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setOrganizationName(entity.getOrganizationName());
        dto.setBusinessLocation(entity.getBusinessLocation());
        dto.setIndustry(entity.getIndustry());
        dto.setAddressLine1(entity.getAddressLine1());
        dto.setAddressLine2(entity.getAddressLine2());
        dto.setState(entity.getState());
        dto.setCity(entity.getCity());
        dto.setHasRunPayroll(entity.getHasRunPayroll());
        dto.setTimezone(entity.getTimezone());
        dto.setPinCode(entity.getPinCode());
        dto.setcreatedBy(entity.getcreatedBy());
        dto.setcreatedDate(entity.getcreatedDate());
        dto.setupdatedBy(entity.getupdatedBy());
        dto.setupdatedDate(entity.getupdatedDate());
        dto.setIsOrgActive(entity.getIsOrgActive());
        dto.setEmail(entity.getEmail());
        dto.setFileName(entity.getFileName());
        dto.setFileUrl(entity.getFileUrl());
        dto.setFilePublicId(entity.getFilePublicId());

        if (entity.getWorkLocations() != null) {
            List<WorkLocationDTO> wl = entity.getWorkLocations().stream()
                    .map(WorkLocationMapper::toDTO)
                    .collect(Collectors.toList());
            dto.setWorkLocations(wl);
        }
        return dto;
    }

    public static Organization toEntity(OrganizationDTO dto) {
        Organization org = new Organization();
        org.setOrganizationId(dto.getOrganizationId());
        org.setOrganizationName(dto.getOrganizationName());
        org.setBusinessLocation(dto.getBusinessLocation());
        org.setIndustry(dto.getIndustry());
        org.setAddressLine1(dto.getAddressLine1());
        org.setAddressLine2(dto.getAddressLine2());
        org.setState(dto.getState());
        org.setCity(dto.getCity());
        org.setHasRunPayroll(dto.getHasRunPayroll());
        org.setTimezone(dto.getTimezone());
        org.setPinCode(dto.getPinCode());
        org.setcreatedBy(dto.getcreatedBy());
        org.setupdatedBy(dto.getupdatedBy());
        org.setIsOrgActive(dto.getIsOrgActive());
        org.setEmail(dto.getEmail());
        return org;
    }
    
    public static List<OrganizationDTO> toDtoList(List<Organization> organizations) {
        return organizations.stream()
                .map(OrganizationMapper::toDTO)
                .collect(Collectors.toList());
    }


}
