package com.itsdev.payroll.serviceimpl.employee;

import com.itsdev.payroll.dto.employee.SalaryDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.GridDeductionRequestDTO;
import com.itsdev.payroll.dto.employee.SalaryDeductionResponseDTO;
import com.itsdev.payroll.entity.SalaryDeduction;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.enumeration.DeductionStatus;
import com.itsdev.payroll.mapper.employee.SalaryDeductionMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.SalaryDeductionRepository;
import com.itsdev.payroll.service.employee.SalaryDeductionService;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class SalaryDeductionServiceImpl implements SalaryDeductionService {

    private final SalaryDeductionRepository salaryDeductionRepository;
    private final BasicDetailsRepository basicDetailsRepository;

    public SalaryDeductionServiceImpl(
            SalaryDeductionRepository salaryDeductionRepository,
            BasicDetailsRepository basicDetailsRepository
    ) {
        this.salaryDeductionRepository = salaryDeductionRepository;
        this.basicDetailsRepository = basicDetailsRepository;
    }

    @Override
    public Map<String, Object> createGridDeduction(
            String organizationId,
            GridDeductionRequestDTO request,
            String currentUser
    ) {
        List<SalaryDeductionResponseDTO> successList = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();

        YearMonth currentMonth = YearMonth.now();
        int rowIndex = 0;

        for (GridDeductionRequestDTO.GridDeductionEntry entry : request.getEntries()) {
            rowIndex++;
            try {
                // Amount validation
                if (entry.getDeductionAmount() == null || entry.getDeductionAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Deduction amount must be greater than zero");
                }

                // Month validation
                YearMonth inputMonth;
                try {
                    inputMonth = YearMonth.parse(entry.getDeductionMonth());
                } catch (Exception e) {
                    throw new RuntimeException("Invalid deduction month format. Expected YYYY-MM");
                }
                if (inputMonth.isBefore(currentMonth)) {
                    throw new RuntimeException("Deduction month cannot be in the past");
                }

                // Deduction type validation
                if (entry.getDeductionType() == null || entry.getDeductionType().trim().isEmpty()) {
                    throw new RuntimeException("Deduction type is required");
                }

                // Employee validation
                BasicDetails employee = basicDetailsRepository.findByEmployeeId(entry.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("Employee not found: " + entry.getEmployeeId()));

                if (employee.getOrganization() == null || !organizationId.equals(employee.getOrganization().getOrganizationId())) {
                    throw new RuntimeException("Employee does not belong to this organization");
                }

                if (Boolean.TRUE.equals(employee.getIsDeleted())) {
                    throw new RuntimeException("Employee is inactive or deleted");
                }

                // Build and save
                SalaryDeduction deduction = new SalaryDeduction();
                deduction.setEmployeeId(entry.getEmployeeId());
                deduction.setOrganizationId(organizationId);
                deduction.setDeductionAmount(entry.getDeductionAmount());
                deduction.setDeductionMonth(inputMonth.atDay(1));
                deduction.setDeductionType(entry.getDeductionType());
                deduction.setReason(entry.getReason() != null ? entry.getReason() : entry.getDeductionType());
                deduction.setRemarks(entry.getRemarks());
                deduction.setProofUrl(entry.getProofUrl());
                deduction.setProofPublicId(entry.getProofPublicId());
                deduction.setCreatedBy(currentUser);
                deduction.setStatus(DeductionStatus.ACTIVE);

                SalaryDeduction saved = salaryDeductionRepository.save(deduction);
                saved.setEmployee(employee);
                successList.add(SalaryDeductionMapper.toResponseDTO(saved));

            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("row", rowIndex);
                failure.put("employeeId", entry.getEmployeeId());
                failure.put("error", e.getMessage());
                failedList.add(failure);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successList.size());
        result.put("failedCount", failedList.size());
        result.put("created", successList);
        result.put("failed", failedList);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SalaryDeductionResponseDTO> getAllDeductions(
            String organizationId,
            String search,
            String month,
            String status,
            Pageable pageable
    ) {
        Specification<SalaryDeduction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by organization
            predicates.add(cb.equal(root.get("organizationId"), organizationId));

            // Filter by month (expected format: YYYY-MM)
            if (month != null && !month.trim().isEmpty()) {
                try {
                    LocalDate dateVal = LocalDate.parse(month.trim() + "-01");
                    predicates.add(cb.equal(root.get("deductionMonth"), dateVal));
                } catch (Exception e) {
                    // Ignore search filter for invalid format
                }
            }

            // Filter by status (ACTIVE / PROCESSED)
            if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status)) {
                try {
                    DeductionStatus statusEnum = DeductionStatus.valueOf(status.trim().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (Exception e) {
                    // Ignore search filter for invalid status
                }
            }

            // Search by employeeId (resolved from name/number via a separate lookup)
            // to AVOID a cross-table JOIN between employee_deduction and employee,
            // which fails with "Illegal mix of collations" when the two tables use
            // different collations. We resolve matching employee IDs first, then filter
            // employee_deduction.employee_id (same-column comparison, no collation clash).
            if (search != null && !search.trim().isEmpty()) {
                List<String> matchingEmployeeIds = basicDetailsRepository
                        .searchEmployeeIdsByOrganization(organizationId, search.trim().toLowerCase());

                if (matchingEmployeeIds == null || matchingEmployeeIds.isEmpty()) {
                    // No employee matches → force empty result
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("employeeId").in(matchingEmployeeIds));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SalaryDeduction> page = salaryDeductionRepository.findAll(spec, pageable);
        return page.map(SalaryDeductionMapper::toResponseDTO);
    }

    @Override
    public void validateEmployeeBelongsToOrg(String employeeId, String organizationId) {
        BasicDetails employee = basicDetailsRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee does not exist"));

        if (employee.getOrganization() == null || !organizationId.equals(employee.getOrganization().getOrganizationId())) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        if (Boolean.TRUE.equals(employee.getIsDeleted())) {
            throw new RuntimeException("Employee is inactive or deleted");
        }
    }

    @Override
    public SalaryDeductionResponseDTO updateDeduction(
            String organizationId,
            Long id,
            SalaryDeductionRequestDTO request,
            String currentUser
    ) {
        SalaryDeduction deduction = salaryDeductionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Deduction not found"));

        if (!organizationId.equals(deduction.getOrganizationId())) {
            throw new RuntimeException("Deduction not found");
        }

        if (deduction.getStatus() != DeductionStatus.ACTIVE) {
            throw new RuntimeException("Cannot update deduction. It is already in payrun or processed.");
        }

        YearMonth inputMonth;
        try {
            inputMonth = YearMonth.parse(request.getDeductionMonth());
        } catch (Exception e) {
            throw new RuntimeException("Invalid deduction month format. Expected YYYY-MM");
        }

        YearMonth currentMonth = YearMonth.now();
        if (inputMonth.isBefore(currentMonth)) {
            throw new RuntimeException("Deduction month cannot be in the past");
        }

        BasicDetails employee = basicDetailsRepository.findByEmployeeId(request.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee does not exist"));

        if (employee.getOrganization() == null || !organizationId.equals(employee.getOrganization().getOrganizationId())) {
            throw new RuntimeException("Employee does not belong to this organization");
        }

        if (Boolean.TRUE.equals(employee.getIsDeleted())) {
            throw new RuntimeException("Employee is inactive or deleted");
        }

        deduction.setEmployeeId(request.getEmployeeId());
        deduction.setDeductionAmount(request.getDeductionAmount());
        deduction.setDeductionMonth(inputMonth.atDay(1));
        deduction.setDeductionType(request.getDeductionType());
        deduction.setReason(request.getReason());
        deduction.setRemarks(request.getRemarks());
        deduction.setProofUrl(request.getProofUrl());
        deduction.setProofPublicId(request.getProofPublicId());

        SalaryDeduction saved = salaryDeductionRepository.save(deduction);

        // Fetch employee details to map complete responseDTO
        saved.setEmployee(employee);

        return SalaryDeductionMapper.toResponseDTO(saved);
    }

    @Override
    public void deleteDeduction(String organizationId, Long id) {
        SalaryDeduction deduction = salaryDeductionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Deduction not found"));

        if (!organizationId.equals(deduction.getOrganizationId())) {
            throw new RuntimeException("Deduction not found");
        }

        if (deduction.getStatus() != DeductionStatus.ACTIVE) {
            throw new RuntimeException("Cannot delete deduction. It is already in payrun or processed.");
        }

        salaryDeductionRepository.delete(deduction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalaryDeductionResponseDTO> getMyDeductions(
            String organizationId,
            String employeeId,
            String month
    ) {
        List<SalaryDeduction> deductions = salaryDeductionRepository
                .findByOrganizationIdAndEmployeeIdOrderByDeductionMonthDesc(organizationId, employeeId);

        if (month != null && !month.trim().isEmpty() && !"all".equalsIgnoreCase(month)) {
            try {
                LocalDate targetMonth = LocalDate.parse(month.trim() + "-01");
                deductions = deductions.stream()
                        .filter(d -> d.getDeductionMonth() != null && d.getDeductionMonth().equals(targetMonth))
                        .collect(java.util.stream.Collectors.toList());
            } catch (Exception ignored) {
            }
        }

        BasicDetails employee = basicDetailsRepository.findByEmployeeId(employeeId).orElse(null);

        return deductions.stream().map(d -> {
            SalaryDeductionResponseDTO dto = SalaryDeductionMapper.toResponseDTO(d);
            if (employee != null) {
                dto.setEmployeeNumber(employee.getEmployeeNumber());
                String fullName = ((employee.getFirstName() != null ? employee.getFirstName() : "") + " " +
                        (employee.getLastName() != null ? employee.getLastName() : "")).trim();
                dto.setEmployeeName(fullName);
            }
            return dto;
        }).collect(java.util.stream.Collectors.toList());
    }
}
