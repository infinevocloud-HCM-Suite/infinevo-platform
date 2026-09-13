//package com.itsdev.payroll.serviceimpl.employee;
//
//
//// BasicDetailsServiceImpl.java
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
//import com.itsdev.payroll.entity.employee.BasicDetails;
//import com.itsdev.payroll.entity.organization.Organization;
//import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
//import com.itsdev.payroll.repository.organization.OrganizationRepository;
//import com.itsdev.payroll.service.employee.BasicDetailsService;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//import java.util.stream.Collectors;
//@Service
//public class BasicDetailsServiceImpl implements BasicDetailsService {
//
//    private final BasicDetailsRepository basicDetailsRepository;
//    private final OrganizationRepository organizationRepository;
//
//    public BasicDetailsServiceImpl(BasicDetailsRepository basicDetailsRepository,
//                                   OrganizationRepository organizationRepository) {
//        this.basicDetailsRepository = basicDetailsRepository;
//        this.organizationRepository = organizationRepository;
//    }
//
//    private String generateEmployeeNumber() {
//        Random random = new Random();
//        return String.format("%010d", random.nextInt(1_000_000_000));
//    }
//
//    @Override
//    @Transactional
//    public BasicDetailsDTO createBasicDetails(String organizationId, BasicDetailsDTO dto) {
//
//        Organization org = organizationRepository.findByOrganizationId(organizationId)
//                .orElseThrow(() -> new RuntimeException("Organization not found"));
//
//        BasicDetails entity = new BasicDetails();
//        entity.setOrganization(org); // ✅ set organization
//        entity.setEmployeeNumber(dto.getEmployeeNumber() != null ? dto.getEmployeeNumber() : generateEmployeeNumber());
//        entity.setFirstName(dto.getFirstName());
//        entity.setMiddleName(dto.getMiddleName());
//        entity.setLastName(dto.getLastName());
//        entity.setGender(dto.getGender());
//        entity.setDateOfJoining(dto.getDateOfJoining());
//        entity.setDepartmentId(dto.getDepartmentId());
//        entity.setDesignationId(dto.getDesignationId());
//        entity.setWorkLocationId(dto.getWorkLocationId());
//        entity.setEmployeeStatus(dto.getEmployeeStatus());
//        entity.setPortalEnabled(dto.getPortalEnabled());
//        entity.setEligibleForPf(dto.getEligibleForPf());
//        entity.setEligibleForPt(dto.getEligibleForPt());
//        entity.setEligibleForLwf(dto.getEligibleForLwf());
//
//        entity.setEligibleForEsi(dto.getEligibleForEsi());
//
//        entity.setMobile(dto.getMobile());
//        entity.setWorkMail(dto.getWorkMail());
//        entity.setPfAccountNumber(dto.getPfAccountNumber());
//        entity.setUan(dto.getUan());
//        entity.setTags(dto.getTags());
//
//
//        // ✅ Newly added fields from payload
//        entity.setDirector(dto.getDirector());
//        entity.setEligibleForEps(dto.getEligibleForEps());
//        entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
//
//        BasicDetails saved = basicDetailsRepository.save(entity);
//        dto.setId(saved.getId());
//        return dto;
//    }
//
//
//    @Override
//    @Transactional
//    public BasicDetailsDTO updateBasicDetails(String organizationId, String basicDetailsId, BasicDetailsDTO dto) {
//        Organization org = organizationRepository.findByOrganizationId(organizationId)
//                .orElseThrow(() -> new RuntimeException("Organization not found"));
//
//        Long id = Long.parseLong(basicDetailsId);
//        BasicDetails entity = basicDetailsRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//
//        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
//            throw new RuntimeException("Employee does not belong to this organization");
//        }
//
//        entity.setFirstName(dto.getFirstName());
//        entity.setMiddleName(dto.getMiddleName());
//        entity.setLastName(dto.getLastName());
//        entity.setGender(dto.getGender());
//        entity.setDateOfJoining(dto.getDateOfJoining());
//        entity.setDepartmentId(dto.getDepartmentId());
//        entity.setDesignationId(dto.getDesignationId());
//        entity.setWorkLocationId(dto.getWorkLocationId());
//        entity.setEmployeeStatus(dto.getEmployeeStatus());
//        entity.setPortalEnabled(dto.getPortalEnabled());
//        entity.setEligibleForPf(dto.getEligibleForPf());
//        entity.setEligibleForPt(dto.getEligibleForPt());
//        entity.setEligibleForLwf(dto.getEligibleForLwf());
//
//        entity.setEligibleForEsi(dto.getEligibleForEsi());
//
//        entity.setMobile(dto.getMobile());
//        entity.setWorkMail(dto.getWorkMail());
//        entity.setPfAccountNumber(dto.getPfAccountNumber());
//        entity.setUan(dto.getUan());
//       // entity.setTags(dto.getTags());
//        entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
//
//        entity.setOrganization(org);
//
//
//        // ✅ Newly added fields from payload
//        entity.setDirector(dto.getDirector());
//        entity.setEligibleForEps(dto.getEligibleForEps());
//        entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
//
//        basicDetailsRepository.save(entity);
//        dto.setId(entity.getId());
//        return dto;
//    }
//
//    @Override
//    public BasicDetailsDTO getBasicDetails(String organizationId, String basicDetailsId) {
//        Long id = Long.parseLong(basicDetailsId);
//        BasicDetails entity = basicDetailsRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//
//        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
//            throw new RuntimeException("Employee does not belong to this organization");
//        }
//
//        BasicDetailsDTO dto = new BasicDetailsDTO();
//        dto.setId(entity.getId());
//
//        dto.setEmployeeNumber(entity.getEmployeeNumber());
//        dto.setFirstName(entity.getFirstName());
//        dto.setMiddleName(entity.getMiddleName());
//        dto.setLastName(entity.getLastName());
//        dto.setGender(entity.getGender());
//
//
//        dto.setDateOfJoining(entity.getDateOfJoining());
//
//
//
//        dto.setDepartmentId(entity.getDepartmentId());
//        dto.setDesignationId(entity.getDesignationId());
//        dto.setWorkLocationId(entity.getWorkLocationId());
//        dto.setEmployeeStatus(entity.getEmployeeStatus());
//        dto.setPortalEnabled(entity.getPortalEnabled());
//        dto.setEligibleForPf(entity.getEligibleForPf());
//        dto.setEligibleForPt(entity.getEligibleForPt());
//        dto.setEligibleForLwf(entity.getEligibleForLwf());
//
//        //dto.setIsEligibleForEsi(entity.getIsEligibleForEsi());
//
//        dto.setEligibleForEsi(entity.getEligibleForEsi());
//
//
//        dto.setMobile(entity.getMobile());
//        dto.setWorkMail(entity.getWorkMail());
//        dto.setPfAccountNumber(entity.getPfAccountNumber());
//        dto.setUan(entity.getUan());
//       // dto.setTags(entity.getTags());
//        dto.setTags(entity.getTags() != null ? entity.getTags() : new ArrayList<>());
//
//        dto.setOrganizationId(entity.getOrganization().getOrganizationId());
//
//        // ✅ Newly added fields
//        dto.setDirector(entity.getDirector());
//        dto.setEligibleForEps(entity.getEligibleForEps());
//        dto.setCanContributeToEpsOnHigherWages(entity.getCanContributeToEpsOnHigherWages());
//
//        return dto;
//    }
//
////    @Override
////    public List<BasicDetailsDTO> getAllBasicDetails(String organizationId) {
////        List<BasicDetails> list = basicDetailsRepository.findByOrganization_OrganizationId(organizationId);
////        return list.stream().map(entity -> {
////            BasicDetailsDTO dto = new BasicDetailsDTO();
////            dto.setId(entity.getId());
////            dto.setEmployeeNumber(entity.getEmployeeNumber());
////            dto.setFirstName(entity.getFirstName());
////            dto.setMiddleName(entity.getMiddleName());
////            dto.setLastName(entity.getLastName());
////            dto.setGender(entity.getGender());
////            dto.setDateOfJoining(entity.getDateOfJoining());
////            dto.setDepartmentId(entity.getDepartmentId());
////            dto.setDesignationId(entity.getDesignationId());
////            dto.setWorkLocationId(entity.getWorkLocationId());
////            dto.setEmployeeStatus(entity.getEmployeeStatus());
////            dto.setIsPortalEnabled(entity.getIsPortalEnabled());
////            dto.setIsEligibleForPf(entity.getIsEligibleForPf());
////            dto.setIsEligibleForPt(entity.getIsEligibleForPt());
////            dto.setIsEligibleForLwf(entity.getIsEligibleForLwf());
////            dto.setMobile(entity.getMobile());
////            dto.setWorkMail(entity.getWorkMail());
////            dto.setPfAccountNumber(entity.getPfAccountNumber());
////            dto.setUan(entity.getUan());
////           // dto.setTags(entity.getTags());
////            dto.setTags(entity.getTags() != null ? entity.getTags() : new ArrayList<>());
////
////            dto.setOrganizationId(entity.getOrganization().getOrganizationId());
////
////
////            // ✅ Newly added fields from payload
////            dto.setDirector(entity.getDirector());
////            dto.setEligibleForEps(entity.getEligibleForEps());
////            dto.setCanContributeToEpsOnHigherWages(entity.getCanContributeToEpsOnHigherWages());
////            return dto;
////        }).toList();
////    }
//
//
//
//
//
//
//    @Override
//    public Page<BasicDetailsDTO> getAllBasicDetails(String organizationId, Pageable pageable) {
//        Page<BasicDetails> page = basicDetailsRepository.findByOrganization_OrganizationId(organizationId, pageable);
//
//        return page.map(this::mapToDto);
//    }
//
//    // helper mapper inside the class
//    private BasicDetailsDTO mapToDto(BasicDetails entity) {
//        BasicDetailsDTO dto = new BasicDetailsDTO();
//        dto.setId(entity.getId());
//        dto.setEmployeeNumber(entity.getEmployeeNumber());
//        dto.setFirstName(entity.getFirstName());
//        dto.setMiddleName(entity.getMiddleName());
//        dto.setLastName(entity.getLastName());
//        dto.setGender(entity.getGender());
//        dto.setDateOfJoining(entity.getDateOfJoining());
//        dto.setDepartmentId(entity.getDepartmentId());
//        dto.setDesignationId(entity.getDesignationId());
//        dto.setWorkLocationId(entity.getWorkLocationId());
//        dto.setEmployeeStatus(entity.getEmployeeStatus());
//        dto.setPortalEnabled(entity.getPortalEnabled());
//        dto.setEligibleForPf(entity.getEligibleForPf());
//        dto.setEligibleForPt(entity.getEligibleForPt());
//        dto.setEligibleForLwf(entity.getEligibleForLwf());
//
//       // dto.setIsEligibleForEsi(entity.getIsEligibleForEsi());
//        entity.setEligibleForEsi(dto.getEligibleForEsi());
//
//        dto.setMobile(entity.getMobile());
//        dto.setWorkMail(entity.getWorkMail());
//        dto.setPfAccountNumber(entity.getPfAccountNumber());
//        dto.setUan(entity.getUan());
//        // normalize tags if you prefer non-null:
//        dto.setTags(entity.getTags() != null ? entity.getTags() : new ArrayList<>());
//        dto.setOrganizationId(entity.getOrganization().getOrganizationId());
//
//        // newly added payload fields
//        dto.setDirector(entity.getDirector());
//        dto.setEligibleForEps(entity.getEligibleForEps());
//        dto.setCanContributeToEpsOnHigherWages(entity.getCanContributeToEpsOnHigherWages());
//
//        return dto;
//    }
//
//
//
//
//
//
//
//    @Override
//    @Transactional
//    public void deleteBasicDetails(String organizationId, String basicDetailsId) {
//        Long id = Long.parseLong(basicDetailsId);
//        BasicDetails entity = basicDetailsRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//
//        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
//            throw new RuntimeException("Employee does not belong to this organization");
//        }
//
//        basicDetailsRepository.delete(entity);
//    }
//
//    @Override
//    @Transactional
//    public void saveAll(String organizationId, List<BasicDetailsDTO> basicDetailsList) {
//        Organization org = organizationRepository.findByOrganizationId(organizationId)
//                .orElseThrow(() -> new RuntimeException("Organization not found"));
//
//        List<BasicDetails> entities = basicDetailsList.stream().map(dto -> {
//            BasicDetails entity = new BasicDetails();
//            entity.setEmployeeNumber(dto.getEmployeeNumber() != null ? dto.getEmployeeNumber() : generateEmployeeNumber());
//            entity.setFirstName(dto.getFirstName());
//            entity.setMiddleName(dto.getMiddleName());
//            entity.setLastName(dto.getLastName());
//            entity.setGender(dto.getGender());
//            entity.setDateOfJoining(dto.getDateOfJoining());
//            entity.setDepartmentId(dto.getDepartmentId());
//            entity.setDesignationId(dto.getDesignationId());
//            entity.setWorkLocationId(dto.getWorkLocationId());
//            entity.setEmployeeStatus(dto.getEmployeeStatus());
//            entity.setPortalEnabled(dto.getPortalEnabled());
//            entity.setEligibleForPf(dto.getEligibleForPf());
//            entity.setEligibleForPt(dto.getEligibleForPt());
//            entity.setEligibleForLwf(dto.getEligibleForLwf());
//
//            //  entity.setIsEligibleForEsi(dto.getIsEligibleForEsi());
//
//            entity.setEligibleForEsi(dto.getEligibleForEsi());
//
//            entity.setMobile(dto.getMobile());
//            entity.setWorkMail(dto.getWorkMail());
//            entity.setPfAccountNumber(dto.getPfAccountNumber());
//            entity.setUan(dto.getUan());
//           // entity.setTags(dto.getTags());
//
//            // 👉 Either allow null or fallback to empty list
//            entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
//
//
//            entity.setOrganization(org);
//
//
//
//            // ✅ Newly added fields from payload
//            entity.setDirector(dto.getDirector());
//            entity.setEligibleForEps(dto.getEligibleForEps());
//            entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
//
//
//
//            return entity;
//        }).toList();
//
//        basicDetailsRepository.saveAll(entities);
//    }
//}

