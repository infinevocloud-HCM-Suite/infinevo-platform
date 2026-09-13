package com.itsdev.payroll.serviceimpl.employee;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.controller.employee.EmployyePortalContoller;
import com.itsdev.payroll.dto.employee.CustomFieldDTO;
import com.itsdev.payroll.dto.employee.EmployeePersonalDetailDTO;
import com.itsdev.payroll.dto.employee.ResidentialAddressDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.EmployeePersonalDetail;
import com.itsdev.payroll.entity.employee.ResidentialAddress;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.EmployeePersonalDetailRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.employee.EmployeePersonalDetailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeePersonalDetailServiceImpl implements EmployeePersonalDetailService {

    private final EmployeePersonalDetailRepository repository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
	 private static final Logger log = LoggerFactory.getLogger(EmployeePersonalDetailServiceImpl.class);

    public EmployeePersonalDetailServiceImpl(EmployeePersonalDetailRepository repository,
                                             OrganizationRepository organizationRepository,
                                             BasicDetailsRepository basicDetailsRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
    }

    // ---------------- CREATE ----------------
    @Override
    public EmployeePersonalDetailDTO create(String organizationId, EmployeePersonalDetailDTO dto) {
        // validate org
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // employee must be provided in DTO
        if (dto.getEmployeeId() == null) {
            throw new IllegalArgumentException("employeeId must be provided in DTO");
        }

     //   BasicDetails employee = basicDetailsRepository.findById(dto.getEmployeeId())
     //           .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + dto.getEmployeeId()));
        
        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        
        // ✅ check for duplicate PAN before save
        if (dto.getPan() != null && !dto.getPan().isBlank()) {
            Optional<EmployeePersonalDetail> existingPan =
                    repository.findByPanAndOrganization_OrganizationId(dto.getPan(), organizationId);

            if (existingPan.isPresent() && !existingPan.get().getEmployee().getId().equals(employee.getId())) {
                throw new RuntimeException("PAN already exists for another employee");
            }
        }

        // If personal detail already exists for this employee+org -> update it (upsert behaviour)
        Optional<EmployeePersonalDetail> existingOpt =
                repository.findByEmployee_IdAndOrganization_OrganizationId(employee.getId(), organizationId);

        EmployeePersonalDetail entity = existingOpt.orElse(new EmployeePersonalDetail());

        // map fields
        entity.setPersonalMail(dto.getPersonalMail());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setFatherName(dto.getFatherName());
        entity.setPan(dto.getPan());
        entity.setDifferentlyAbledType(dto.getDifferentlyAbledType());
        entity.setIsEligibleForFullIncomeTaxExemption(dto.getIsEligibleForFullIncomeTaxExemption());

        if (dto.getPresentResidentialAddress() != null) {
            entity.setPresentResidentialAddress(mapToEntity(dto.getPresentResidentialAddress()));
        }

        if (dto.getCustomFields() != null) {
            try {
                entity.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing custom fields", e);
            }
        }

        // link organization & employee
        entity.setOrganization(org);
        entity.setEmployee(employee);

        EmployeePersonalDetail saved = repository.save(entity);
        return mapToDto(saved);
    }

    // ---------------- UPDATE ----------------
//    @Override
//    public EmployeePersonalDetailDTO update(String organizationId, Long employeeId, EmployeePersonalDetailDTO dto) {
//        EmployeePersonalDetail entity = repository.findByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId)
//                .orElseThrow(() -> new RuntimeException("Employee personal detail not found for employee: " + employeeId));
//
//        // Update fields from DTO (only if non-null; adapt as you prefer)
//        entity.setPersonalMail(dto.getPersonalMail());
//        entity.setDateOfBirth(dto.getDateOfBirth());
//        entity.setFatherName(dto.getFatherName());
//        entity.setPan(dto.getPan());
//        entity.setDifferentlyAbledType(dto.getDifferentlyAbledType());
//        entity.setIsEligibleForFullIncomeTaxExemption(dto.getIsEligibleForFullIncomeTaxExemption());
//
//        if (dto.getPresentResidentialAddress() != null) {
//            entity.setPresentResidentialAddress(mapToEntity(dto.getPresentResidentialAddress()));
//        }
//
//        if (dto.getCustomFields() != null) {
//            try {
//                entity.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException("Error serializing custom fields", e);
//            }
//        }
//
//        EmployeePersonalDetail saved = repository.save(entity);
//        return mapToDto(saved);
//    }

    @Override
    public EmployeePersonalDetailDTO update(String organizationId, String employeeId, EmployeePersonalDetailDTO dto) {
     //   EmployeePersonalDetail entity = repository
      //          .findByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId)
       //         .orElseThrow(() -> new RuntimeException("Employee personal detail not found"));
        
        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        EmployeePersonalDetail entity = employee.getPersonalDetail();

        // update fields
        entity.setPersonalMail(dto.getPersonalMail());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setFatherName(dto.getFatherName());
        entity.setPan(dto.getPan());
        entity.setDifferentlyAbledType(dto.getDifferentlyAbledType());
        entity.setIsEligibleForFullIncomeTaxExemption(dto.getIsEligibleForFullIncomeTaxExemption());

        if (dto.getPresentResidentialAddress() != null) {
            entity.setPresentResidentialAddress(mapToEntity(dto.getPresentResidentialAddress()));
        }

        if (dto.getCustomFields() != null) {
            try {
                entity.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (Exception e) {
                throw new RuntimeException("Error serializing custom fields", e);
            }
        }

        EmployeePersonalDetail updated = repository.save(entity);
        return mapToDto(updated);
    }

    // ---------------- GET BY EMPLOYEE ID ----------------
//    @Override
//    public EmployeePersonalDetailDTO getByEmployeeId(String organizationId, Long employeeId) {
//        EmployeePersonalDetail entity = repository.findByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId)
//                .orElseThrow(() -> new RuntimeException("Employee personal detail not found for employee: " + employeeId));
//        return mapToDto(entity);
//    }

    @Override
    public EmployeePersonalDetailDTO getByEmployeeId(String organizationId, String employeeId) {
    	
        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
              
        return mapToDto(employee.getPersonalDetail());
    }

    // ---------------- LIST ----------------
    @Override
    public List<EmployeePersonalDetailDTO> getAll(String organizationId) {
        return repository.findAllByOrganization_OrganizationId(organizationId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ---------------- DELETE BY EMPLOYEE ID ----------------
    @Override
    public void deleteByEmployeeId(String organizationId, Long employeeId) {
        repository.deleteByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId);
    }

    // ======================================================
    // ============== MAPPING HELPERS =======================
    // ======================================================

    private EmployeePersonalDetailDTO mapToDto(EmployeePersonalDetail entity) {
        EmployeePersonalDetailDTO dto = new EmployeePersonalDetailDTO();
        dto.setId(entity.getId() != null ? entity.getId().toString() : null);

        // Link employee id
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getEmployeeId());
        }

        dto.setPersonalMail(entity.getPersonalMail());
        dto.setDateOfBirth(entity.getDateOfBirth());
        dto.setFatherName(entity.getFatherName());
        dto.setPan(entity.getPan());
        dto.setDifferentlyAbledType(entity.getDifferentlyAbledType());
        dto.setIsEligibleForFullIncomeTaxExemption(entity.getIsEligibleForFullIncomeTaxExemption());

        // organizationId
        if (entity.getOrganization() != null) {
            dto.setOrganizationId(entity.getOrganization().getOrganizationId());
        }

        // Address
        if (entity.getPresentResidentialAddress() != null) {
            dto.setPresentResidentialAddress(mapToDto(entity.getPresentResidentialAddress()));
        }

        // Custom Fields
        if (entity.getCustomFields() != null) {
            try {
                List<CustomFieldDTO> fields = objectMapper.readValue(
                        entity.getCustomFields(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, CustomFieldDTO.class)
                );
                dto.setCustomFields(fields);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing custom fields", e);
            }
        }

        return dto;
    }

    private EmployeePersonalDetail mapToEntity(EmployeePersonalDetailDTO dto) {
        EmployeePersonalDetail entity = new EmployeePersonalDetail();

        if (dto.getId() != null) {
            entity.setId(Long.valueOf(dto.getId()));
        }

        entity.setPersonalMail(dto.getPersonalMail());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setFatherName(dto.getFatherName());
        entity.setPan(dto.getPan());
        entity.setDifferentlyAbledType(dto.getDifferentlyAbledType());
        entity.setIsEligibleForFullIncomeTaxExemption(dto.getIsEligibleForFullIncomeTaxExemption());

        if (dto.getPresentResidentialAddress() != null) {
            entity.setPresentResidentialAddress(mapToEntity(dto.getPresentResidentialAddress()));
        }

        if (dto.getCustomFields() != null) {
            try {
                entity.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing custom fields", e);
            }
        }

        return entity;
    }

    // Address DTO ↔ Entity
    private ResidentialAddressDTO mapToDto(ResidentialAddress entity) {
        ResidentialAddressDTO dto = new ResidentialAddressDTO();
        dto.setAddressLine1(entity.getAddressLine1());
        dto.setAddressLine2(entity.getAddressLine2());
        dto.setCity(entity.getCity());
        dto.setState(entity.getState());
        dto.setStateCode(entity.getStateCode());
        dto.setZipCode(entity.getZipCode());
        return dto;
    }

    private ResidentialAddress mapToEntity(ResidentialAddressDTO dto) {
        ResidentialAddress entity = new ResidentialAddress();
        entity.setAddressLine1(dto.getAddressLine1());
        entity.setAddressLine2(dto.getAddressLine2());
        entity.setCity(dto.getCity());
        entity.setState(dto.getState());
        entity.setStateCode(dto.getStateCode());
        entity.setZipCode(dto.getZipCode());
        return entity;
    }
    
    
    @Override
    public List<EmployeePersonalDetailDTO> createBulk(String organizationId, List<EmployeePersonalDetailDTO> dtoList) {
        String method = "createBulk";
        log.info("[{}] 📥 Incoming bulk create request | orgId={} | totalRecords={}", 
                method, organizationId, dtoList != null ? dtoList.size() : 0);

        // Validate organization
        log.info("[{}] 🔍 Validating organization...", method);
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        log.info("[{}] ✅ Organization validated: {}", method, org.getOrganizationName());

        List<EmployeePersonalDetailDTO> result = new ArrayList<>();

        if (dtoList == null || dtoList.isEmpty()) {
            log.warn("[{}] ⚠️ No records provided in request body | orgId={}", method, organizationId);
            return result;
        }

        int successCount = 0;
        int failureCount = 0;

        for (EmployeePersonalDetailDTO dto : dtoList) {
            try {
                log.info("[{}] ➕ Processing record for employeeId={}", method, dto.getEmployeeId());

                if (dto.getEmployeeId() == null) {
                    log.warn("[{}] ⚠️ Skipping record - employeeId is missing", method);
                    continue;
                }

                BasicDetails employee = basicDetailsRepository
                        .findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("Employee not found for ID: " + dto.getEmployeeId()));

                log.info("[{}] 👤 Employee found: {} {}", method, employee.getFirstName(), employee.getLastName());

                // Check if record exists (upsert behavior)
                Optional<EmployeePersonalDetail> existingOpt =
                        repository.findByEmployee_IdAndOrganization_OrganizationId(employee.getId(), organizationId);

                EmployeePersonalDetail entity = existingOpt.orElse(new EmployeePersonalDetail());
                boolean isUpdate = existingOpt.isPresent();

                // Map fields
                entity.setPersonalMail(dto.getPersonalMail());
                entity.setDateOfBirth(dto.getDateOfBirth());
                entity.setFatherName(dto.getFatherName());
                entity.setPan(dto.getPan());
                entity.setDifferentlyAbledType(dto.getDifferentlyAbledType());
                entity.setIsEligibleForFullIncomeTaxExemption(dto.getIsEligibleForFullIncomeTaxExemption());

                if (dto.getPresentResidentialAddress() != null) {
                    entity.setPresentResidentialAddress(mapToEntity(dto.getPresentResidentialAddress()));
                }

                if (dto.getCustomFields() != null) {
                    try {
                        entity.setCustomFields(objectMapper.writeValueAsString(dto.getCustomFields()));
                    } catch (JsonProcessingException e) {
                        log.error("[{}] ❌ Error serializing custom fields for employeeId={} | error={}", 
                                method, dto.getEmployeeId(), e.getMessage());
                        throw new RuntimeException("Error serializing custom fields", e);
                    }
                }

                entity.setOrganization(org);
                entity.setEmployee(employee);

                EmployeePersonalDetail saved = repository.save(entity);
                result.add(mapToDto(saved));

                successCount++;
                log.info("[{}] ✅ {} record for employeeId={} successfully {}",
                        method, organizationId, dto.getEmployeeId(), (isUpdate ? "updated" : "created"));

            } catch (Exception ex) {
                failureCount++;
                log.error("[{}] ❌ Failed to process employeeId={} | error={}",
                        method, dto.getEmployeeId(), ex.getMessage(), ex);
            }
        }

        log.info("[{}] 🏁 Bulk create completed | orgId={} | total={} | success={} | failed={}",
                method, organizationId, dtoList.size(), successCount, failureCount);

        return result;
    }


}
