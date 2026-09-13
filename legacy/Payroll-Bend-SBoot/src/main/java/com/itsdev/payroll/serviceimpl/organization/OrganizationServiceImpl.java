package com.itsdev.payroll.serviceimpl.organization;

import com.itsdev.payroll.dto.organization.OrganizationRoleDTO;
import com.itsdev.payroll.dto.organization.WorkLocationDTO;
import com.itsdev.payroll.entity.OrganizationUserMapping;
import com.itsdev.payroll.entity.OrganizationUserRoleMapping;
import com.itsdev.payroll.entity.organization.WorkLocation;
import com.itsdev.payroll.mapper.organization.WorkLocationMapper;
import com.itsdev.payroll.repository.OrganizationUserMappingRepository;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import com.itsdev.payroll.repository.organization.WorkLocationRepository;

import com.itsdev.payroll.service.organization.OrganizationRoleService;
import com.itsdev.payroll.service.statutorycomponents.ProfessionalTaxService;
import com.itsdev.payroll.serviceimpl.CloudinaryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import com.itsdev.payroll.dto.organization.OrganizationDTO;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.organization.OrganizationMapper;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.organization.OrganizationService;
import com.itsdev.payroll.util.JWTUtil;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.itsdev.payroll.service.organization.UserInvitationService;
import com.itsdev.payroll.dto.organization.UserInvitationDTO;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final WorkLocationRepository workLocationRepository;
    private final CloudinaryService cloudinaryService;
    private final OrganizationUserMappingRepository organizationUserMappingRepository;
    private final ProfessionalTaxService professionalTaxService;
    private final OrganizationRoleService organizationRoleService;
    private final OrganizationUserRoleMappingRepository organizationUserRoleMappingRepository;
    private final UserInvitationService userInvitationService;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
            WorkLocationRepository workLocationRepository,
            CloudinaryService cloudinaryService,
            OrganizationUserMappingRepository organizationUserMappingRepository,
            ProfessionalTaxService professionalTaxService,
            OrganizationRoleService organizationRoleService,
            OrganizationUserRoleMappingRepository organizationUserRoleMappingRepository,
            UserInvitationService userInvitationService) {
        this.organizationRepository = organizationRepository;
        this.workLocationRepository = workLocationRepository;
        this.cloudinaryService = cloudinaryService;
        this.organizationUserMappingRepository = organizationUserMappingRepository;
        this.professionalTaxService = professionalTaxService;
        this.organizationRoleService = organizationRoleService;
        this.organizationUserRoleMappingRepository = organizationUserRoleMappingRepository;
        this.userInvitationService = userInvitationService;
    }

    private static final Logger log = LoggerFactory.getLogger(CloudinaryServiceImpl.class);

    private String generateUniqueOrganizationId() {
        String id;
        do {
            id = String.format("%06d", new Random().nextInt(900000) + 100000); // 6-digit
        } while (organizationRepository.existsByOrganizationId(id));
        return id;
    }

    private String generateUniqueWorkLocationId() {
        String id;
        do {
            id = String.format("%07d", new Random().nextInt(9000000) + 1000000); // 7-digit
        } while (workLocationRepository.existsByWorkLocationId(id));
        return id;
    }

    // while setup organization first time after registration
    @Override
    @Transactional
    public OrganizationDTO updateOrganizationWithHeadOffice(String organizationId, OrganizationDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Get userId from JWT
        String userId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        // Update organization fields
        org.setOrganizationName(dto.getOrganizationName());
        org.setBusinessLocation(dto.getBusinessLocation());
        org.setIndustry(dto.getIndustry());
        org.setAddressLine1(dto.getAddressLine1());
        org.setAddressLine2(dto.getAddressLine2());
        org.setState(dto.getState());
        org.setCity(dto.getCity());
        org.setPinCode(dto.getPinCode());
        org.setHasRunPayroll(dto.getHasRunPayroll());
        org.setTimezone(dto.getTimezone());
        org.setupdatedBy(userId);
        org.setIsOrgActive(true);
        org.setIsDeleted(false);
        org.setEmail(dto.getEmail());

        // Always create a new Head Office WorkLocation
        WorkLocation headOffice = new WorkLocation();
        headOffice.setWorkLocationId(generateUniqueWorkLocationId());
        headOffice.setWorkLocationName("Head Office");
        headOffice.setOrganization(org);
        headOffice.setState(org.getState());
        headOffice.setCity(org.getCity());
        headOffice.setZipCode(org.getPinCode());
        headOffice.setStreetAddress1(org.getAddressLine1());
        headOffice.setStreetAddress2(org.getAddressLine2());
        headOffice.setIsFilingAddress(true);
        headOffice.setStatus(true);

        // Add it to organization
        org.getWorkLocations().add(headOffice);

        // Save everything
        Organization saved = organizationRepository.save(org);

        // Auto create ProfessionalTax for this org based on state
        professionalTaxService.createDefaultTax(org, saved.getState());

        // Auto-create SuperAdmin UserInvitation for the organization creator (if not
        // exists)
        // UserInvitationDTO superAdminInvitation = new UserInvitationDTO();
        // superAdminInvitation.setUserId(userId);
        // superAdminInvitation.setRoleId("admin"); // Use appropriate role ID
        // superAdminInvitation.setName("Super Admin");
        // superAdminInvitation.setEmail(org.getEmail());
        // superAdminInvitation.setInvitationType("AUTO_CREATED");
        // superAdminInvitation.setIsSuperAdmin(true);
        // superAdminInvitation.setStatus("active");
        // superAdminInvitation.setUserRole("admin");
        // superAdminInvitation.setIsEditable(false);
        // superAdminInvitation.setIsInvitationAccepted(true);

        // userInvitationService.createInvitation(org.getOrganizationId(),
        // superAdminInvitation);

        // log.info("✅ SuperAdmin UserInvitation created during org setup: {}",
        // org.getOrganizationId());

        return OrganizationMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public OrganizationDTO createOrganization(OrganizationDTO dto) {
        // Get companyUserId and email from token in one call
        String userId = JWTUtil.getUserIdAndEmailFromToken().get("userId");
        String email = JWTUtil.getUserIdAndEmailFromToken().get("email");
        String name = JWTUtil.getUserIdAndEmailFromToken().get("name");


        // Map DTO to entity
        Organization org = new Organization();
        org.setOrganizationName(dto.getOrganizationName());
        org.setBusinessLocation(dto.getBusinessLocation());
        org.setIndustry(dto.getIndustry());
        org.setAddressLine1(dto.getAddressLine1());
        org.setAddressLine2(dto.getAddressLine2());
        org.setState(dto.getState());
        org.setCity(dto.getCity());
        org.setPinCode(dto.getPinCode());
        org.setHasRunPayroll(dto.getHasRunPayroll());
        org.setTimezone(dto.getTimezone());
        org.setcreatedBy(userId);
        org.setEmail(email);
        org.setIsOrgActive(true);
        org.setIsDeleted(false);

        // Generate 6-digit organizationId
        org.setOrganizationId(generateUniqueOrganizationId());

        // Create default Head Office work location
        WorkLocation headOffice = new WorkLocation();
        headOffice.setWorkLocationId(generateUniqueWorkLocationId());
        headOffice.setWorkLocationName("Head Office");
        headOffice.setState(org.getState());
        headOffice.setCity(org.getCity());
        headOffice.setZipCode(org.getPinCode());
        headOffice.setStreetAddress1(org.getAddressLine1());
        headOffice.setStreetAddress2(org.getAddressLine2());
        headOffice.setIsFilingAddress(true);
        headOffice.setStatus(true);
        headOffice.setOrganization(org);

        // Attach default work location
        org.setWorkLocations(new ArrayList<>());
        org.getWorkLocations().add(headOffice);

        // Save organization
        Organization saved = organizationRepository.save(org);

        // Auto create ProfessionalTax for this org based on state
       // professionalTaxService.createDefaultTax(org, saved.getState());

        // Auto-create Admin Role for this organization
        OrganizationRoleDTO adminRoleDto = new OrganizationRoleDTO();
        adminRoleDto.setRoleName("admin");
        adminRoleDto.setAccessType("FULL");
        adminRoleDto.setUserActionRequired(false);
        adminRoleDto.setIsDefault(true);
        adminRoleDto.setRoleDescription("Default Admin role with full access");

        OrganizationRoleDTO savedRole = organizationRoleService.createRole(saved.getOrganizationId(), adminRoleDto);

        // Save OrganizationUserMapping
        OrganizationUserMapping mapping = new OrganizationUserMapping();
        mapping.setUserId(userId);
        mapping.setOrganizationId(saved.getOrganizationId());
        organizationUserMappingRepository.save(mapping);

        // Save OrganizationUserRoleMapping (assign Admin role to creator)
        OrganizationUserRoleMapping roleMapping = new OrganizationUserRoleMapping();
        roleMapping.setUserId(userId);
        roleMapping.setOrganizationId(saved.getOrganizationId());
        roleMapping.setRoleId(savedRole.getRoleId());
        roleMapping.setRoleName(savedRole.getRoleName());
        roleMapping.setEmployeePortalEnable(false);
        organizationUserRoleMappingRepository.save(roleMapping);

        // Auto-create User Invitation
        System.out.println("Auto-creating User Invitation for the while new Organization Adding");

        UserInvitationDTO invitationDTO = new UserInvitationDTO();
        invitationDTO.setRoleId(savedRole.getRoleId());
        invitationDTO.setName(name);
        invitationDTO.setEmail(email);
        invitationDTO.setInvitationType("AUTO_REGISTER");
        invitationDTO.setIsSuperAdmin(true);
        invitationDTO.setStatus("ACCEPTED");
        invitationDTO.setUserRole(savedRole.getRoleName());
        invitationDTO.setDeleted(false);
        invitationDTO.setIsEditable(false);
        invitationDTO.setOrganizationId(saved.getOrganizationId());

        userInvitationService.createInvitation(saved.getOrganizationId(), invitationDTO);

        System.out.println("=== User Invitation auto-created successfully ===");

        return OrganizationMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public OrganizationDTO updateOrganization(String organizationId, OrganizationDTO dto, MultipartFile file)
            throws Exception {
        String methodName = "updateOrganization";
        log.info("[{}] Start updating organization ID: {}", methodName, organizationId);

        Optional<Organization> optional = organizationRepository.findByOrganizationId(organizationId);
        if (optional.isEmpty()) {
            throw new RuntimeException("Organization not found");
        }

        // Get userId from JWT
        String userId = JWTUtil.getUserIdAndEmailFromToken().get("userId");

        Organization org = optional.get();

        // Update organization fields
        org.setOrganizationName(dto.getOrganizationName());
        org.setBusinessLocation(dto.getBusinessLocation());
        org.setIndustry(dto.getIndustry());
        org.setAddressLine1(dto.getAddressLine1());
        org.setAddressLine2(dto.getAddressLine2());
        org.setState(dto.getState());
        org.setCity(dto.getCity());
        org.setPinCode(dto.getPinCode());
        org.setHasRunPayroll(dto.getHasRunPayroll());
        org.setTimezone(dto.getTimezone());
        org.setupdatedBy(userId);
        org.setEmail(dto.getEmail());

        // Handle file upload if present
        if (file != null && !file.isEmpty()) {
            log.info("[{}] Uploading organization file: {}", methodName, file.getOriginalFilename());

            // Upload to Cloudinary with companyUserId
            CloudinaryUploadResponseDTO uploadResponse = cloudinaryService.uploadFile(file, userId);

            log.info("[{}] File uploaded successfully. URL: {}", methodName, uploadResponse.getSecure_url());

            // Save file details in entity
            org.setFileName(file.getOriginalFilename());
            org.setFileUrl(uploadResponse.getSecure_url());
            org.setFilePublicId(uploadResponse.getPublic_id());
        }

        // Update existing work locations only (no creation)
        if (dto.getWorkLocations() != null && !dto.getWorkLocations().isEmpty()) {
            Map<String, WorkLocation> existingLocations = org.getWorkLocations()
                    .stream()
                    .collect(Collectors.toMap(WorkLocation::getWorkLocationId, wl -> wl));

            for (WorkLocationDTO wdto : dto.getWorkLocations()) {
                if (wdto.getWorkLocationId() != null && existingLocations.containsKey(wdto.getWorkLocationId())) {
                    // Existing work location found — update fields
                    WorkLocation workLocation = existingLocations.get(wdto.getWorkLocationId());
                    workLocation.setWorkLocationName(wdto.getWorkLocationName());
                    workLocation.setStreetAddress1(wdto.getStreetAddress1());
                    workLocation.setStreetAddress2(wdto.getStreetAddress2());
                    workLocation.setCity(wdto.getCity());
                    workLocation.setState(wdto.getState());
                    workLocation.setZipCode(wdto.getZipCode());
                    workLocation.setState(wdto.getState());
                    workLocation.setIsFilingAddress(wdto.getIsFilingAddress());
                }
            }
        }

        Organization saved = organizationRepository.save(org);
        log.info("[{}] Organization updated successfully: {}", methodName, saved.getOrganizationId());

        return OrganizationMapper.toDTO(saved);
    }

    // @Override
    // public OrganizationDTO getOrganizationByOrganizationId(String organizationId)
    // {
    // return organizationRepository.findByOrganizationId(organizationId)
    // .map(org -> {
    // // Filter work locations before mapping
    // org.setWorkLocations(
    // org.getWorkLocations().stream()
    // .filter(WorkLocation::getIsFilingAddress)
    // .collect(Collectors.toList())
    // );
    //
    // return OrganizationMapper.toDTO(org);
    // })
    // .orElseThrow(() -> new RuntimeException("Organization not found"));
    // }

    @Override
    public OrganizationDTO getOrganizationByOrganizationId(String organizationId) {
        // Fetch organization with all work locations eagerly
        Organization org = organizationRepository
                .findWithWorkLocationsByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Map full organization + all work locations
        OrganizationDTO dto = OrganizationMapper.toDTO(org);

        // Identify the filing work location (if any)
        org.getWorkLocations().stream()
                .filter(WorkLocation::getIsFilingAddress)
                .findFirst()
                .ifPresent(filing -> dto.setFilingWorkLocationId(filing.getWorkLocationId()));

        return dto;
    }

    @Override
    public List<OrganizationDTO> getAllOrganizations(String userId) {
        // 1. Fetch all organizationIds associated with this user from role mapping
        List<String> organizationIds = organizationUserRoleMappingRepository
                .findByUserId(userId)
                .stream()
                .map(OrganizationUserRoleMapping::getOrganizationId)
                .toList();

        if (organizationIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Fetch active organizations by those IDs
        List<Organization> organizations = organizationRepository
                .findByOrganizationIdInAndIsDeletedFalse(organizationIds);

        // 3. Map to DTO
        return organizations.stream()
                .map(OrganizationMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteOrganization(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        org.setIsDeleted(true);
        organizationRepository.save(org);
    }

    @Override
    public List<Map<String, Object>> getActiveOrganizationsForUser(String userId) {
        // 1. Get all organizationIds linked to this user
        List<String> organizationIds = organizationUserRoleMappingRepository
                .findByUserId(userId)
                .stream()
                .map(OrganizationUserRoleMapping::getOrganizationId)
                .toList();

        if (organizationIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Fetch all active organizations for these IDs
        List<Organization> organizations = organizationRepository
                .findByOrganizationIdInAndIsDeletedFalseAndIsOrgActiveTrue(organizationIds);

        // 3. Map to required response
        return organizations.stream()
                .map(org -> {
                    Map<String, Object> orgMap = new HashMap<>();
                    orgMap.put("organizationId", org.getOrganizationId());
                    orgMap.put("organizationName", org.getOrganizationName());
                    orgMap.put("createdDate", org.getcreatedDate());
                    orgMap.put("isOrgActive", org.getIsOrgActive());
                    return orgMap;
                })
                .collect(Collectors.toList());
    }

    @Override
    public String deleteOrganizationFile(String organizationId) {
        String methodName = "deleteOrganizationFile";

        try {
            Optional<Organization> optionalOrg = organizationRepository.findByOrganizationId(organizationId);
            log.debug("[{}] Organization lookup completed for ID: {}", methodName, organizationId);

            if (optionalOrg.isEmpty()) {
                log.warn("[{}] Organization not found for ID: {}", methodName, organizationId);
                return "Organization not found";
            }

            Organization org = optionalOrg.get();

            if (org.getFilePublicId() == null || org.getFilePublicId().isEmpty()) {
                log.warn("[{}] No file exists for this organization ID: {}", methodName, organizationId);
                return "No file exists for this organization";
            }

            log.info("[{}] Attempting to delete file from Cloudinary. Public ID: {}", methodName,
                    org.getFilePublicId());
            boolean deleted = cloudinaryService.deleteFile(org.getFilePublicId());

            if (!deleted) {
                log.error("[{}] Failed to delete file from Cloudinary for Public ID: {}", methodName,
                        org.getFilePublicId());
                return "Failed to delete file from Cloudinary";
            }

            // Clear file info in the database
            org.setFileUrl(null);
            org.setFilePublicId(null);
            org.setFileName(null);
            organizationRepository.save(org);

            log.info("[{}] File deleted successfully for organization ID: {}", methodName, organizationId);
            return "File deleted successfully";

        } catch (Exception e) {
            log.error("[{}] Error deleting file for organization ID: {}", methodName, organizationId, e);
            return "Error deleting file";
        }
    }

    @Override
    @Transactional
    public void setFilingAddress(String organizationId, String workLocationId) {
        String methodName = "setFilingAddress";
        log.info("[{}] Start - Setting filing address for organizationId: {}, workLocationId: {}",
                methodName, organizationId, workLocationId);

        // Fetch organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] Organization not found for organizationId: {}", methodName, organizationId);
                    return new RuntimeException("Organization not found");
                });

        log.info("[{}] Organization found: {} | Total work locations: {}",
                methodName, org.getOrganizationName(),
                org.getWorkLocations().size());

        if (org.getWorkLocations() == null || org.getWorkLocations().isEmpty()) {
            log.error("[{}] No work locations found for organizationId: {}", methodName, organizationId);
            throw new RuntimeException("No work locations found for this organization");
        }

        boolean found = false;

        for (WorkLocation wl : org.getWorkLocations()) {
            if (wl.getWorkLocationId().equals(workLocationId)) {
                log.info("[{}] Marking workLocationId: {} ({}) as filing address",
                        methodName, wl.getWorkLocationId(), wl.getWorkLocationName());
                wl.setIsFilingAddress(true);
                found = true;
            } else if (Boolean.TRUE.equals(wl.getIsFilingAddress())) {
                log.info("[{}] Unmarking previous filing address from workLocationId: {} ({})",
                        methodName, wl.getWorkLocationId(), wl.getWorkLocationName());
                wl.setIsFilingAddress(false);
            }
        }

        if (!found) {
            log.error("[{}] Work location not found for workLocationId: {} in organizationId: {}",
                    methodName, workLocationId, organizationId);
            throw new RuntimeException("Work location not found for ID: " + workLocationId);
        }

        organizationRepository.save(org);
        log.info("[{}] Filing address updated successfully for organizationId: {}", methodName, organizationId);
        log.info("[{}] End - Filing address set successfully", methodName);
    }

}