package com.itsdev.payroll.serviceimpl.employee;

import com.itsdev.payroll.dto.employee.EmployeeInvitationDTO;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.service.employee.EmployeeInvitationService;
import com.itsdev.payroll.service.keycloak.KeycloakUserService;
import com.itsdev.payroll.specs.BasicDetailsSpecs;
import com.itsdev.payroll.util.JWTUtil;

import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.itsdev.payroll.dto.employee.BasicDetailsDTO;
import com.itsdev.payroll.dto.employee.EmployeeDetailsDTO;
import com.itsdev.payroll.dto.employee.ResidentialAddressDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.EmployeeBankDetail;
import com.itsdev.payroll.entity.employee.EmployeePersonalDetail;
import com.itsdev.payroll.entity.employee.ResidentialAddress;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.organization.Department;
import com.itsdev.payroll.entity.organization.Designation;
import com.itsdev.payroll.entity.organization.WorkLocation;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.organization.DepartmentRepository;
import com.itsdev.payroll.repository.organization.DesignationRepository;
import com.itsdev.payroll.repository.organization.WorkLocationRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import com.itsdev.payroll.service.CompanyUserService;
import com.itsdev.payroll.service.employee.BasicDetailsService;
import com.itsdev.payroll.dto.CompanyUserDTO;
import com.itsdev.payroll.entity.CompanyUser;
import com.itsdev.payroll.repository.CompanyUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

@Service
public class BasicDetailsServiceImpl implements BasicDetailsService {

    private final BasicDetailsRepository basicDetailsRepository;
    private final OrganizationRepository organizationRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final WorkLocationRepository workLocationRepository;
    private final EmployeeInvitationService employeeInvitationService;
    private final KeycloakUserService keycloakService;
    private final CtcStructureRepository ctcStructureRepository;
    private final CompanyUserService companyUserService;
    private final BrevoEmailService emailService;
    private final CompanyUserRepository companyUserRepository;

    private static final Logger log = LoggerFactory.getLogger(BasicDetailsServiceImpl.class);

    public BasicDetailsServiceImpl(BasicDetailsRepository basicDetailsRepository,
            OrganizationRepository organizationRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            WorkLocationRepository workLocationRepository,
            EmployeeInvitationService employeeInvitationService,
            KeycloakUserService keycloakService,
            CtcStructureRepository ctcStructureRepository,
            CompanyUserService companyUserService,
            BrevoEmailService emailService,
            CompanyUserRepository companyUserRepository) {
        this.basicDetailsRepository = basicDetailsRepository;
        this.organizationRepository = organizationRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.workLocationRepository = workLocationRepository;
        this.employeeInvitationService = employeeInvitationService;
        this.keycloakService = keycloakService;
        this.ctcStructureRepository = ctcStructureRepository;
        this.companyUserService = companyUserService;
        this.emailService = emailService;
        this.companyUserRepository = companyUserRepository;
    }

    private String generateEmployeeNumber() {
        Random random = new Random();
        return String.format("%010d", random.nextInt(1_000_000_000));
    }

