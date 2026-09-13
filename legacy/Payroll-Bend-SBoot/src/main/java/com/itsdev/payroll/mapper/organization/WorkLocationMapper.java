package com.itsdev.payroll.mapper.organization;

import com.itsdev.payroll.dto.organization.WorkLocationDTO;
import com.itsdev.payroll.entity.organization.WorkLocation;

public class WorkLocationMapper {

    public static WorkLocationDTO toDTO(WorkLocation entity) {
        WorkLocationDTO dto = new WorkLocationDTO();
        dto.setWorkLocationId(entity.getWorkLocationId());
        dto.setWorkLocationName(entity.getWorkLocationName());
        dto.setStreetAddress1(entity.getStreetAddress1());
        dto.setStreetAddress2(entity.getStreetAddress2());
        dto.setCity(entity.getCity());
        dto.setState(entity.getState());
        dto.setZipCode(entity.getZipCode());
        dto.setCountry(entity.getCountry());
        dto.setIsFilingAddress(entity.getIsFilingAddress());
        return dto;
    }

    public static WorkLocation toEntity(WorkLocationDTO dto) {
        WorkLocation entity = new WorkLocation();
        entity.setWorkLocationName(dto.getWorkLocationName());
        entity.setStreetAddress1(dto.getStreetAddress1());
        entity.setStreetAddress2(dto.getStreetAddress2());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setZipCode(dto.getZipCode());
        entity.setCountry(dto.getCountry());
        entity.setIsFilingAddress(dto.getIsFilingAddress());
        return entity;
    }
}
