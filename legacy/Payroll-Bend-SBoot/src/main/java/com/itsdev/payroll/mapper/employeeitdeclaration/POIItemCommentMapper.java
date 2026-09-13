package com.itsdev.payroll.mapper.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIItemCommentDTO;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIItemComment;
import com.itsdev.payroll.entity.employee.BasicDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class POIItemCommentMapper {

    public POIItemCommentDTO toDto(EmployeePOIItemComment entity) {
        if (entity == null)
            return null;

        POIItemCommentDTO dto = new POIItemCommentDTO();
        dto.setId(entity.getId());
        if (entity.getPoiItem() != null) {
            dto.setPoiItemId(entity.getPoiItem().getId());
        }
        dto.setComment(entity.getComment());

        // Map employee details
        BasicDetails commentedByEmployee = entity.getCommentedByEmployee();
        if (commentedByEmployee != null) {
            dto.setCommentedByEmployeeId(commentedByEmployee.getId());

            String firstName = commentedByEmployee.getFirstName() != null ? commentedByEmployee.getFirstName() : "";
            String lastName = commentedByEmployee.getLastName() != null ? commentedByEmployee.getLastName() : "";
            dto.setCommentedByEmployeeName(firstName + " " + lastName);
        }

        // Map admin details
        dto.setCommentedByAdmin(entity.getCommentedByAdmin());
        // Note: You might want to fetch admin name from user service

        dto.setCreatedTime(entity.getCreatedTime());

        // Map response reference (just ID to avoid circular references)
        if (entity.getResponseTo() != null) {
            dto.setResponseToCommentId(entity.getResponseTo().getId());
        }

        return dto;
    }

    public EmployeePOIItemComment toEntity(POIItemCommentDTO dto) {
        if (dto == null)
            return null;

        EmployeePOIItemComment entity = new EmployeePOIItemComment();
        entity.setId(dto.getId());
        entity.setComment(dto.getComment());
        // Note: poiItem, commentedByEmployee, commentedByAdmin need to be set
        // separately

        return entity;
    }
}