    // @Override
    // @Transactional
    // public BasicDetailsDTO createBasicDetails(String organizationId,
    // BasicDetailsDTO dto) {
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> new RuntimeException("Organization not found"));
    //
    // BasicDetails entity = new BasicDetails();
    // entity.setOrganization(org);
    // entity.setEmployeeNumber(dto.getEmployeeNumber() != null ?
    // dto.getEmployeeNumber() : generateEmployeeNumber());
    // entity.setFirstName(dto.getFirstName());
    // entity.setMiddleName(dto.getMiddleName());
    // entity.setLastName(dto.getLastName());
    // entity.setGender(dto.getGender());
    // entity.setDateOfJoining(dto.getDateOfJoining());
    //
    // // relationships
    // if (dto.getDepartmentId() != null) {
    // Department dept =
    // departmentRepository.findByDepartmentId(dto.getDepartmentId())
    // .orElseThrow(() -> new RuntimeException("Department not found"));
    // entity.setDepartment(dept);
    // }
    // if (dto.getDesignationId() != null) {
    // Designation desig =
    // designationRepository.findByDesignationId(dto.getDesignationId())
    // .orElseThrow(() -> new RuntimeException("Designation not found"));
    // entity.setDesignation(desig);
    // }
    // if (dto.getWorkLocationId() != null) {
    // WorkLocation loc =
    // workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
    // .orElseThrow(() -> new RuntimeException("Work Location not found"));
    // entity.setWorkLocation(loc);
    // }
    //
    // entity.setEmployeeStatus(dto.getEmployeeStatus());
    // entity.setPortalEnabled(dto.getPortalEnabled());
    // entity.setEligibleForPf(dto.getEligibleForPf());
    // entity.setEligibleForPt(dto.getEligibleForPt());
    // entity.setEligibleForLwf(dto.getEligibleForLwf());
    // entity.setEligibleForEsi(dto.getEligibleForEsi());
    // entity.setMobile(dto.getMobile());
    // entity.setWorkMail(dto.getWorkMail());
    // entity.setPfAccountNumber(dto.getPfAccountNumber());
    // entity.setUan(dto.getUan());
    // entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
    //
    // // new fields
    // entity.setDirector(dto.getDirector());
    // entity.setEligibleForEps(dto.getEligibleForEps());
    // entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
    //
    // BasicDetails saved = basicDetailsRepository.save(entity);
    // dto.setId(saved.getId());
    // return dto;
    // }

    @Override
    @Transactional
    public BasicDetailsDTO createBasicDetails(String organizationId, BasicDetailsDTO dto) {
        String method = "createBasicDetails";
        log.info("[{}] 📥 Incoming request: organizationId={}, dto={}", method, organizationId, dto);

        // --- Organization lookup ---
        log.info("[{}] 🔍 Fetching Organization by ID: {}", method, organizationId);
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Organization not found for ID: {}", method, organizationId);
                    return new RuntimeException("Organization not found");
                });
        log.info("[{}] ✅ Organization found: {}", method, org.getOrganizationName());


        // --- Check if employee already exists in same organization with same email ---
        log.info("[{}] 🔎 Checking if BasicDetails already exists for email={} in organization={}",
                method, dto.getWorkMail(), organizationId);

        Optional<BasicDetails> existingEmployee =
                basicDetailsRepository.findByOrganization_OrganizationIdAndWorkMail(
                        organizationId,
                        dto.getWorkMail());

        if (existingEmployee.isPresent()) {

            log.error("[{}] ❌ Employee already exists with email={} in organization={}",
                    method, dto.getWorkMail(), organizationId);

            throw new RuntimeException(
                    "Employee already exists with email: " + dto.getWorkMail()
            );
        }

        log.info("[{}] ✅ No existing employee found with email={}", method, dto.getWorkMail());


        // --- Check if work email already exists ---
        log.info("[{}] 🔎 Checking if work email exists in DB and Keycloak: {}", method, dto.getWorkMail());

        String keycloakUserId;
        try {
            // Try to get existing user from Keycloak
            keycloakUserId = keycloakService.getUserIdByEmail(dto.getWorkMail());
            log.info("[{}] ✅ Email already exists in Keycloak. Using existing userId: {}", method, keycloakUserId);
        } catch (RuntimeException e) {
            // If user doesn't exist, create via CompanyUserServiceImpl
            log.info("[{}] 🔑 Creating employee user via CompanyUserServiceImpl for email: {}", method,
                    dto.getWorkMail());

            // Set organizationId in DTO and call directly
            dto.setOrganizationId(organizationId);

            // organizationEmployeeRegistration now takes BasicDetailsDTO and returns String
            // userId
            keycloakUserId = companyUserService.organizationEmployeeRegistration(dto);

            log.info("[{}] ✅ Employee created via CompanyUserServiceImpl, userId={}", method, keycloakUserId);
        }

        // --- Populate BasicDetails entity ---
        BasicDetails entity = new BasicDetails();
        entity.setOrganization(org);
        String empNumber = dto.getEmployeeNumber() != null ? dto.getEmployeeNumber() : generateEmployeeNumber();
        entity.setEmployeeNumber(empNumber);
        log.info("[{}] Assigned employeeNumber: {}", method, empNumber);

        // --- Set employeeId from Keycloak userId ---
        entity.setEmployeeId(keycloakUserId);

        entity.setFirstName(dto.getFirstName());
        entity.setMiddleName(dto.getMiddleName());
        entity.setLastName(dto.getLastName());
        entity.setGender(dto.getGender());
        entity.setDateOfJoining(dto.getDateOfJoining());
        log.info("[{}] Personal details: firstName={}, lastName={}, gender={}, DOJ={}",
                method, dto.getFirstName(), dto.getLastName(), dto.getGender(), dto.getDateOfJoining());

        // --- Relationships ---
        if (dto.getDepartmentId() != null) {
            log.info("[{}] 🔍 Fetching Department ID: {}", method, dto.getDepartmentId());
            Department dept = departmentRepository.findByDepartmentId(dto.getDepartmentId())
                    .orElseThrow(() -> {
                        log.error("[{}] ❌ Department not found: {}", method, dto.getDepartmentId());
                        return new RuntimeException("Department not found");
                    });
            entity.setDepartment(dept);
            log.info("[{}] ✅ Department set: {}", method, dept.getName());
        }

        if (dto.getDesignationId() != null) {
            log.info("[{}] 🔍 Fetching Designation ID: {}", method, dto.getDesignationId());
            Designation desig = designationRepository.findByDesignationId(dto.getDesignationId())
                    .orElseThrow(() -> {
                        log.error("[{}] ❌ Designation not found: {}", method, dto.getDesignationId());
                        return new RuntimeException("Designation not found");
                    });
            entity.setDesignation(desig);
            log.info("[{}] ✅ Designation set: {}", method, desig.getName());
        }

        if (dto.getWorkLocationId() != null) {
            log.info("[{}] 🔍 Fetching WorkLocation ID: {}", method, dto.getWorkLocationId());
            WorkLocation loc = workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
                    .orElseThrow(() -> {
                        log.error("[{}] ❌ WorkLocation not found: {}", method, dto.getWorkLocationId());
                        return new RuntimeException("Work Location not found");
                    });
            entity.setWorkLocation(loc);
            log.info("[{}] ✅ WorkLocation set: {}", method, loc.getWorkLocationName());
        }

        // --- Misc fields ---
        entity.setEmployeeStatus("ACTIVE");
        entity.setPortalEnabled(dto.getIsPortalEnabled());
        entity.setEligibleForPf(dto.getEligibleForPf());
        entity.setEligibleForPt(dto.getEligibleForPt());
        entity.setEligibleForLwf(dto.getEligibleForLwf());
        entity.setEligibleForEsi(dto.getEligibleForEsi());
        entity.setEsiNumber(dto.getEsiNumber());
        entity.setMobile(dto.getMobile());
        entity.setWorkMail(dto.getWorkMail());
        entity.setPfAccountNumber(dto.getPfAccountNumber());
        entity.setUan(dto.getUan());
        entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
        log.info("[{}] Employment details set: status={}, portalEnabled={}, workMail={}",
                method, dto.getEmployeeStatus(), dto.getIsPortalEnabled(), dto.getWorkMail());

        // --- New fields ---
        entity.setDirector(dto.getDirector());
        entity.setEligibleForEps(dto.getEligibleForEps());
        entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
        String employeeUniqueId = generateEmployeeUniqueId();
        entity.setEmployeeUniqueId(employeeUniqueId);
        entity.setHrUser(dto.getHrUser());
        log.debug("[{}] Generated employeeUniqueId: {}", method, employeeUniqueId);

        // --- Save entity ---
        log.info("[{}] 💾 Saving BasicDetails entity...", method);
        BasicDetails saved = basicDetailsRepository.save(entity);
        log.info("[{}] ✅ Saved BasicDetails with ID: {}", method, saved.getId());

        // --- Send employee invitation email ---
        if (Boolean.TRUE.equals(saved.getPortalEnabled())) {
            try {
                log.info("[{}] 📧 Sending employee invitation email to: {}", method, dto.getWorkMail());
                String inviterName = JWTUtil.getCurrentUserName();

                // Check if invitation already exists before creating (search by employeeId or email)
                Optional<EmployeeInvitationDTO> existingInvitation = employeeInvitationService
                        .findByEmployeeId(organizationId, saved.getEmployeeId());

                if (existingInvitation.isEmpty()) {
                    try {
                        EmployeeInvitationDTO invByEmail = employeeInvitationService
                                .findByEmailAndOrganization(saved.getWorkMail(), organizationId);
                        existingInvitation = Optional.of(invByEmail);
                    } catch (Exception ex) {
                        // ignore if not found by email
                    }
                }

                EmployeeInvitationDTO savedInvitation;

                if (existingInvitation.isPresent()) {
                    // Use existing invitation
                    EmployeeInvitationDTO existing = existingInvitation.get();
                    
                    // Auto-migrate employeeId if it was the old numeric ID
                    if (!saved.getEmployeeId().equals(existing.getEmployeeId())) {
                        existing.setEmployeeId(saved.getEmployeeId());
                        existing.setEmail(saved.getWorkMail());
                        savedInvitation = employeeInvitationService.updateInvitation(organizationId, existing.getInvitationId(), existing);
                        log.info("[{}] 🔄 Migrated existing invitation to new Keycloak UUID: {}", method, saved.getEmployeeId());
                    } else {
                        savedInvitation = existing;
                        log.info("[{}] ✅ Using existing invitation ID: {}", method, savedInvitation.getInvitationId());
                    }
                } else {
                    // Create new invitation only if it doesn't exist
                    EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
                    invitationDto.setEmail(saved.getWorkMail());
                    invitationDto.setEmployeeId(saved.getEmployeeId());
                    invitationDto.setIsPortalEnabled(saved.getPortalEnabled());
                    invitationDto.setIsInvitationAccepted(false);

                    savedInvitation = employeeInvitationService.createInvitation(
                            saved.getOrganization().getOrganizationId(),
                            invitationDto);
                }

                // Send email with the token
                boolean emailSent = emailService.sendEmployeeInvitationEmail(
                        dto.getWorkMail(),
                        dto.getFirstName() + " " + dto.getLastName(),
                        org.getOrganizationName(),
                        inviterName,
                        "Employee",
                        emailService.calculateExpiryDate(),
                        savedInvitation.getAcceptanceToken(),
                        organizationId); // ← Added missing 8th parameter

                if (emailSent) {
                    log.info("[{}] ✅ Employee invitation email sent successfully to: {}", method, dto.getWorkMail());
                } else {
                    log.warn("[{}] ⚠️ Failed to send invitation email to: {}", method, dto.getWorkMail());
                }
            } catch (Exception e) {
                log.error("[{}] ❌ Error sending invitation email: {}", method, e.getMessage());
                // Don't throw exception - email failure shouldn't break employee creation
            }
        } else {
            log.info("[{}] Portal not enabled; skipping invitation email dispatch.", method);
        }

        // // --- Portal invitation ---
        // if (Boolean.TRUE.equals(saved.getPortalEnabled())) {
        // log.info("[{}] 📧 Creating EmployeeInvitation for workMail: {}", method,
        // saved.getWorkMail());
        // EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
        // invitationDto.setEmail(saved.getWorkMail());
        // invitationDto.setEmployeeId(saved.getEmployeeId());
        // invitationDto.setIsPortalEnabled(saved.getPortalEnabled());
        // invitationDto.setIsInvitationAccepted(false);

        // employeeInvitationService.createInvitation(
        // saved.getOrganization().getOrganizationId(),
        // invitationDto);
        // log.info("[{}] ✅ EmployeeInvitation created.", method);
        // } else {
        // log.info("[{}] Portal not enabled; skipping invitation.", method);
        // }

        BasicDetailsDTO response = mapToDto(saved);
        log.info("[{}] 🏁 Returning response DTO: {}", method, response);
        return response;
    }

    // @Override
    // @Transactional
    // public BasicDetailsDTO createBasicDetails(String organizationId,
    // BasicDetailsDTO dto) {
    // String method = "createBasicDetails";
    // log.info("[{}] 📥 Incoming request: organizationId={}, dto={}", method,
    // organizationId, dto);

    // // --- Organization lookup ---
    // log.info("[{}] 🔍 Fetching Organization by ID: {}", method, organizationId);
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> {
    // log.error("[{}] ❌ Organization not found for ID: {}", method,
    // organizationId);
    // return new RuntimeException("Organization not found");
    // });
    // log.info("[{}] ✅ Organization found: {}", method, org.getOrganizationName());

    // // --- Check if work email already exists ---
    // log.info("[{}] 🔎 Checking if work email exists in DB and Keycloak: {}",
    // method, dto.getWorkMail());
    // String keycloakUserId;
    // boolean emailExists = keycloakService.isEmailExists(dto.getWorkMail());

    // if (emailExists) {
    // log.info("[{}] ✅ Email already exists. Fetching userId from Keycloak for
    // email: {}", method,
    // dto.getWorkMail());
    // // Fetch Keycloak user ID
    // keycloakUserId = keycloakService.getUserIdByEmail(dto.getWorkMail());
    // log.info("[{}] ✅ Fetched existing Keycloak userId={}", method,
    // keycloakUserId);
    // } else {
    // log.info("[{}] 🔑 Creating temporary Keycloak user with email={}", method,
    // dto.getWorkMail());
    // keycloakUserId = keycloakService.createEmployeeUserInKeycloak(dto);
    // log.info("[{}] ✅ Temporary Keycloak user created, userId={}", method,
    // keycloakUserId);
    // }

    // // --- Populate BasicDetails entity ---
    // BasicDetails entity = new BasicDetails();
    // entity.setOrganization(org);
    // String empNumber = dto.getEmployeeNumber() != null ? dto.getEmployeeNumber()
    // : generateEmployeeNumber();
    // entity.setEmployeeNumber(empNumber);
    // log.info("[{}] Assigned employeeNumber: {}", method, empNumber);

    // // --- Set employeeId from Keycloak userId ---
    // entity.setEmployeeId(keycloakUserId);

    // entity.setFirstName(dto.getFirstName());
    // entity.setMiddleName(dto.getMiddleName());
    // entity.setLastName(dto.getLastName());
    // entity.setGender(dto.getGender());
    // entity.setDateOfJoining(dto.getDateOfJoining());
    // log.info("[{}] Personal details: firstName={}, lastName={}, gender={},
    // DOJ={}",
    // method, dto.getFirstName(), dto.getLastName(), dto.getGender(),
    // dto.getDateOfJoining());

    // // --- Relationships ---
    // if (dto.getDepartmentId() != null) {
    // log.info("[{}] 🔍 Fetching Department ID: {}", method,
    // dto.getDepartmentId());
    // Department dept =
    // departmentRepository.findByDepartmentId(dto.getDepartmentId())
    // .orElseThrow(() -> {
    // log.error("[{}] ❌ Department not found: {}", method, dto.getDepartmentId());
    // return new RuntimeException("Department not found");
    // });
    // entity.setDepartment(dept);
    // log.info("[{}] ✅ Department set: {}", method, dept.getName());
    // }

    // if (dto.getDesignationId() != null) {
    // log.info("[{}] 🔍 Fetching Designation ID: {}", method,
    // dto.getDesignationId());
    // Designation desig =
    // designationRepository.findByDesignationId(dto.getDesignationId())
    // .orElseThrow(() -> {
    // log.error("[{}] ❌ Designation not found: {}", method,
    // dto.getDesignationId());
    // return new RuntimeException("Designation not found");
    // });
    // entity.setDesignation(desig);
    // log.info("[{}] ✅ Designation set: {}", method, desig.getName());
    // }

    // if (dto.getWorkLocationId() != null) {
    // log.info("[{}] 🔍 Fetching WorkLocation ID: {}", method,
    // dto.getWorkLocationId());
    // WorkLocation loc =
    // workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
    // .orElseThrow(() -> {
    // log.error("[{}] ❌ WorkLocation not found: {}", method,
    // dto.getWorkLocationId());
    // return new RuntimeException("Work Location not found");
    // });
    // entity.setWorkLocation(loc);
    // log.info("[{}] ✅ WorkLocation set: {}", method, loc.getWorkLocationName());
    // }

    // // --- Misc fields ---
    // entity.setEmployeeStatus("ACTIVE");
    // entity.setPortalEnabled(dto.getIsPortalEnabled());
    // entity.setEligibleForPf(dto.getEligibleForPf());
    // entity.setEligibleForPt(dto.getEligibleForPt());
    // entity.setEligibleForLwf(dto.getEligibleForLwf());
    // entity.setEligibleForEsi(dto.getEligibleForEsi());
    // entity.setEsiNumber(dto.getEsiNumber());
    // entity.setMobile(dto.getMobile());
    // entity.setWorkMail(dto.getWorkMail());
    // entity.setPfAccountNumber(dto.getPfAccountNumber());
    // entity.setUan(dto.getUan());
    // entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
    // log.info("[{}] Employment details set: status={}, portalEnabled={},
    // workMail={}",
    // method, dto.getEmployeeStatus(), dto.getIsPortalEnabled(),
    // dto.getWorkMail());

    // // --- New fields ---
    // entity.setDirector(dto.getDirector());
    // entity.setEligibleForEps(dto.getEligibleForEps());
    // entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
    // String employeeUniqueId = generateEmployeeUniqueId();
    // entity.setEmployeeUniqueId(employeeUniqueId);
    // entity.setHrUser(dto.getHrUser());
    // log.debug("[{}] Generated employeeUniqueId: {}", method, employeeUniqueId);

    // // --- Save entity ---
    // log.info("[{}] 💾 Saving BasicDetails entity...", method);
    // BasicDetails saved = basicDetailsRepository.save(entity);
    // log.info("[{}] ✅ Saved BasicDetails with ID: {}", method, saved.getId());

    // // --- Portal invitation ---
    // if (Boolean.TRUE.equals(saved.getPortalEnabled())) {
    // log.info("[{}] 📧 Creating EmployeeInvitation for workMail: {}", method,
    // saved.getWorkMail());
    // EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
    // invitationDto.setEmail(saved.getWorkMail());
    // invitationDto.setEmployeeId(saved.getEmployeeId());
    // invitationDto.setIsPortalEnabled(saved.getPortalEnabled());
    // invitationDto.setIsInvitationAccepted(false);

    // employeeInvitationService.createInvitation(
    // saved.getOrganization().getOrganizationId(),
    // invitationDto);
    // log.info("[{}] ✅ EmployeeInvitation created.", method);
    // } else {
    // log.info("[{}] Portal not enabled; skipping invitation.", method);
    // }

    // BasicDetailsDTO response = mapToDto(saved);
    // log.info("[{}] 🏁 Returning response DTO: {}", method, response);
    // return response;
    // }

    // @Override
    // @Transactional
    // public BasicDetailsDTO createBasicDetails(String organizationId,
    // BasicDetailsDTO dto) {
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> new RuntimeException("Organization not found"));
    //
    // BasicDetails entity = new BasicDetails();
    // entity.setOrganization(org);
    // entity.setEmployeeNumber(dto.getEmployeeNumber() != null ?
    // dto.getEmployeeNumber() : generateEmployeeNumber());
    // entity.setFirstName(dto.getFirstName());
    // entity.setMiddleName(dto.getMiddleName());
    // entity.setLastName(dto.getLastName());
    // entity.setGender(dto.getGender());
    // entity.setDateOfJoining(dto.getDateOfJoining());
    //
    // // relationships
    // if (dto.getDepartmentId() != null) {
    // Department dept =
    // departmentRepository.findByDepartmentId(dto.getDepartmentId())
    // .orElseThrow(() -> new RuntimeException("Department not found"));
    // entity.setDepartment(dept);
    // }
    // if (dto.getDesignationId() != null) {
    // Designation desig =
    // designationRepository.findByDesignationId(dto.getDesignationId())
    // .orElseThrow(() -> new RuntimeException("Designation not found"));
    // entity.setDesignation(desig);
    // }
    // if (dto.getWorkLocationId() != null) {
    // WorkLocation loc =
    // workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
    // .orElseThrow(() -> new RuntimeException("Work Location not found"));
    // entity.setWorkLocation(loc);
    // }
    //
    // entity.setEmployeeStatus(dto.getEmployeeStatus());
    // entity.setPortalEnabled(dto.getPortalEnabled());
    // entity.setEligibleForPf(dto.getEligibleForPf());
    // entity.setEligibleForPt(dto.getEligibleForPt());
    // entity.setEligibleForLwf(dto.getEligibleForLwf());
    // entity.setEligibleForEsi(dto.getEligibleForEsi());
    // entity.setMobile(dto.getMobile());
    // entity.setWorkMail(dto.getWorkMail());
    // entity.setPfAccountNumber(dto.getPfAccountNumber());
    // entity.setUan(dto.getUan());
    // entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
    //
    // // new fields
    // entity.setDirector(dto.getDirector());
    // entity.setEligibleForEps(dto.getEligibleForEps());
    // entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
    //
    // String employeeUniqueId = generateEmployeeUniqueId();
    // entity.setEmployeeUniqueId(employeeUniqueId);
    //
    // BasicDetails saved = basicDetailsRepository.save(entity);
    //
    // // Create EmployeeInvitation only if portalEnabled = true
    // if (Boolean.TRUE.equals(saved.getPortalEnabled())) {
    // EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
    // invitationDto.setEmail(saved.getWorkMail());
    // invitationDto.setEmployeeId(saved.getEmployeeUniqueId());
    // invitationDto.setIsPortalEnabled(saved.getPortalEnabled());
    // invitationDto.setIsInvitationAccepted(false);
    //
    // employeeInvitationService.createInvitation(
    // saved.getOrganization().getOrganizationId(),
    // invitationDto
    // );
    // }
    //
    // // ✅ Use mapToDto so response is complete
    // return mapToDto(saved);
    // }

    private String generateEmployeeUniqueId() {
        String uniqueId;
        do {
            java.util.Random random = new java.util.Random();
            long number = 1000000000L + (long) (random.nextDouble() * 9000000000L);
            uniqueId = String.valueOf(number);
        } while (basicDetailsRepository.existsByEmployeeUniqueId(uniqueId));

        return uniqueId;
    }

    // @Override
    // @Transactional
    // public BasicDetailsDTO updateBasicDetails(String organizationId, String
    // basicDetailsId, BasicDetailsDTO dto) {
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> new RuntimeException("Organization not found"));
    //
    // Long id = Long.parseLong(basicDetailsId);
    // BasicDetails entity = basicDetailsRepository.findById(id)
    // .orElseThrow(() -> new RuntimeException("Employee not found"));
    //
    // if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
    // throw new RuntimeException("Employee does not belong to this organization");
    // }
    //
    // entity.setFirstName(dto.getFirstName());
    // entity.setMiddleName(dto.getMiddleName());
    // entity.setLastName(dto.getLastName());
    // entity.setGender(dto.getGender());
    // entity.setDateOfJoining(dto.getDateOfJoining());
    //
    // // relationships
    // if (dto.getDepartmentId() != null) {
    // Department dept =
    // departmentRepository.findByDepartmentId(dto.getDepartmentId())
    // .orElseThrow(() -> new RuntimeException("Department not found"));
    // entity.setDepartment(dept);
    // }
    // if (dto.getDesignationId() != null) {
    // Designation desig =
    // designationRepository.findByDesignationId(dto.getDesignationId())
    // .orElseThrow(() -> new RuntimeException("Designation not found"));
    // entity.setDesignation(desig);
    // }
    // if (dto.getWorkLocationId() != null) {
    // WorkLocation loc =
    // workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
    // .orElseThrow(() -> new RuntimeException("Work Location not found"));
    // entity.setWorkLocation(loc);
    // }
    //
    // entity.setEmployeeStatus(dto.getEmployeeStatus());
    // entity.setPortalEnabled(dto.getPortalEnabled());
    // entity.setEligibleForPf(dto.getEligibleForPf());
    // entity.setEligibleForPt(dto.getEligibleForPt());
    // entity.setEligibleForLwf(dto.getEligibleForLwf());
    // entity.setEligibleForEsi(dto.getEligibleForEsi());
    // entity.setMobile(dto.getMobile());
    // entity.setWorkMail(dto.getWorkMail());
    // entity.setPfAccountNumber(dto.getPfAccountNumber());
    // entity.setUan(dto.getUan());
    // entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
    //
    // // new fields
    // entity.setDirector(dto.getDirector());
    // entity.setEligibleForEps(dto.getEligibleForEps());
    // entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
    //
    // basicDetailsRepository.save(entity);
    // dto.setId(entity.getId());
    // return dto;
    // }

    @Override
    @Transactional
    public BasicDetailsDTO updateBasicDetails(String organizationId, String basicDetailsId, BasicDetailsDTO dto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // Long id = Long.parseLong(basicDetailsId);
        // BasicDetails entity = basicDetailsRepository.findById(id)
        // .orElseThrow(() -> new RuntimeException("Employee not found"));

        BasicDetails entity = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, basicDetailsId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        entity.setFirstName(dto.getFirstName());
        entity.setMiddleName(dto.getMiddleName());
        entity.setLastName(dto.getLastName());
        entity.setGender(dto.getGender());
        entity.setDateOfJoining(dto.getDateOfJoining());
        entity.setEmployeeNumber(dto.getEmployeeNumber());

        // relationships
        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findByDepartmentId(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            entity.setDepartment(dept);
        }
        if (dto.getDesignationId() != null) {
            Designation desig = designationRepository.findByDesignationId(dto.getDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found"));
            entity.setDesignation(desig);
        }
        if (dto.getWorkLocationId() != null) {
            WorkLocation loc = workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
                    .orElseThrow(() -> new RuntimeException("Work Location not found"));
            entity.setWorkLocation(loc);
        }

        // entity.setEmployeeStatus(dto.getEmployeeStatus());
        entity.setPortalEnabled(dto.getPortalEnabled());
        entity.setEligibleForPf(dto.getEligibleForPf());
        entity.setEligibleForPt(dto.getEligibleForPt());
        entity.setEligibleForLwf(dto.getEligibleForLwf());
        entity.setEligibleForEsi(dto.getEligibleForEsi());
        entity.setEsiNumber(dto.getEsiNumber());
        entity.setMobile(dto.getMobile());
        // ✅ FIX: When work mail changes, sync Keycloak email + CompanyUser table
        String oldWorkMail = entity.getWorkMail();
        String newWorkMail = dto.getWorkMail();
        boolean workMailChanged = newWorkMail != null && !newWorkMail.equalsIgnoreCase(oldWorkMail);

        if (workMailChanged) {
            log.info("[updateBasicDetails] 📧 Work mail changed: {} → {}", oldWorkMail, newWorkMail);
            // entity.getEmployeeId() holds the Keycloak user UUID
            String keycloakUserId = entity.getEmployeeId();
            if (keycloakUserId != null && !keycloakUserId.isEmpty()) {
                try {
                    // Update email + username in Keycloak so getUserIdByEmail(newEmail) succeeds
                    keycloakService.updateUserEmail(keycloakUserId, newWorkMail);
                    log.info("[updateBasicDetails] ✅ Keycloak email updated for userId={}", keycloakUserId);
                } catch (Exception ex) {
                    log.error("[updateBasicDetails] ❌ Failed to update Keycloak email: {}", ex.getMessage(), ex);
                    throw new RuntimeException("Failed to update Keycloak user email: " + ex.getMessage(), ex);
                }
            }

            // Sync with company_user table
            // Sync CompanyUser table using Keycloak userId
            try {

                Optional<CompanyUser> companyUserOpt =
                        companyUserRepository.findByUserId(keycloakUserId);

                if (companyUserOpt.isPresent()) {

                    CompanyUser companyUser = companyUserOpt.get();

                    log.info("[updateBasicDetails] CompanyUser found.");
                    log.info("[updateBasicDetails] Old CompanyUser Email : {}", companyUser.getUserEmail());

                    companyUser.setUserEmail(newWorkMail);

                    companyUserRepository.save(companyUser);

                    log.info("[updateBasicDetails] New CompanyUser Email : {}", companyUser.getUserEmail());

                } else {

                    log.warn("[updateBasicDetails] CompanyUser not found for userId={}", keycloakUserId);

                }

            } catch (Exception ex) {

                log.error("[updateBasicDetails] Failed to update CompanyUser: {}", ex.getMessage(), ex);

            }
        }

        entity.setWorkMail(dto.getWorkMail());
        entity.setPfAccountNumber(dto.getPfAccountNumber());
        entity.setUan(dto.getUan());
        entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());

        // new fields
        entity.setDirector(dto.getDirector());
        entity.setEligibleForEps(dto.getEligibleForEps());
        entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
        entity.setHrUser(dto.getHrUser());

        // basicDetailsRepository.save(entity);

        BasicDetails saved = basicDetailsRepository.save(entity);

        // If portal is enabled → create/update invitation
        if (Boolean.TRUE.equals(saved.getPortalEnabled())) {
            EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
            invitationDto.setEmail(saved.getWorkMail());
            invitationDto.setEmployeeId(saved.getEmployeeId());
            invitationDto.setIsPortalEnabled(saved.getPortalEnabled());

            // We need to keep track of if we should send an email
            boolean[] shouldSendEmail = {false};
            String[] tokenForEmail = {null};

            // Look up existing invitation by employeeId or old work email or new work email
            Optional<EmployeeInvitationDTO> existingInvitation = employeeInvitationService.findByEmployeeId(
                    saved.getOrganization().getOrganizationId(),
                    saved.getEmployeeId());
            
            if (existingInvitation.isEmpty()) {
                try {
                    existingInvitation = Optional.of(employeeInvitationService.findByEmailAndOrganization(
                            oldWorkMail, saved.getOrganization().getOrganizationId()));
                } catch (Exception e1) {
                    try {
                        existingInvitation = Optional.of(employeeInvitationService.findByEmailAndOrganization(
                                newWorkMail, saved.getOrganization().getOrganizationId()));
                    } catch (Exception e2) {
                        // Ignore
                    }
                }
            }

            existingInvitation.ifPresentOrElse(existing -> {
                // Do NOT override isInvitationAccepted during update
                invitationDto.setIsInvitationAccepted(existing.getIsInvitationAccepted());

                EmployeeInvitationDTO updated = employeeInvitationService.updateInvitation(
                        saved.getOrganization().getOrganizationId(),
                        existing.getInvitationId(),
                        invitationDto);
                        
                // If it was previously disabled, we consider it a fresh enable, so send an email
                if (Boolean.FALSE.equals(existing.getIsPortalEnabled())) {
                    shouldSendEmail[0] = true;
                    tokenForEmail[0] = updated.getAcceptanceToken();
                }
            }, () -> {
                // Only set false when creating new invitation
                invitationDto.setIsInvitationAccepted(false);

                EmployeeInvitationDTO created = employeeInvitationService.createInvitation(
                        saved.getOrganization().getOrganizationId(),
                        invitationDto);
                        
                shouldSendEmail[0] = true;
                tokenForEmail[0] = created.getAcceptanceToken();
            });
                    
            if (shouldSendEmail[0] && tokenForEmail[0] != null) {
                try {
                    log.info("[{}] 📧 Sending employee invitation email on update to: {}", "updateBasicDetails", dto.getWorkMail());
                    String inviterName = JWTUtil.getCurrentUserName();
                    boolean emailSent = emailService.sendEmployeeInvitationEmail(
                            dto.getWorkMail(),
                            dto.getFirstName() + " " + dto.getLastName(),
                            org.getOrganizationName(),
                            inviterName,
                            "Employee",
                            emailService.calculateExpiryDate(),
                            tokenForEmail[0],
                            organizationId);

                    if (emailSent) {
                        log.info("[{}] ✅ Employee invitation email sent successfully to: {}", "updateBasicDetails", dto.getWorkMail());
                    } else {
                        log.warn("[{}] ⚠️ Failed to send invitation email to: {}", "updateBasicDetails", dto.getWorkMail());
                    }
                } catch (Exception e) {
                    log.error("[{}] ❌ Error sending invitation email on update: {}", "updateBasicDetails", e.getMessage());
                }
            }
        } else {
            // If portal is disabled → update invitation and disable Keycloak user / role mapping
            employeeInvitationService.findByEmployeeId(
                    saved.getOrganization().getOrganizationId(),
                    saved.getEmployeeId()).ifPresent(existing -> {
                        EmployeeInvitationDTO invitationDto = new EmployeeInvitationDTO();
                        invitationDto.setEmail(saved.getWorkMail());
                        invitationDto.setEmployeeId(saved.getEmployeeId());
                        invitationDto.setIsPortalEnabled(false);
                        invitationDto.setIsInvitationAccepted(false);

                        employeeInvitationService.updateInvitation(
                                saved.getOrganization().getOrganizationId(),
                                existing.getInvitationId(),
                                invitationDto);
                    });

            try {
                CompanyUserDTO companyUserDto = new CompanyUserDTO();
                companyUserDto.setOrganizationId(organizationId);
                companyUserDto.setUserEmail(saved.getWorkMail());
                companyUserDto.setIsEmployeePortalEnable(false);
                companyUserService.toggleEmployeePortalAccess(companyUserDto);
            } catch (Exception e) {
                log.error("[updateBasicDetails] Failed to disable keycloak/portal access on update for email {}: {}", saved.getWorkMail(), e.getMessage());
            }
        }

        // dto.setId(entity.getId());
        return dto;
    }

    @Override
    public BasicDetailsDTO getBasicDetails(String organizationId, String basicDetailsId) {
        // Long id = Long.parseLong(basicDetailsId);
        // BasicDetails entity = basicDetailsRepository.findById(id)
        // .orElseThrow(() -> new RuntimeException("Employee not found"));

        BasicDetails entity = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, basicDetailsId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        return mapToDto(entity);
    }

    // @Override
    // public Page<BasicDetailsDTO> getAllBasicDetails(String organizationId,
    // Pageable pageable) {
    // Page<BasicDetails> page =
    // basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse(organizationId,
    // pageable);
    //
    // return page.map(employee -> {
    // BasicDetailsDTO dto = mapToDto(employee);
    //
    // // ✅ Step 1: Basic Details (always true since we're listing BasicDetails)
    // dto.markStepComplete("basicDetails", true);
    //
    // // ✅ Step 2: CTC
    // boolean hasCTC = employee.getSalaryStructure() != null;
    // dto.markStepComplete("ctc", hasCTC);
    //
    // // ✅ Step 3: Personal Detail
    // boolean hasPersonal = employee.getPersonalDetail() != null;
    // dto.markStepComplete("personalDetail", hasPersonal);
    //
    // // ✅ Step 4: Bank Detail
    // boolean hasBank = employee.getBankDetail() != null;
    // dto.markStepComplete("bankDetail", hasBank);
    //
    //
    //
    // return dto;
    // });
    // }

    @Override
    public Page<BasicDetailsDTO> getAllBasicDetails(String organizationId, Pageable pageable,
            String workLocationId, String departmentId, String designationId) {

        // Build base specification: organization
        Specification<BasicDetails> spec = Specification.where(BasicDetailsSpecs.hasOrganization(organizationId));

        // Add optional filters (only if provided)
        if (StringUtils.hasText(workLocationId)) {
            spec = spec.and(BasicDetailsSpecs.hasWorkLocation(workLocationId));
        }
        if (StringUtils.hasText(departmentId)) {
            spec = spec.and(BasicDetailsSpecs.hasDepartment(departmentId));
        }
        if (StringUtils.hasText(designationId)) {
            spec = spec.and(BasicDetailsSpecs.hasDesignation(designationId));
        }

        // If spec is null (shouldn't be because hasOrganization added), fallback to
        // findByOrganization...
        Page<BasicDetails> page = basicDetailsRepository.findAll(spec, pageable);

        // Map to DTO and keep your completion flags logic
        return page.map(employee -> {
            BasicDetailsDTO dto = mapToDto(employee);

            // ✅ Step 1: Basic Details (always true since we're listing BasicDetails)
            dto.markStepComplete("basicDetails", true);

            // ✅ Step 2: CTC
            // ✅ Step 2: CTC (ACTIVE CTC exists)
            boolean hasCTC = !ctcStructureRepository
                    .findByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrue(
                            organizationId,
                            employee.getId())
                    .isEmpty();

            dto.markStepComplete("ctc", hasCTC);

            // ✅ Step 3: Personal Detail
            boolean hasPersonal = employee.getPersonalDetail() != null;
            dto.markStepComplete("personalDetail", hasPersonal);

            // ✅ Step 4: Bank Detail
            boolean hasBank = employee.getBankDetail() != null;
            dto.markStepComplete("bankDetail", hasBank);

            return dto;
        });
    }

    @Override
    public BasicDetailsDTO mapToDto(BasicDetails entity) {
        BasicDetailsDTO dto = new BasicDetailsDTO();
        // dto.setId(entity.getId());

        dto.setId(entity.getEmployeeId());
        dto.setEmployeeNumber(entity.getEmployeeNumber());
        dto.setEmployeeId(entity.getEmployeeId());
        dto.setFirstName(entity.getFirstName());
        dto.setMiddleName(entity.getMiddleName());
        dto.setLastName(entity.getLastName());
        dto.setGender(entity.getGender());
        dto.setDateOfJoining(entity.getDateOfJoining());
        dto.setEmployeeStatus(entity.getEmployeeStatus());
        dto.setIsPortalEnabled(entity.getPortalEnabled());
        dto.setEligibleForPf(entity.getEligibleForPf());
        dto.setEligibleForPt(entity.getEligibleForPt());
        dto.setEligibleForLwf(entity.getEligibleForLwf());
        dto.setEligibleForEsi(entity.getEligibleForEsi());
        dto.setEsiNumber(entity.getEsiNumber());
        dto.setMobile(entity.getMobile());
        dto.setWorkMail(entity.getWorkMail());
        dto.setPfAccountNumber(entity.getPfAccountNumber());
        dto.setUan(entity.getUan());
        dto.setTags(entity.getTags() != null ? entity.getTags() : new ArrayList<>());
        dto.setOrganizationId(entity.getOrganization().getOrganizationId());
        dto.setHrUser(entity.getHrUser());

        // // relationships
        // dto.setDepartmentId(entity.getDepartment() != null ?
        // entity.getDepartment().getDepartmentId() : null);
        // dto.setDesignationId(entity.getDesignation() != null ?
        // entity.getDesignation().getDesignationId() : null);
        // dto.setWorkLocationId(entity.getWorkLocation() != null ?
        // entity.getWorkLocation().getWorkLocationId() : null);

        // relationships: set both Id and Name (use real entity getters)
        if (entity.getDepartment() != null) {
            dto.setDepartmentId(entity.getDepartment().getDepartmentId());
            dto.setDepartmentName(entity.getDepartment().getName()); // Department.name
        } else {
            dto.setDepartmentId(null);
            dto.setDepartmentName(null);
        }

        if (entity.getDesignation() != null) {
            dto.setDesignationId(entity.getDesignation().getDesignationId());
            dto.setDesignationName(entity.getDesignation().getName()); // Designation.name
        } else {
            dto.setDesignationId(null);
            dto.setDesignationName(null);
        }

        if (entity.getWorkLocation() != null) {
            dto.setWorkLocationId(entity.getWorkLocation().getWorkLocationId());
            dto.setWorkLocationName(entity.getWorkLocation().getWorkLocationName()); // WorkLocation.workLocationName
        } else {
            dto.setWorkLocationId(null);
            dto.setWorkLocationName(null);
        }

        // new fields
        dto.setDirector(entity.getDirector());
        dto.setEligibleForEps(entity.getEligibleForEps());
        dto.setCanContributeToEpsOnHigherWages(entity.getCanContributeToEpsOnHigherWages());

        dto.setEmployeeUniqueId(entity.getEmployeeUniqueId());

        dto.setIsDeleted(entity.getIsDeleted());

        if (entity.getPersonalDetail() != null) {
            dto.setFatherName(entity.getPersonalDetail().getFatherName());
        }

        return dto;
    }

    @Override
    @Transactional
    public void deleteBasicDetails(String organizationId, String basicDetailsId) {
        Long id = Long.parseLong(basicDetailsId);
        BasicDetails entity = basicDetailsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!entity.getOrganization().getOrganizationId().equals(organizationId)) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        basicDetailsRepository.delete(entity);
    }

    // @Override
    // @Transactional
    // public void saveAll(String organizationId, List<BasicDetailsDTO>
    // basicDetailsList) {
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> new RuntimeException("Organization not found"));
    //
    // List<BasicDetails> entities = basicDetailsList.stream().map(dto -> {
    // BasicDetails entity = new BasicDetails();
    // entity.setEmployeeNumber(dto.getEmployeeNumber() != null ?
    // dto.getEmployeeNumber() : generateEmployeeNumber());
    // entity.setFirstName(dto.getFirstName());
    // entity.setMiddleName(dto.getMiddleName());
    // entity.setLastName(dto.getLastName());
    // entity.setGender(dto.getGender());
    // entity.setDateOfJoining(dto.getDateOfJoining());
    //
    // // relationships
    // if (dto.getDepartmentId() != null) {
    // Department dept =
    // departmentRepository.findByDepartmentId(dto.getDepartmentId())
    // .orElseThrow(() -> new RuntimeException("Department not found"));
    // entity.setDepartment(dept);
    // }
    // if (dto.getDesignationId() != null) {
    // Designation desig =
    // designationRepository.findByDesignationId(dto.getDesignationId())
    // .orElseThrow(() -> new RuntimeException("Designation not found"));
    // entity.setDesignation(desig);
    // }
    // if (dto.getWorkLocationId() != null) {
    // WorkLocation loc =
    // workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
    // .orElseThrow(() -> new RuntimeException("Work Location not found"));
    // entity.setWorkLocation(loc);
    // }
    //
    // entity.setEmployeeStatus(dto.getEmployeeStatus());
    // entity.setPortalEnabled(dto.getPortalEnabled());
    // entity.setEligibleForPf(dto.getEligibleForPf());
    // entity.setEligibleForPt(dto.getEligibleForPt());
    // entity.setEligibleForLwf(dto.getEligibleForLwf());
    // entity.setEligibleForEsi(dto.getEligibleForEsi());
    // entity.setMobile(dto.getMobile());
    // entity.setWorkMail(dto.getWorkMail());
    // entity.setPfAccountNumber(dto.getPfAccountNumber());
    // entity.setUan(dto.getUan());
    // entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
    // entity.setOrganization(org);
    //
    // // new fields
    // entity.setDirector(dto.getDirector());
    // entity.setEligibleForEps(dto.getEligibleForEps());
    // entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());
    //
    // return entity;
    // }).toList();
    //
    // basicDetailsRepository.saveAll(entities);
    // }

    @Override
    @Transactional
    public void saveAll(String organizationId, List<BasicDetailsDTO> basicDetailsList) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        List<BasicDetails> entities = basicDetailsList.stream().map(dto -> {
            BasicDetails entity = new BasicDetails();
            entity.setEmployeeNumber(
                    dto.getEmployeeNumber() != null ? dto.getEmployeeNumber() : generateEmployeeNumber());

            // Handle employeeId via CompanyUserServiceImpl
            if (dto.getWorkMail() != null) {
                String keycloakUserId;
                try {
                    // Try to get existing user from Keycloak
                    keycloakUserId = keycloakService.getUserIdByEmail(dto.getWorkMail());
                } catch (RuntimeException e) {
                    // If user doesn't exist, create via CompanyUserServiceImpl
                    dto.setOrganizationId(organizationId);
                    keycloakUserId = companyUserService.organizationEmployeeRegistration(dto);
                }

                entity.setEmployeeId(keycloakUserId);
            } else {
                throw new RuntimeException("Work email is required for employee creation");
            }
            entity.setFirstName(dto.getFirstName());
            entity.setMiddleName(dto.getMiddleName());
            entity.setLastName(dto.getLastName());
            entity.setGender(dto.getGender());
            entity.setDateOfJoining(dto.getDateOfJoining());

            entity.setEmployeeStatus(dto.getEmployeeStatus());
            entity.setPortalEnabled(dto.getIsPortalEnabled());
            entity.setEligibleForPf(dto.getEligibleForPf());
            entity.setEligibleForPt(dto.getEligibleForPt());
            entity.setEligibleForLwf(dto.getEligibleForLwf());
            entity.setEligibleForEsi(dto.getEligibleForEsi());
            entity.setMobile(dto.getMobile());
            entity.setWorkMail(dto.getWorkMail());
            entity.setPfAccountNumber(dto.getPfAccountNumber());
            entity.setUan(dto.getUan());
            entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
            entity.setOrganization(org);

            // new fields
            entity.setDirector(dto.getDirector());
            entity.setEligibleForEps(dto.getEligibleForEps());
            entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());

            // ✅ generate employeeUniqueId for each new employee
            String employeeUniqueId = generateEmployeeUniqueId();
            entity.setEmployeeUniqueId(employeeUniqueId);

            return entity;
        }).toList();

        basicDetailsRepository.saveAll(entities);
    }

    // @Override
    // @Transactional
    // public void saveAll(String organizationId, List<BasicDetailsDTO>
    // basicDetailsList) {
    // Organization org =
    // organizationRepository.findByOrganizationId(organizationId)
    // .orElseThrow(() -> new RuntimeException("Organization not found"));

    // List<BasicDetails> entities = basicDetailsList.stream().map(dto -> {
    // BasicDetails entity = new BasicDetails();
    // entity.setEmployeeNumber(
    // dto.getEmployeeNumber() != null ? dto.getEmployeeNumber() :
    // generateEmployeeNumber());

    // // Handle employeeId via Keycloak using workMail
    // if (dto.getWorkMail() != null) {
    // boolean emailExists = keycloakService.isEmailExists(dto.getWorkMail());
    // String keycloakUserId;

    // if (emailExists) {
    // keycloakUserId = keycloakService.getUserIdByEmail(dto.getWorkMail());
    // } else {
    // keycloakUserId = keycloakService.createEmployeeUserInKeycloak(dto);
    // }

    // entity.setEmployeeId(keycloakUserId);
    // } else {
    // throw new RuntimeException("Work email is required for employee creation");
    // }

    // entity.setFirstName(dto.getFirstName());
    // entity.setMiddleName(dto.getMiddleName());
    // entity.setLastName(dto.getLastName());
    // entity.setGender(dto.getGender());
    // entity.setDateOfJoining(dto.getDateOfJoining());

    // // relationships
    // // if (dto.getDepartmentId() != null) {
    // // Department dept =
    // // departmentRepository.findByDepartmentId(dto.getDepartmentId())
    // // .orElseThrow(() -> new RuntimeException("Department not found"));
    // // entity.setDepartment(dept);
    // // }
    // // if (dto.getDesignationId() != null) {
    // // Designation desig =
    // // designationRepository.findByDesignationId(dto.getDesignationId())
    // // .orElseThrow(() -> new RuntimeException("Designation not found"));
    // // entity.setDesignation(desig);
    // // }
    // // if (dto.getWorkLocationId() != null) {
    // // WorkLocation loc =
    // // workLocationRepository.findByWorkLocationId(dto.getWorkLocationId())
    // // .orElseThrow(() -> new RuntimeException("Work Location not found"));
    // // entity.setWorkLocation(loc);
    // // }

    // entity.setEmployeeStatus(dto.getEmployeeStatus());
    // entity.setPortalEnabled(dto.getIsPortalEnabled());
    // entity.setEligibleForPf(dto.getEligibleForPf());
    // entity.setEligibleForPt(dto.getEligibleForPt());
    // entity.setEligibleForLwf(dto.getEligibleForLwf());
    // entity.setEligibleForEsi(dto.getEligibleForEsi());
    // entity.setMobile(dto.getMobile());
    // entity.setWorkMail(dto.getWorkMail());
    // entity.setPfAccountNumber(dto.getPfAccountNumber());
    // entity.setUan(dto.getUan());
    // entity.setTags(dto.getTags() != null ? dto.getTags() : new ArrayList<>());
    // entity.setOrganization(org);

    // // new fields
    // entity.setDirector(dto.getDirector());
    // entity.setEligibleForEps(dto.getEligibleForEps());
    // entity.setCanContributeToEpsOnHigherWages(dto.getCanContributeToEpsOnHigherWages());

    // // ✅ generate employeeUniqueId for each new employee
    // String employeeUniqueId = generateEmployeeUniqueId();
    // entity.setEmployeeUniqueId(employeeUniqueId);

    // return entity;
    // }).toList();

    // basicDetailsRepository.saveAll(entities);
    // }

    @Override
    public EmployeeDetailsDTO getEmployeeByWorkMail(String email) {
        String method = "getEmployeeByWorkMail";
        log.info("[{}] 🔍 Searching employee with work mail: {}", method, email);

        BasicDetails employee = basicDetailsRepository.findByWorkMail(email)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Employee not found with work mail: {}", method, email);
                    return new RuntimeException("Employee not found with mail: " + email);
                });

        log.info("[{}] ℹ️ Found employee entity: {}", method, employee.getEmployeeNumber());

        EmployeePersonalDetail personalDetail = employee.getPersonalDetail();
        EmployeeBankDetail bankDetail = employee.getBankDetail();

        // Build DTO
        EmployeeDetailsDTO dto = new EmployeeDetailsDTO();
        dto.setEmployeeNumber(employee.getEmployeeNumber());
        dto.setFirstName(employee.getFirstName());
        dto.setMiddleName(employee.getMiddleName());
        dto.setLastName(employee.getLastName());
        dto.setGender(employee.getGender());
        dto.setDateOfJoining(employee.getDateOfJoining());
        dto.setMobile(employee.getMobile());
        dto.setDepartment(employee.getDepartment() != null ? employee.getDepartment().getName() : null);
        dto.setDesignation(employee.getDesignation() != null ? employee.getDesignation().getName() : null);
        dto.setWorkLocation(
                employee.getWorkLocation() != null ? employee.getWorkLocation().getWorkLocationName() : null);
        dto.setWorkMail(employee.getWorkMail());

        if (bankDetail != null) {
            dto.setPaymentMode(bankDetail.getPaymentMode());
        }

        if (personalDetail != null) {
            dto.setPersonalMail(personalDetail.getPersonalMail());
            dto.setDateOfBirth(personalDetail.getDateOfBirth());
            dto.setFatherName(personalDetail.getFatherName());
            dto.setPan(personalDetail.getPan());
        }

        if (personalDetail != null && personalDetail.getPresentResidentialAddress() != null) {
            ResidentialAddress addr = personalDetail.getPresentResidentialAddress();
            ResidentialAddressDTO addressDTO = new ResidentialAddressDTO();
            addressDTO.setAddressLine1(addr.getAddressLine1());
            addressDTO.setAddressLine2(addr.getAddressLine2());
            addressDTO.setCity(addr.getCity());
            addressDTO.setState(addr.getState());
            addressDTO.setStateCode(addr.getStateCode());
            addressDTO.setZipCode(addr.getZipCode());
            dto.setAddress(addressDTO);
        }

        log.info("[{}] ✅ Successfully built EmployeeDetailsDTO for email: {}", method, email);
        return dto;
    }

    @Override
    @Transactional
    public void importStatutory(String organizationId, List<BasicDetailsDTO> dtos) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        for (BasicDetailsDTO dto : dtos) {
            BasicDetails emp = basicDetailsRepository
                    .findByEmployeeNumberAndOrganization(dto.getEmployeeNumber(), org)
                    .orElseThrow(() -> new RuntimeException(
                            "Employee not found with number: " + dto.getEmployeeNumber()));

            // ✅ update only statutory fields
            emp.setEligibleForPf(dto.getEligibleForPf());
            emp.setEligibleForEps(dto.getEligibleForEps());
            emp.setPfAccountNumber(dto.getPfAccountNumber());
            emp.setUan(dto.getUan());
            emp.setEligibleForEsi(dto.getEligibleForEsi());
            emp.setEligibleForPt(dto.getEligibleForPt());
            emp.setEligibleForLwf(dto.getEligibleForLwf());

            basicDetailsRepository.save(emp);
        }
    }

}
