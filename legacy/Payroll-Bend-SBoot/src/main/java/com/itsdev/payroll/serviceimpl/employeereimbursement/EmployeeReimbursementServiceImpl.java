package com.itsdev.payroll.serviceimpl.employeereimbursement;

import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.AdminReimbursementResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.ApproveReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementRequestDTO;
import com.itsdev.payroll.dto.employeereimbursement.EmployeeReimbursementResponseDTO;
import com.itsdev.payroll.dto.employeereimbursement.RejectReimbursementRequestDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employeereimbursement.EmployeeReimbursementRequest;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementPaymentStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementStatus;
import com.itsdev.payroll.exception.ResourceNotFoundException;
import com.itsdev.payroll.mapper.employeereimbursement.EmployeeReimbursementMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employeereimbursement.EmployeeReimbursementRequestRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.employeereimbursement.EmployeeReimbursementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service implementation for Employee & Admin Reimbursement Request operations.
 */
@Service
public class EmployeeReimbursementServiceImpl implements EmployeeReimbursementService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeReimbursementServiceImpl.class);

    private final EmployeeReimbursementRequestRepository reimbursementRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final CloudinaryService cloudinaryService;
    private final ObjectMapper objectMapper;

    public EmployeeReimbursementServiceImpl(
            EmployeeReimbursementRequestRepository reimbursementRepository,
            BasicDetailsRepository basicDetailsRepository,
            CloudinaryService cloudinaryService) {
        this.reimbursementRepository = reimbursementRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.cloudinaryService = cloudinaryService;
        this.objectMapper = new ObjectMapper();
    }

    // ======================== EMPLOYEE: CREATE ========================

    @Override
    @Transactional
    public EmployeeReimbursementResponseDTO createReimbursement(
            String employeeId,
            String organizationId,
            EmployeeReimbursementRequestDTO dto) {
        return createReimbursement(employeeId, organizationId, dto, List.of());
    }

    @Override
    @Transactional
    public EmployeeReimbursementResponseDTO createReimbursement(
            String employeeId,
            String organizationId,
            EmployeeReimbursementRequestDTO dto,
            List<MultipartFile> files) {

        String method = "createReimbursement";
        log.info("[{}] 📥 Incoming reimbursement request | employeeId={} | organizationId={} | type={} | amount={} | fileCount={}",
                method, employeeId, organizationId, dto != null ? dto.getReimbursementType() : null,
                dto != null ? dto.getRequestedAmount() : null, files != null ? files.size() : 0);

        // 1. Mandatory Business Validations
        if (dto == null) {
            log.warn("[{}] ❌ Request payload is null", method);
            throw new IllegalArgumentException("Reimbursement details are required.");
        }

        if (dto.getReimbursementType() == null || dto.getReimbursementType().trim().isEmpty()) {
            log.warn("[{}] ❌ Reimbursement type missing", method);
            throw new IllegalArgumentException("Reimbursement type is required.");
        }
        validateReimbursementType(method, dto.getReimbursementType());

        if (dto.getRequestedAmount() == null || dto.getRequestedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[{}] ❌ Requested amount must be > 0 | amount={}", method, dto.getRequestedAmount());
            throw new IllegalArgumentException("Requested amount must be greater than 0.");
        }

        if (dto.getBillDate() == null) {
            log.warn("[{}] ❌ Bill date missing", method);
            throw new IllegalArgumentException("Bill date is required.");
        }
        if (dto.getBillDate().isAfter(LocalDate.now())) {
            log.warn("[{}] ❌ Bill date cannot be in future | billDate={}", method, dto.getBillDate());
            throw new IllegalArgumentException("Bill date cannot be a future date.");
        }

        if (dto.getDescription() == null || dto.getDescription().trim().isEmpty()) {
            log.warn("[{}] ❌ Description missing", method);
            throw new IllegalArgumentException("Description is required.");
        }
        if (dto.getDescription().length() > 1000) {
            log.warn("[{}] ❌ Description too long", method);
            throw new IllegalArgumentException("Description must not exceed 1000 characters.");
        }

        // 2. Filter & Validate each attachment file
        List<MultipartFile> validFiles = (files != null)
                ? files.stream().filter(f -> f != null && !f.isEmpty()).collect(Collectors.toList())
                : List.of();

        for (MultipartFile uploadFile : validFiles) {
            log.info("[{}] 📎 Validating attachment | filename={} | size={} bytes | contentType={}",
                    method, uploadFile.getOriginalFilename(), uploadFile.getSize(), uploadFile.getContentType());

            if (uploadFile.getSize() > 10 * 1024 * 1024) {
                log.warn("[{}] ❌ File size exceeds 10MB | filename={} | size={}",
                        method, uploadFile.getOriginalFilename(), uploadFile.getSize());
                throw new IllegalArgumentException(
                        "File '" + uploadFile.getOriginalFilename() + "' exceeds the 10MB size limit.");
            }

            String contentType = uploadFile.getContentType();
            String filename = uploadFile.getOriginalFilename();
            String extension = filename != null && filename.contains(".")
                    ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase()
                    : "";

            boolean isValidMime = contentType != null && (
                    contentType.equalsIgnoreCase("application/pdf") ||
                    contentType.equalsIgnoreCase("application/x-pdf") ||
                    contentType.equalsIgnoreCase("image/jpeg") ||
                    contentType.equalsIgnoreCase("image/jpg") ||
                    contentType.equalsIgnoreCase("image/pjpeg") ||
                    contentType.equalsIgnoreCase("image/png") ||
                    contentType.equalsIgnoreCase("image/x-png")
            );
            boolean isValidExt = extension.equals("pdf") || extension.equals("jpg")
                    || extension.equals("jpeg") || extension.equals("png");

            if (!isValidMime && !isValidExt) {
                log.warn("[{}] ❌ Invalid attachment type | filename={} | contentType={}",
                        method, filename, contentType);
                throw new IllegalArgumentException(
                        "Invalid file type for '" + filename + "'. Allowed formats: PDF, JPG, JPEG, PNG.");
            }
        }

        // 3. Upload ALL valid files to Cloudinary
        List<CloudinaryUploadResponseDTO> uploadedFiles = new ArrayList<>();
        for (MultipartFile uploadFile : validFiles) {
            log.info("[{}] ☁️ Uploading attachment to Cloudinary | employeeId={} | filename={}",
                    method, employeeId, uploadFile.getOriginalFilename());
            try {
                CloudinaryUploadResponseDTO uploadResponse =
                        cloudinaryService.uploadReimbursementAttachment(uploadFile, organizationId, employeeId);
                uploadedFiles.add(uploadResponse);
                log.info("[{}] ☁️ Upload success | publicId={} | url={}",
                        method, uploadResponse.getPublic_id(), uploadResponse.getSecure_url());
            } catch (IOException e) {
                // Rollback: clean up any already-uploaded files
                log.error("[{}] ❌ Cloudinary upload failed for '{}'. Cleaning up {} previously uploaded file(s).",
                        method, uploadFile.getOriginalFilename(), uploadedFiles.size(), e);
                uploadedFiles.forEach(u -> {
                    try { cloudinaryService.deleteFile(u.getPublic_id()); }
                    catch (Exception ex) { log.error("[{}] ⚠️ Cleanup failed for publicId={}", method, u.getPublic_id(), ex); }
                });
                throw new RuntimeException("Failed to upload attachment '" + uploadFile.getOriginalFilename()
                        + "' to cloud storage. Please try again.", e);
            }
        }

        // 4. Build lists of URLs / publicIds / fileNames from uploaded results
        List<String> urls       = uploadedFiles.stream().map(CloudinaryUploadResponseDTO::getSecure_url).collect(Collectors.toList());
        List<String> publicIds  = uploadedFiles.stream().map(CloudinaryUploadResponseDTO::getPublic_id).collect(Collectors.toList());
        List<String> fileNames  = validFiles.stream().map(f -> f.getOriginalFilename() != null ? f.getOriginalFilename() : "").collect(Collectors.toList());

        // 5. Build Entity & persist attachment metadata as JSON arrays
        EmployeeReimbursementRequest entity = EmployeeReimbursementMapper.toEntity(dto, employeeId, organizationId);
        if (!uploadedFiles.isEmpty()) {
            try {
                entity.setAttachmentUrl(objectMapper.writeValueAsString(urls));
                entity.setAttachmentPublicId(objectMapper.writeValueAsString(publicIds));
                entity.setAttachmentFileName(objectMapper.writeValueAsString(fileNames));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize attachment metadata.", e);
            }
        }

        // 6. Persist to Database with Rollback / Cleanup Safety
        EmployeeReimbursementRequest saved;
        try {
            saved = reimbursementRepository.save(entity);
            log.info("[{}] ✅ Reimbursement saved | reimbursementId={} | attachments={}",
                    method, saved.getId(), uploadedFiles.size());
        } catch (Exception e) {
            log.error("[{}] ❌ DB save failed. Cleaning up {} Cloudinary file(s).", method, uploadedFiles.size(), e);
            uploadedFiles.forEach(u -> {
                try { cloudinaryService.deleteFile(u.getPublic_id()); }
                catch (Exception ex) { log.error("[{}] ⚠️ Cleanup failed | publicId={}", method, u.getPublic_id(), ex); }
            });
            throw e;
        }

        // 7. Resolve employeeNumber for the response
        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElse(null);
        String employeeNumber = employee != null ? employee.getEmployeeNumber() : null;

        EmployeeReimbursementResponseDTO responseDTO = EmployeeReimbursementMapper.toResponseDTO(saved, employeeNumber);
        log.info("[{}] 📤 Response ready | reimbursementId={}", method, saved.getId());
        return responseDTO;
    }

    // ======================== EMPLOYEE: GET ALL ========================

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeReimbursementResponseDTO> getEmployeeReimbursements(
            String employeeId,
            String organizationId) {

        String method = "getEmployeeReimbursements";
        log.info("[{}] 📥 Incoming request to fetch reimbursement history | employeeId={} | organizationId={}",
                method, employeeId, organizationId);

        List<EmployeeReimbursementRequest> requests = reimbursementRepository
                .findByEmployeeIdAndOrganizationIdOrderByCreatedAtDesc(employeeId, organizationId);

        log.info("[{}] 🗄️ Fetched {} reimbursement record(s) | employeeId={}", method, requests.size(), employeeId);

        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElse(null);
        String employeeNumber = employee != null ? employee.getEmployeeNumber() : null;

        List<EmployeeReimbursementResponseDTO> result = requests.stream()
                .map(r -> EmployeeReimbursementMapper.toResponseDTO(r, employeeNumber))
                .collect(Collectors.toList());

        log.info("[{}] ✅ Reimbursement history fetched successfully | count={}", method, result.size());
        log.info("[{}] 📤 Response sent successfully", method);

        return result;
    }

    // ======================== EMPLOYEE: GET BY ID ========================

    @Override
    @Transactional(readOnly = true)
    public EmployeeReimbursementResponseDTO getReimbursementDetails(
            Long id,
            String employeeId,
            String organizationId) {

        String method = "getReimbursementDetails";
        log.info("[{}] 📥 Incoming request for reimbursement details | id={} | employeeId={} | organizationId={}",
                method, id, employeeId, organizationId);

        EmployeeReimbursementRequest entity = reimbursementRepository
                .findByIdAndEmployeeIdAndOrganizationId(id, employeeId, organizationId)
                .orElseThrow(() -> {
                    log.warn("[{}] ⚠️ Reimbursement not found | id={} | employeeId={}", method, id, employeeId);
                    return new ResourceNotFoundException(
                            "Reimbursement request with id " + id + " not found for this employee.");
                });

        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElse(null);
        String employeeNumber = employee != null ? employee.getEmployeeNumber() : null;

        log.info("[{}] ✅ Reimbursement details fetched successfully | reimbursementId={}", method, entity.getId());

        EmployeeReimbursementResponseDTO responseDTO = EmployeeReimbursementMapper.toResponseDTO(entity, employeeNumber);
        log.info("[{}] 📤 Response sent successfully | reimbursementId={}", method, entity.getId());

        return responseDTO;
    }

    // ======================== ADMIN: GET ALL ========================

    @Override
    @Transactional(readOnly = true)
    public List<AdminReimbursementResponseDTO> getAdminReimbursements(String organizationId) {
        String method = "getAdminReimbursements";
        log.info("[{}] 📥 Incoming request to fetch reimbursements | organizationId={}",
                method, organizationId);

        List<EmployeeReimbursementRequest> requests = reimbursementRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId);

        log.info("[{}] 🗄️ Fetched {} reimbursement record(s) for organizationId={}",
                method, requests.size(), organizationId);

        // Fetch employees in bulk to avoid N+1 queries and map both name and employeeNumber
        List<BasicDetails> employees = basicDetailsRepository.findByOrganization_OrganizationId(organizationId);
        Map<String, BasicDetails> employeeMap = employees.stream()
                .filter(e -> e.getEmployeeId() != null)
                .collect(Collectors.toMap(
                        BasicDetails::getEmployeeId,
                        e -> e,
                        (existing, replacement) -> existing
                ));

        List<AdminReimbursementResponseDTO> result = requests.stream()
                .map(r -> {
                    BasicDetails bd = employeeMap.get(r.getEmployeeId());
                    String employeeName = bd != null ? formatEmployeeName(bd) : r.getEmployeeId();
                    String employeeNumber = bd != null ? bd.getEmployeeNumber() : null;
                    return EmployeeReimbursementMapper.toAdminResponseDTO(r, employeeName, employeeNumber);
                })
                .collect(Collectors.toList());

        log.info("[{}] ✅ Successfully fetched {} reimbursements", method, result.size());
        log.info("[{}] 📤 Response sent to client", method);

        return result;
    }

    // ======================== ADMIN: GET BY ID ========================

    @Override
    @Transactional(readOnly = true)
    public AdminReimbursementResponseDTO getAdminReimbursementById(Long id, String organizationId) {
        String method = "getAdminReimbursementById";
        log.info("[{}] 📥 Incoming request for admin reimbursement details | id={} | organizationId={}",
                method, id, organizationId);

        EmployeeReimbursementRequest entity = reimbursementRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> {
                    log.warn("[{}] ⚠️ Reimbursement not found | id={} | organizationId={}", method, id, organizationId);
                    return new ResourceNotFoundException(
                            "Reimbursement request with id " + id + " not found in this organization.");
                });

        // Resolve employee details (name and employeeNumber)
        String employeeName = entity.getEmployeeId();
        String employeeNumber = null;
        if (entity.getEmployeeId() != null) {
            BasicDetails employee = basicDetailsRepository
                    .findByOrganization_OrganizationIdAndEmployeeId(organizationId, entity.getEmployeeId())
                    .orElse(null);
            if (employee != null) {
                employeeName = formatEmployeeName(employee);
                employeeNumber = employee.getEmployeeNumber();
            }
        }

        AdminReimbursementResponseDTO responseDTO = EmployeeReimbursementMapper.toAdminResponseDTO(
                entity, employeeName, employeeNumber);
        log.info("[{}] ✅ Reimbursement fetched successfully | reimbursementId={}", method, entity.getId());
        log.info("[{}] 📤 Response sent to client", method);

        return responseDTO;
    }

    // ======================== ADMIN: APPROVE ========================

    @Override
    @Transactional
    public AdminReimbursementResponseDTO approveReimbursement(
            Long id,
            String organizationId,
            String adminId,
            ApproveReimbursementRequestDTO dto) {

        String method = "approveReimbursement";
        log.info("[{}] 📥 Approval request | reimbursementId={} | organizationId={} | adminId={}",
                method, id, organizationId, adminId);

        EmployeeReimbursementRequest entity = reimbursementRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> {
                    log.warn("[{}] ⚠️ Reimbursement not found for approval | id={} | organizationId={}", method, id, organizationId);
                    return new ResourceNotFoundException(
                            "Reimbursement request with id " + id + " not found in this organization.");
                });

        if (entity.getStatus() != ReimbursementStatus.PENDING) {
            log.warn("[{}] ❌ Invalid status transition | reimbursementId={} | currentStatus={}",
                    method, id, entity.getStatus());
            throw new IllegalStateException(
                    "Only PENDING reimbursement requests can be approved. Current status: " + entity.getStatus());
        }

        if (dto.getApprovedAmount() == null || dto.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("[{}] ❌ Invalid approved amount | reimbursementId={} | amount={}",
                    method, id, dto.getApprovedAmount());
            throw new IllegalArgumentException("Approved amount must be greater than 0.");
        }

        if (dto.getApprovedAmount().compareTo(entity.getRequestedAmount()) > 0) {
            log.warn("[{}] ❌ Approved amount exceeds requested amount | approved={} | requested={}",
                    method, dto.getApprovedAmount(), entity.getRequestedAmount());
            throw new IllegalArgumentException(
                    "Approved amount (" + dto.getApprovedAmount() + ") cannot exceed requested amount ("
                            + entity.getRequestedAmount() + ").");
        }

        entity.setStatus(ReimbursementStatus.APPROVED);
        entity.setApprovedAmount(dto.getApprovedAmount());
        entity.setRemarks(dto.getRemarks());
        entity.setApprovedBy(adminId);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setPaymentStatus(ReimbursementPaymentStatus.UNPAID);

        if (dto.getReimbursementMonth() != null && !dto.getReimbursementMonth().isBlank()) {
            entity.setReimbursementMonth(dto.getReimbursementMonth().trim());
        }

        EmployeeReimbursementRequest saved = reimbursementRepository.save(entity);
        log.info("[{}] ✅ Reimbursement approved successfully | reimbursementId={} | approvedAmount={}",
                method, saved.getId(), saved.getApprovedAmount());

        // Resolve employee details for response
        String employeeName = entity.getEmployeeId();
        String employeeNumber = null;
        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, entity.getEmployeeId())
                .orElse(null);
        if (employee != null) {
            employeeName = formatEmployeeName(employee);
            employeeNumber = employee.getEmployeeNumber();
        }

        AdminReimbursementResponseDTO responseDTO = EmployeeReimbursementMapper.toAdminResponseDTO(
                saved, employeeName, employeeNumber);
        log.info("[{}] 📤 Response sent to client", method);

        return responseDTO;
    }

    // ======================== ADMIN: REJECT ========================

    @Override
    @Transactional
    public AdminReimbursementResponseDTO rejectReimbursement(
            Long id,
            String organizationId,
            String adminId,
            RejectReimbursementRequestDTO dto) {

        String method = "rejectReimbursement";
        log.info("[{}] 📥 Rejection request | reimbursementId={} | organizationId={} | adminId={}",
                method, id, organizationId, adminId);

        EmployeeReimbursementRequest entity = reimbursementRepository
                .findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> {
                    log.warn("[{}] ⚠️ Reimbursement not found for rejection | id={} | organizationId={}", method, id, organizationId);
                    return new ResourceNotFoundException(
                            "Reimbursement request with id " + id + " not found in this organization.");
                });

        if (entity.getStatus() != ReimbursementStatus.PENDING) {
            log.warn("[{}] ❌ Invalid status transition | reimbursementId={} | currentStatus={}",
                    method, id, entity.getStatus());
            throw new IllegalStateException(
                    "Only PENDING reimbursement requests can be rejected. Current status: " + entity.getStatus());
        }

        if (dto.getRemarks() == null || dto.getRemarks().trim().isEmpty()) {
            log.warn("[{}] ❌ Rejection remarks missing | reimbursementId={}", method, id);
            throw new IllegalArgumentException("Rejection remarks/reason is required.");
        }

        entity.setStatus(ReimbursementStatus.REJECTED);
        entity.setApprovedAmount(null);
        entity.setRemarks(dto.getRemarks().trim());
        entity.setApprovedBy(adminId);
        entity.setApprovedAt(LocalDateTime.now());
        entity.setPaymentStatus(ReimbursementPaymentStatus.UNPAID);

        EmployeeReimbursementRequest saved = reimbursementRepository.save(entity);
        log.info("[{}] ✅ Reimbursement rejected successfully | reimbursementId={} | remarks={}",
                method, saved.getId(), saved.getRemarks());

        // Resolve employee details for response
        String employeeName = entity.getEmployeeId();
        String employeeNumber = null;
        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, entity.getEmployeeId())
                .orElse(null);
        if (employee != null) {
            employeeName = formatEmployeeName(employee);
            employeeNumber = employee.getEmployeeNumber();
        }

        AdminReimbursementResponseDTO responseDTO = EmployeeReimbursementMapper.toAdminResponseDTO(
                saved, employeeName, employeeNumber);
        log.info("[{}] 📤 Response sent to client", method);

        return responseDTO;
    }

    // ======================== PRIVATE HELPERS ========================

    private String formatEmployeeName(BasicDetails bd) {
        if (bd == null) return null;
        String fullName = Stream.of(bd.getFirstName(), bd.getMiddleName(), bd.getLastName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return fullName.isBlank() ? (bd.getEmployeeNumber() != null ? bd.getEmployeeNumber() : bd.getEmployeeId()) : fullName;
    }

    private void validateReimbursementType(String method, String type) {
        try {
            com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("[{}] ❌ Invalid reimbursement type received | type={}", method, type);
            throw new IllegalArgumentException(
                    "Invalid reimbursement type: '" + type + "'. Allowed values: MEDICAL, TRAVEL, FOOD, INTERNET, FUEL, OTHER.");
        }
    }
}
