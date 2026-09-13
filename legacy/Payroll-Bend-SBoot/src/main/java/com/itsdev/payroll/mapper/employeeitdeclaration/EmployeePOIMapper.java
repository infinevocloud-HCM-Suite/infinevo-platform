package com.itsdev.payroll.mapper.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeePOIDocumentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeePOIItemDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.EmployeeProofOfInvestmentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIItemCommentDTO;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POIPropertyDetailDTO;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIDocument;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIItem;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIItemComment;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIPropertyDetail;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EmployeePOIMapper {

    private final POIItemCommentMapper poiItemCommentMapper;

    public EmployeePOIMapper(POIItemCommentMapper poiItemCommentMapper) {
        this.poiItemCommentMapper = poiItemCommentMapper;
    }

    public EmployeePOIMapper() {
        this.poiItemCommentMapper = new POIItemCommentMapper();
    }

    /* ===================== ENTITY → DTO ===================== */

    public EmployeeProofOfInvestmentDTO toDto(EmployeeProofOfInvestment entity) {
        if (entity == null)
            return null;

        EmployeeProofOfInvestmentDTO dto = new EmployeeProofOfInvestmentDTO();
        dto.setId(entity.getId());
        if (entity.getDeclaration() != null) {
            dto.setDeclarationId(entity.getDeclaration().getId());
        }
        if (entity.getOrganization() != null) {
            dto.setOrganizationId(entity.getOrganization().getId());
        }
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getId());
            dto.setEmployeeName(entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName());
            dto.setEmployeeNumber(entity.getEmployee().getEmployeeNumber());
        }
        dto.setFiscalYear(entity.getFiscalYear());

        // ========== ADD THESE LINES ==========
        dto.setTaxRegimeAtSubmission(entity.getTaxRegimeAtSubmission());

        // Add formatted tax regime for frontend
        String taxRegime = entity.getTaxRegimeAtSubmission();
        if ("with_exemptions".equals(taxRegime)) {
            dto.setTaxRegime("with_exemptions");
            dto.setTaxRegimeFormatted("Old Regime");
        } else if ("without_exemptions".equals(taxRegime)) {
            dto.setTaxRegime("without_exemptions");
            dto.setTaxRegimeFormatted("New Regime");
        } else {
            dto.setTaxRegime(taxRegime);
            dto.setTaxRegimeFormatted(taxRegime);
        }
        // ========== END ADDED LINES ==========

        dto.setStatus(entity.getStatus());
        dto.setSubmittedBy(entity.getSubmittedBy());
        dto.setSubmittedDate(entity.getSubmittedDate());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedDate(entity.getApprovedDate());
        dto.setConsideredForIt(entity.getConsideredForIt());

        dto.setLastEditedBy(determineLastEditedBy(entity));
        dto.setLastEditedDate(entity.getUpdatedTime());

        dto.setPoiItems(
                entity.getPoiItems()
                        .stream()
                        .map(this::toItemDto)
                        .collect(Collectors.toList()));

        return dto;
    }

    // ADD THIS HELPER METHOD to determine lastEditedBy
    private String determineLastEditedBy(EmployeeProofOfInvestment entity) {
        if (entity.getSubmittedBy() != null && !entity.getSubmittedBy().isEmpty()) {
            return entity.getSubmittedBy();
        }

        if (entity.getApprovedBy() != null && !entity.getApprovedBy().isEmpty()) {
            return entity.getApprovedBy();
        }

        // If you track createdBy in entity, use that
        // return entity.getCreatedBy();

        // Fallback to employee name
        if (entity.getEmployee() != null) {
            return entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName();
        }

        return "System";
    }

    public EmployeePOIItemDTO toItemDto(EmployeePOIItem item) {
        if (item == null)
            return null;

        EmployeePOIItemDTO dto = new EmployeePOIItemDTO();
        dto.setId(item.getId());

        // Set POI ID if proofOfInvestment exists
        if (item.getProofOfInvestment() != null) {
            dto.setPoiId(item.getProofOfInvestment().getId());
        }

        dto.setInvestmentType(item.getInvestmentType());
        dto.setSection6aItemId(item.getSection6aItemId());

        dto.setDeclaredAmount(item.getDeclaredAmount());
        dto.setActualAmount(item.getActualAmount());
        dto.setApprovedAmount(item.getApprovedAmount());

        if (item.getStatus() != null) {
            dto.setStatus(item.getStatus().name());
        } else {
            dto.setStatus(null);
        }

        dto.setAdminComment(item.getAdminComment());
        dto.setItemIdExternal(item.getItemIdExternal());
        // EmployeePOIItem does not define getCreatedTime/getUpdatedTime; set to null
        // for now.
        dto.setCreatedTime(null);
        dto.setUpdatedTime(null);

        // Map documents
        dto.setDocuments(
                item.getDocuments()
                        .stream()
                        .map(this::toDocumentDto)
                        .collect(Collectors.toList()));

        // MAP COMMENTS - NEW SECTION
        if (item.getComments() != null && !item.getComments().isEmpty()) {
            List<POIItemCommentDTO> commentDTOs = item.getComments()
                    .stream()
                    .map(poiItemCommentMapper::toDto)
                    .collect(Collectors.toList());
            dto.setComments(commentDTOs);

            // Set comment count
            dto.setCommentCount(commentDTOs.size());
        } else {
            dto.setComments(null);
            dto.setCommentCount(0);
        }
        // Map property details
        if (item.getPropertyDetails() != null) {
            List<POIPropertyDetailDTO> propertyDetailDTOs = item.getPropertyDetails()
                    .stream()
                    .map(this::toPropertyDetailDto)
                    .collect(Collectors.toList());
            dto.setPropertyDetails(propertyDetailDTOs);
        }

        return dto;
    }

    private POIPropertyDetailDTO toPropertyDetailDto(EmployeePOIPropertyDetail detail) {
        if (detail == null)
            return null;

        POIPropertyDetailDTO dto = new POIPropertyDetailDTO();
        dto.setType(detail.getType());
        dto.setAmount(detail.getAmount());
        dto.setNameOfLender(detail.getNameOfLender());
        dto.setPanOfLender(detail.getPanOfLender());

        // Add formatted values if needed
        if ("annual_rent".equals(detail.getType())) {
            dto.setTypeFormatted("Annual Rent Received");
        } else if ("municipal_tax".equals(detail.getType())) {
            dto.setTypeFormatted("Municipal Taxes Paid");
        }

        return dto;
    }

    private EmployeePOIDocumentDTO toDocumentDto(EmployeePOIDocument doc) {
        if (doc == null)
            return null;

        EmployeePOIDocumentDTO dto = new EmployeePOIDocumentDTO();
        dto.setId(doc.getId());
        dto.setDocumentName(doc.getDocumentName());
        dto.setDocumentUrl(doc.getDocumentUrl());
        dto.setUploadedBy(doc.getUploadedBy());
        dto.setUploadedTime(doc.getUploadedTime());

        return dto;
    }

    /* ===================== DTO → ENTITY ===================== */
    // Add these if you need reverse mapping

    public EmployeeProofOfInvestment toEntity(EmployeeProofOfInvestmentDTO dto) {
        if (dto == null)
            return null;

        EmployeeProofOfInvestment entity = new EmployeeProofOfInvestment();
        entity.setId(dto.getId());
        entity.setFiscalYear(dto.getFiscalYear());
        entity.setTaxRegimeAtSubmission(dto.getTaxRegimeAtSubmission());
        entity.setStatus(dto.getStatus());
        entity.setSubmittedBy(dto.getSubmittedBy());
        entity.setSubmittedDate(dto.getSubmittedDate());
        entity.setApprovedBy(dto.getApprovedBy());
        entity.setApprovedDate(dto.getApprovedDate());
        entity.setConsideredForIt(dto.getConsideredForIt());

        // Note: Organization, Employee, Declaration need to be set separately
        // as they are relationships

        return entity;
    }

    public EmployeePOIItem toItemEntity(EmployeePOIItemDTO dto) {
        if (dto == null)
            return null;

        EmployeePOIItem entity = new EmployeePOIItem();
        entity.setId(dto.getId());
        entity.setInvestmentType(dto.getInvestmentType());
        entity.setSection6aItemId(dto.getSection6aItemId());
        entity.setDeclaredAmount(dto.getDeclaredAmount());
        entity.setActualAmount(dto.getActualAmount());
        entity.setApprovedAmount(dto.getApprovedAmount());

        if (dto.getStatus() != null) {
            // Assuming you have an enum for status
            // entity.setStatus(Status.valueOf(dto.getStatus()));
        }

        entity.setAdminComment(dto.getAdminComment());
        entity.setItemIdExternal(dto.getItemIdExternal());
        // EmployeePOIItem does not expose created/updated time setters; skip mapping
        // here.

        // Note: Comments and documents would need to be mapped separately
        // as they have their own entities

        return entity;
    }
}