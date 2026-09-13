package com.itsdev.payroll.serviceimpl.employee;

import com.itsdev.payroll.controller.employee.BasicDetailsController;
import com.itsdev.payroll.dto.employee.EmployeeBankDetailDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.EmployeeBankDetail;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.EmployeeBankDetailRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.employee.EmployeeBankDetailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class EmployeeBankDetailServiceImpl implements EmployeeBankDetailService {

    private final EmployeeBankDetailRepository repository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    
    private static final Logger log = LoggerFactory.getLogger(EmployeeBankDetailServiceImpl.class);

    public EmployeeBankDetailServiceImpl(EmployeeBankDetailRepository repository,
                                         OrganizationRepository organizationRepository,
                                         BasicDetailsRepository basicDetailsRepository) {
        this.repository = repository;
        this.organizationRepository = organizationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
    }

    // ---------------- CREATE ----------------
    @Override
    public EmployeeBankDetailDTO create(String organizationId, EmployeeBankDetailDTO dto) {
        EmployeeBankDetail entity = mapToEntity(dto);

        // Set Organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        entity.setOrganization(org);

        // Set Employee
      //  BasicDetails employee = basicDetailsRepository.findById(dto.getEmployeeId())
       //         .orElseThrow(() -> new RuntimeException("Employee not found with id: " + dto.getEmployeeId()));
        
        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        
        entity.setEmployee(employee);

        EmployeeBankDetail saved = repository.save(entity);
        return mapToDto(saved);
    }

    // ---------------- UPDATE ----------------
    @Override
    public EmployeeBankDetailDTO update(String organizationId, String employeeId, EmployeeBankDetailDTO dto) {
    //    EmployeeBankDetail entity = repository.findByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId)
     //           .orElseThrow(() -> new RuntimeException("Bank details not found for employeeId: " + employeeId));
        
        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        EmployeeBankDetail entity = employee.getBankDetail();
        entity.setPaymentMode(dto.getPaymentMode());
        entity.setAccountHolderName(dto.getAccountHolderName());
        entity.setBankName(dto.getBankName());
        entity.setIfscCode(dto.getIfscCode());
        entity.setBankAccountNumber(dto.getBankAccountNumber());
        entity.setBankAccountType(dto.getBankAccountType());

        EmployeeBankDetail updated = repository.save(entity);
        return mapToDto(updated);
    }

    // ---------------- GET BY EMPLOYEE ID ----------------
    @Override
    public EmployeeBankDetailDTO getByEmployeeId(String organizationId, String employeeId) {
      //  EmployeeBankDetail entity = repository.findByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId)
       //         .orElseThrow(() -> new RuntimeException("Bank details not found for employeeId: " + employeeId));
        
        
        BasicDetails employee = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        return mapToDto(employee.getBankDetail());
    }

    // ---------------- LIST ALL ----------------
    @Override
    public List<EmployeeBankDetailDTO> getAll(String organizationId) {
        return repository.findAllByOrganization_OrganizationId(organizationId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ---------------- DELETE ----------------
    @Override
    public void deleteByEmployeeId(String organizationId, Long employeeId) {
        repository.deleteByEmployee_IdAndOrganization_OrganizationId(employeeId, organizationId);
    }

    // ---------------- MAPPERS ----------------
    private EmployeeBankDetailDTO mapToDto(EmployeeBankDetail entity) {
        EmployeeBankDetailDTO dto = new EmployeeBankDetailDTO();
        dto.setId(entity.getId() != null ? entity.getId().toString() : null);
        dto.setEmployeeId(entity.getEmployee().getEmployeeId());
        dto.setPaymentMode(entity.getPaymentMode());
        dto.setAccountHolderName(entity.getAccountHolderName());
        dto.setBankName(entity.getBankName());
        dto.setIfscCode(entity.getIfscCode());
        dto.setBankAccountNumber(entity.getBankAccountNumber());
        dto.setBankAccountType(entity.getBankAccountType());
        dto.setOrganizationId(entity.getOrganization().getOrganizationId());
        return dto;
    }

    private EmployeeBankDetail mapToEntity(EmployeeBankDetailDTO dto) {
        EmployeeBankDetail entity = new EmployeeBankDetail();
        if (dto.getId() != null) {
            entity.setId(Long.valueOf(dto.getId()));
        }
        entity.setPaymentMode(dto.getPaymentMode());
        entity.setAccountHolderName(dto.getAccountHolderName());
        entity.setBankName(dto.getBankName());
        entity.setIfscCode(dto.getIfscCode());
        entity.setBankAccountNumber(dto.getBankAccountNumber());
        entity.setBankAccountType(dto.getBankAccountType());
        return entity;
    }
    
    
    @Override
    public List<EmployeeBankDetailDTO> createBulk(String organizationId, List<EmployeeBankDetailDTO> dtoList) {
        String method = "createBulkBankDetails";
        log.info("[{}] 📥 Incoming bulk create request | orgId={} | totalRecords={}",
                method, organizationId, dtoList != null ? dtoList.size() : 0);

        if (dtoList == null || dtoList.isEmpty()) {
            log.warn("[{}] ⚠️ No bank detail records provided | orgId={}", method, organizationId);
            return Collections.emptyList();
        }

        // Validate organization
        log.info("[{}] 🔍 Validating organization existence...", method);
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        log.info("[{}] ✅ Organization validated: {}", method, org.getOrganizationName());

        List<EmployeeBankDetailDTO> result = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (EmployeeBankDetailDTO dto : dtoList) {
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

                // Check for existing record (upsert behavior)
                Optional<EmployeeBankDetail> existingOpt =
                        repository.findByEmployee_IdAndOrganization_OrganizationId(employee.getId(), organizationId);

                EmployeeBankDetail entity = existingOpt.orElse(new EmployeeBankDetail());

                // ✅ Use your mapper to populate fields
                EmployeeBankDetail mapped = mapToEntity(dto);

                // Set relationships
                mapped.setOrganization(org);
                mapped.setEmployee(employee);

                EmployeeBankDetail saved = repository.save(mapped);
                result.add(mapToDto(saved));

                successCount++;
                log.info("[{}] ✅ Bank detail successfully {} for employeeId={}",
                        method, (existingOpt.isPresent() ? "updated" : "created"), dto.getEmployeeId());

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

