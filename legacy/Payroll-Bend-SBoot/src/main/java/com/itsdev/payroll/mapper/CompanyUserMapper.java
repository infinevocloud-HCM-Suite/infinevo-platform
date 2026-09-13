package com.itsdev.payroll.mapper;

import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.entity.CompanyUser;

public class CompanyUserMapper {

    public static CompanyUser mapToEntity(CompanyUserDTO dto) {
        CompanyUser user = new CompanyUser();
        user.setUserId(dto.getUserId());
        user.setCompanyName(dto.getCompanyName());
        user.setUserEmail(dto.getUserEmail());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setCountry(dto.getCountry());
        user.setStates(dto.getStates());
        user.setPassword(dto.getPassword());
        user.setToc(dto.getToc());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        return user;
    }

    public static CompanyUserDTO toDTO(CompanyUser user) {
        CompanyUserDTO dto = new CompanyUserDTO();
        dto.setUserId(user.getUserId());
        dto.setCompanyName(user.getCompanyName());
        dto.setUserEmail(user.getUserEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setCountry(user.getCountry());
        dto.setStates(user.getStates());
        dto.setToc(user.getToc());
        return dto;
    }
}
