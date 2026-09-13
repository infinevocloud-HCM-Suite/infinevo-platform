package com.itsdev.payroll.serviceimpl.payRun.offCyclePayrun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.payRun.offcyclepayrun.*;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payRun.offCyclePayrun.*;
import com.itsdev.payroll.entity.salarycomponents.Deduction;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.mapper.payRun.offCyclePayrun.OffCyclePayRunMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.payRun.offCyclePayrun.OffCyclePayRunRepository;
import com.itsdev.payroll.repository.salarycomponents.DeductionRepository;
import com.itsdev.payroll.repository.salarycomponents.EarningRepository;
import com.itsdev.payroll.service.payRun.offCyclePayrun.OffCyclePayRunService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OffCyclePayRunServiceImpl implements OffCyclePayRunService {

    private final OffCyclePayRunRepository payRunRepository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final EarningRepository earningRepository;
    private final DeductionRepository deductionRepository;
    private final CtcStructureRepository ctcStructureRepository;

    public OffCyclePayRunServiceImpl(OffCyclePayRunRepository payRunRepository,
                                     OrganizationRepository organizationRepository,
                                     BasicDetailsRepository basicDetailsRepository,
                                     EarningRepository earningRepository,
                                     DeductionRepository deductionRepository,
                                     CtcStructureRepository ctcStructureRepository) {
        this.payRunRepository = payRunRepository;
        this.organizationRepository = organizationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.earningRepository = earningRepository;
        this.deductionRepository = deductionRepository;
        this.ctcStructureRepository = ctcStructureRepository;
    }

    @Override
    @Transactional
    public OffCyclePayRunDTO createPayRun(String organizationId, OffCyclePayRunDTO requestDto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OffCyclePayRun payRun = OffCyclePayRunMapper.toEntity(requestDto);
        payRun.setOrganization(org);

        if (payRun.getPayrollRunId() == null || payRun.getPayrollRunId().isBlank()) {
            payRun.setPayrollRunId(UUID.randomUUID().toString());
        }
        if (payRun.getStatus() == null) payRun.setStatus("draft");
        if (payRun.getType() == null) payRun.setType("off_cycle");
        if (payRun.getCreatedAt() == null) payRun.setCreatedAt(LocalDateTime.now());

        // Handle employees
        if (requestDto.getEmployees() != null && !requestDto.getEmployees().isEmpty()) {
            for (OffCyclePayrunEmployeeDTO empDto : requestDto.getEmployees()) {
                BasicDetails employee = basicDetailsRepository.findById(empDto.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("Employee not found: " + empDto.getEmployeeId()));

                // Fetch employee’s CTC structure for calculation
                CtcStructure ctc = ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_Id(organizationId, employee.getId())
                        .stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("CTC structure not found for employee: " + employee.getId()));

                List<OffCyclePayrunEmployeeEarning> managedEarnings = (empDto.getEarnings() == null)
                        ? List.of()
                        : empDto.getEarnings().stream()
                        .map(eDto -> {
                            Earning dbEarning = earningRepository.findByEarningId(eDto.getEarningId())
                                    .orElseThrow(() -> new RuntimeException("Earning not found: " + eDto.getEarningId()));
                            OffCyclePayrunEmployeeEarning earningEntity =
                                    OffCyclePayRunMapper.toEntity(eDto, null, dbEarning);

                            // Calculate amount from days if amount is null or zero and days provided
                            if ((eDto.getAmount() == null || eDto.getAmount().compareTo(BigDecimal.ZERO) == 0)
                                    && eDto.getDays() != null) {

                                BigDecimal monthlySalary = ctc.getMonthlySalary();
                                BigDecimal perDay = monthlySalary.divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP);
                                BigDecimal amount = perDay
                                        .multiply(BigDecimal.valueOf(Integer.parseInt(eDto.getDays())))
                                        .setScale(2, RoundingMode.HALF_UP);

                                earningEntity.setAmount(amount);
                            }

                            return earningEntity;
                        })
                        .collect(Collectors.toList());

                List<OffCyclePayrunEmployeeDeduction> managedDeductions = (empDto.getDeductions() == null)
                        ? List.of()
                        : empDto.getDeductions().stream()
                        .map(dDto -> {
                            Deduction dbDeduction = deductionRepository.findByDeductionId(dDto.getDeductionId())
                                    .orElseThrow(() -> new RuntimeException("Deduction not found: " + dDto.getDeductionId()));
                            return OffCyclePayRunMapper.toEntity(dDto, null, dbDeduction);
                        })
                        .collect(Collectors.toList());

                OffCyclePayrunEmployee employeeEntity = OffCyclePayRunMapper.toEntity(empDto, payRun, employee, org,
                        managedEarnings, managedDeductions);

                if (employeeEntity.getEarnings() != null) {
                    employeeEntity.getEarnings().forEach(e -> e.setOffCyclePayrunEmployee(employeeEntity));
                }
                if (employeeEntity.getDeductions() != null) {
                    employeeEntity.getDeductions().forEach(d -> d.setOffCyclePayrunEmployee(employeeEntity));
                }

                payRun.getEmployees().add(employeeEntity);
            }
        }

        OffCyclePayRun saved = payRunRepository.save(payRun);
        return OffCyclePayRunMapper.toDto(saved);
    }

    @Override
    @Transactional
    public OffCyclePayRunDTO updatePayRun(String organizationId, String payrollRunId, OffCyclePayRunDTO requestDto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OffCyclePayRun payRun = payRunRepository.findByPayrollRunIdAndOrganization(payrollRunId, org)
                .orElseThrow(() -> new RuntimeException("Off-cycle pay run not found"));

        if (requestDto.getPayDate() != null) payRun.setPayDate(requestDto.getPayDate());
        if (requestDto.getStatus() != null) payRun.setStatus(requestDto.getStatus());
        if (requestDto.getStatusFormatted() != null) payRun.setStatusFormatted(requestDto.getStatusFormatted());
        if (requestDto.getType() != null) payRun.setType(requestDto.getType());
        if (requestDto.getNotes() != null) payRun.setNotes(requestDto.getNotes());

        payRun.getEmployees().clear();
        if (requestDto.getEmployees() != null && !requestDto.getEmployees().isEmpty()) {
            for (OffCyclePayrunEmployeeDTO empDto : requestDto.getEmployees()) {
                BasicDetails employee = basicDetailsRepository.findById(empDto.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("Employee not found: " + empDto.getEmployeeId()));

                CtcStructure ctc = ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_Id(organizationId, employee.getId())
                        .stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("CTC structure not found for employee: " + employee.getId()));

                List<OffCyclePayrunEmployeeEarning> managedEarnings = (empDto.getEarnings() == null)
                        ? List.of()
                        : empDto.getEarnings().stream()
                        .map(eDto -> {
                            Earning dbEarning = earningRepository.findByEarningId(eDto.getEarningId())
                                    .orElseThrow(() -> new RuntimeException("Earning not found: " + eDto.getEarningId()));
                            OffCyclePayrunEmployeeEarning earningEntity =
                                    OffCyclePayRunMapper.toEntity(eDto, null, dbEarning);

                            if ((eDto.getAmount() == null || eDto.getAmount().compareTo(BigDecimal.ZERO) == 0)
                                    && eDto.getDays() != null) {

                                BigDecimal monthlySalary = ctc.getMonthlySalary();
                                BigDecimal perDay = monthlySalary.divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP);
                                BigDecimal amount = perDay
                                        .multiply(BigDecimal.valueOf(Integer.parseInt(eDto.getDays())))
                                        .setScale(2, RoundingMode.HALF_UP);

                                earningEntity.setAmount(amount);
                            }

                            return earningEntity;
                        })
                        .collect(Collectors.toList());

                List<OffCyclePayrunEmployeeDeduction> managedDeductions = (empDto.getDeductions() == null)
                        ? List.of()
                        : empDto.getDeductions().stream()
                        .map(dDto -> {
                            Deduction dbDeduction = deductionRepository.findByDeductionId(dDto.getDeductionId())
                                    .orElseThrow(() -> new RuntimeException("Deduction not found: " + dDto.getDeductionId()));
                            return OffCyclePayRunMapper.toEntity(dDto, null, dbDeduction);
                        })
                        .collect(Collectors.toList());

                OffCyclePayrunEmployee employeeEntity = OffCyclePayRunMapper.toEntity(empDto, payRun, employee, org,
                        managedEarnings, managedDeductions);

                if (employeeEntity.getEarnings() != null) {
                    employeeEntity.getEarnings().forEach(e -> e.setOffCyclePayrunEmployee(employeeEntity));
                }
                if (employeeEntity.getDeductions() != null) {
                    employeeEntity.getDeductions().forEach(d -> d.setOffCyclePayrunEmployee(employeeEntity));
                }

                payRun.getEmployees().add(employeeEntity);
            }
        }

        OffCyclePayRun updated = payRunRepository.save(payRun);
        return OffCyclePayRunMapper.toDto(updated);
    }

    @Override
    public OffCyclePayRunDTO getPayRun(String organizationId, String payrollRunId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OffCyclePayRun payRun = payRunRepository.findByPayrollRunIdAndOrganization(payrollRunId, org)
                .orElseThrow(() -> new RuntimeException("Off-cycle pay run not found"));

        return OffCyclePayRunMapper.toDto(payRun);
    }

    @Override
    public List<OffCyclePayRunDTO> getAllPayRuns(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        return payRunRepository.findByOrganization(org).stream()
                .map(OffCyclePayRunMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deletePayRun(String organizationId, String payrollRunId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OffCyclePayRun payRun = payRunRepository.findByPayrollRunIdAndOrganization(payrollRunId, org)
                .orElseThrow(() -> new RuntimeException("Off-cycle pay run not found"));

        payRunRepository.delete(payRun);
    }

    @Override
    @Transactional
    public OffCyclePayRunDTO importEmployeesToPayRun(
            String organizationId,
            String payrollRunId,
            OffCyclePayRunImportRequestDTO requestDto) {

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OffCyclePayRun payRun = payRunRepository.findByPayrollRunIdAndOrganization(payrollRunId, org)
                .orElseThrow(() -> new RuntimeException("Off-cycle pay run not found"));

        // Pre-fetch master components once
        Earning bonusEarning = earningRepository.findByEarningNameAndOrganization("Bonus", org)
                .orElseThrow(() -> new RuntimeException("Bonus earning not found"));
        Earning commissionEarning = earningRepository.findByEarningNameAndOrganization("Commission", org)
                .orElseThrow(() -> new RuntimeException("Commission earning not found"));
        Deduction taxDeduction = deductionRepository.findByDeductionNameAndOrganization("Income Tax", org)
                .orElseThrow(() -> new RuntimeException("Income Tax deduction not found"));

        ObjectMapper mapper = new ObjectMapper();

        for (OffCyclePayRunImportDTO importDto : requestDto.getEmployees()) {
            BasicDetails employee = basicDetailsRepository
                    .findByEmployeeNumberAndOrganization(importDto.getEmployeeNumber(), org)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + importDto.getEmployeeNumber()));

            // Deduplication check: reuse if already exists
            OffCyclePayrunEmployee empEntity = payRun.getEmployees().stream()
                    .filter(e -> e.getEmployee().getId().equals(employee.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        OffCyclePayrunEmployee newEmp = new OffCyclePayrunEmployee();
                        newEmp.setPayrollRun(payRun);
                        newEmp.setEmployee(employee);
                        newEmp.setOrganization(org);
                        payRun.getEmployees().add(newEmp);
                        return newEmp;
                    });

            // --- Earnings ---
            if (importDto.getBonus() != null) {
                OffCyclePayrunEmployeeEarning bonus = new OffCyclePayrunEmployeeEarning();
                bonus.setEarning(bonusEarning);
                bonus.setAmount(importDto.getBonus());
                bonus.setOffCyclePayrunEmployee(empEntity);
                empEntity.getEarnings().add(bonus);
            }

            if (importDto.getCommission() != null) {
                OffCyclePayrunEmployeeEarning commission = new OffCyclePayrunEmployeeEarning();
                commission.setEarning(commissionEarning);
                commission.setAmount(importDto.getCommission());
                commission.setOffCyclePayrunEmployee(empEntity);
                empEntity.getEarnings().add(commission);
            }

            // --- Deductions ---
            if (importDto.getIncomeTax() != null) {
                OffCyclePayrunEmployeeDeduction deduction = new OffCyclePayrunEmployeeDeduction();
                deduction.setDeduction(taxDeduction);
                deduction.setAmount(importDto.getIncomeTax());
                deduction.setOffCyclePayrunEmployee(empEntity);
                empEntity.getDeductions().add(deduction);
            }

            // --- Taxes (JSON field) ---
            if (importDto.getIncomeTaxOverrideReason() != null && !importDto.getIncomeTaxOverrideReason().isBlank()) {
                try {
                    empEntity.setTaxes(mapper.writeValueAsString(
                            List.of("OverrideReason:" + importDto.getIncomeTaxOverrideReason())
                    ));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize taxes JSON", e);
                }
            }
        }

        OffCyclePayRun saved = payRunRepository.save(payRun);
        return OffCyclePayRunMapper.toDto(saved);
    }

    @Override
    @Transactional
    public OffCyclePayRunDTO importReleaseWithheldSalary(
            String organizationId,
            String payrollRunId,
            OffCyclePayRunReleaseWithheldImportRequestDTO requestDto) {

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OffCyclePayRun payRun = payRunRepository.findByPayrollRunIdAndOrganization(payrollRunId, org)
                .orElseThrow(() -> new RuntimeException("Off-cycle pay run not found"));

        ObjectMapper mapper = new ObjectMapper();

        for (OffCyclePayRunReleaseWithheldImportDTO importDto : requestDto.getEmployees()) {
            BasicDetails employee = basicDetailsRepository
                    .findByEmployeeNumberAndOrganization(importDto.getEmployeeNumber(), org)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + importDto.getEmployeeNumber()));

            // Deduplication check
            OffCyclePayrunEmployee empEntity = payRun.getEmployees().stream()
                    .filter(e -> e.getEmployee().getId().equals(employee.getId()))
                    .findFirst()
                    .orElseGet(() -> {
                        OffCyclePayrunEmployee newEmp = new OffCyclePayrunEmployee();
                        newEmp.setPayrollRun(payRun);
                        newEmp.setEmployee(employee);
                        newEmp.setOrganization(org);
                        payRun.getEmployees().add(newEmp);
                        return newEmp;
                    });

            // Build JSON data for Withheld Salary Release
            Map<String, Object> withheldData = new HashMap<>();
            withheldData.put("notes", importDto.getNotes());
            withheldData.put("releaseWithheldSalary", importDto.getReleaseWithheldSalary());
            withheldData.put("monthsToRelease", importDto.getMonthsToRelease());

            try {
                empEntity.setLopAdjustmentDetails(
                        mapper.writeValueAsString(List.of(withheldData))
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize withheld salary JSON", e);
            }
        }

        OffCyclePayRun saved = payRunRepository.save(payRun);
        return OffCyclePayRunMapper.toDto(saved);
    }
}
