package com.itsdev.payroll.mapper.employeereimbursement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.employeereimbursement.AdminReimbursementResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementResponseDTO;
import com.itsdev.payroll.entity.employeereimbursement.EmployeeReimbursementRequest;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementPaymentStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementType;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Static mapper between EmployeeReimbursementRequest entity and its DTOs.
 */
public class EmployeeReimbursementMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EmployeeReimbursementMapper() {
        // utility class — do not instantiate
    }

    /**
     * Parse a JSON-array string (e.g. ["url1","url2"]) or plain string into a List<String>.
     * Returns a single-element list for legacy plain-URL rows; empty list for null/empty.
     */
    private static List<String> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        raw = raw.trim();
        if (raw.startsWith("[")) {
            try {
                return OBJECT_MAPPER.readValue(raw, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                // fall through to plain-string handling
            }
        }
        // Legacy single-value plain string
        return Collections.singletonList(raw);
    }

    /**
     * Convert a request DTO into a new entity.
     */
    public static EmployeeReimbursementRequest toEntity(
            EmployeeReimbursementRequestDTO dto,
            String employeeId,
            String organizationId) {

        EmployeeReimbursementRequest entity = new EmployeeReimbursementRequest();

        entity.setEmployeeId(employeeId);
        entity.setOrganizationId(organizationId);
        entity.setReimbursementType(ReimbursementType.valueOf(dto.getReimbursementType().toUpperCase()));
        entity.setRequestedAmount(dto.getRequestedAmount());
        entity.setBillDate(dto.getBillDate());
        entity.setDescription(dto.getDescription().trim());
        entity.setAttachmentUrl(dto.getAttachmentUrl());

        // Business defaults — billing month is assigned upon admin approval
        entity.setStatus(ReimbursementStatus.PENDING);
        entity.setPaymentStatus(ReimbursementPaymentStatus.UNPAID);
        entity.setReimbursementMonth(null);
        entity.setApprovedAmount(null);
        entity.setRemarks(null);
        entity.setApprovedBy(null);
        entity.setApprovedAt(null);

        return entity;
    }

    /**
     * Map a persisted entity to a response DTO for the Employee Portal.
     */
    public static EmployeeReimbursementResponseDTO toResponseDTO(EmployeeReimbursementRequest entity) {
        return toResponseDTO(entity, null);
    }

    /**
     * Map a persisted entity with employeeNumber to a response DTO for the Employee Portal.
     * Billing month is only returned once the request status is APPROVED; otherwise null.
     */
    public static EmployeeReimbursementResponseDTO toResponseDTO(
            EmployeeReimbursementRequest entity,
            String employeeNumber) {

        EmployeeReimbursementResponseDTO dto = new EmployeeReimbursementResponseDTO();

        dto.setId(entity.getId());
        dto.setEmployeeNumber(employeeNumber);
        dto.setRequestDate(entity.getCreatedAt() != null
                ? entity.getCreatedAt().format(DATE_FORMATTER)
                : null);
        dto.setReimbursementType(entity.getReimbursementType() != null
                ? entity.getReimbursementType().name()
                : null);
        dto.setRequestedAmount(entity.getRequestedAmount());
        dto.setApprovedAmount(entity.getApprovedAmount());
        dto.setBillDate(entity.getBillDate());
        dto.setDescription(entity.getDescription());

        // Parse JSON array attachment columns
        List<String> urls      = parseJsonArray(entity.getAttachmentUrl());
        List<String> fileNames = parseJsonArray(entity.getAttachmentFileName());
        dto.setAttachmentUrls(urls);
        dto.setAttachmentFileNames(fileNames);
        // Legacy single-value fields for backward compatibility (first element)
        dto.setAttachmentUrl(urls.isEmpty() ? null : urls.get(0));
        dto.setAttachmentFileName(fileNames.isEmpty() ? null : fileNames.get(0));

        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setRemarks(entity.getRemarks());
        dto.setPaymentStatus(entity.getPaymentStatus() != null ? entity.getPaymentStatus().name() : null);

        // Billing month is visible once approved, otherwise null (rendered as '-' on frontend)
        dto.setReimbursementMonth(entity.getStatus() == ReimbursementStatus.APPROVED
                ? entity.getReimbursementMonth()
                : null);

        return dto;
    }

    /**
     * Backward-compatible overload for Admin response DTO.
     */
    public static AdminReimbursementResponseDTO toAdminResponseDTO(
            EmployeeReimbursementRequest entity,
            String employeeName) {
        return toAdminResponseDTO(entity, employeeName, null);
    }

    /**
     * Map a persisted entity to an Admin response DTO with employeeName and employeeNumber.
     * Billing month is only returned once the request status is APPROVED; otherwise null.
     */
    public static AdminReimbursementResponseDTO toAdminResponseDTO(
            EmployeeReimbursementRequest entity,
            String employeeName,
            String employeeNumber) {

        AdminReimbursementResponseDTO dto = new AdminReimbursementResponseDTO();

        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setEmployeeNumber(employeeNumber != null ? employeeNumber : entity.getEmployeeId());
        dto.setEmployeeName(employeeName != null ? employeeName : (employeeNumber != null ? employeeNumber : entity.getEmployeeId()));
        dto.setReimbursementType(entity.getReimbursementType() != null
                ? entity.getReimbursementType().name()
                : null);
        dto.setRequestedAmount(entity.getRequestedAmount());
        dto.setApprovedAmount(entity.getApprovedAmount());
        dto.setDescription(entity.getDescription());
        dto.setBillDate(entity.getBillDate());
        dto.setRequestDate(entity.getCreatedAt() != null
                ? entity.getCreatedAt().format(DATE_FORMATTER)
                : null);

        // Billing month is populated once approved, otherwise null
        dto.setReimbursementMonth(entity.getStatus() == ReimbursementStatus.APPROVED
                ? entity.getReimbursementMonth()
                : null);

        dto.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        dto.setPaymentStatus(entity.getPaymentStatus() != null ? entity.getPaymentStatus().name() : null);
        dto.setRemarks(entity.getRemarks());

        // Parse JSON array attachment columns
        List<String> urls      = parseJsonArray(entity.getAttachmentUrl());
        List<String> fileNames = parseJsonArray(entity.getAttachmentFileName());
        dto.setAttachmentUrls(urls);
        dto.setAttachmentFileNames(fileNames);
        // Legacy single-value fields (first element)
        dto.setAttachmentUrl(urls.isEmpty() ? null : urls.get(0));
        dto.setAttachmentFileName(fileNames.isEmpty() ? null : fileNames.get(0));

        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedAt(entity.getApprovedAt());

        return dto;
    }
}
