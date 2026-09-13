package com.itsdev.payroll.serviceimpl.payRun.oneTimePayout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutImportRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutRequestDTO;
import com.itsdev.payroll.dto.payRun.oneTimePayout.OneTimePayoutResponseDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payRun.oneTimePayout.OneTimePayout;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.mapper.payRun.oneTimePayout.OneTimePayoutMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.payRun.oneTImePayout.OneTimePayoutRepository;
import com.itsdev.payroll.repository.salarycomponents.EarningRepository;
import com.itsdev.payroll.service.payRun.oneTImePayout.OneTimePayoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OneTimePayoutServiceImpl implements OneTimePayoutService {

    private final OneTimePayoutRepository payoutRepository;
    private final OrganizationRepository organizationRepository;
    private final EarningRepository earningRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final CtcStructureRepository ctcStructureRepository;

    public OneTimePayoutServiceImpl(
            OneTimePayoutRepository payoutRepository,
            OrganizationRepository organizationRepository,
            EarningRepository earningRepository,
            BasicDetailsRepository basicDetailsRepository,
            CtcStructureRepository ctcStructureRepository) {
        this.payoutRepository = payoutRepository;
        this.organizationRepository = organizationRepository;
        this.earningRepository = earningRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.ctcStructureRepository = ctcStructureRepository;
    }

    // -----------------------------
    // Step 1: Create draft payout
    // -----------------------------
    @Override
    @Transactional
    public OneTimePayoutResponseDTO createDraftPayout(String organizationId, OneTimePayoutRequestDTO requestDto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByEarningId(requestDto.getEarningId())
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        OneTimePayout payout = new OneTimePayout();
        payout.setOrganization(org);
        payout.setEarning(earning);
        payout.setPayDate(requestDto.getPayDate());
        payout.setEarningAmount(null); // no amount yet
        payout.setTaxes(null);         // no taxes yet

        OneTimePayout saved = payoutRepository.save(payout);
        return OneTimePayoutMapper.toResponseDTO(saved);
    }

    // -----------------------------
    // Step 2: Persist one payout row per employee
    // -----------------------------
    @Override
    @Transactional
    public List<OneTimePayoutResponseDTO> addEmployeesToPayout(String organizationId, OneTimePayoutRequestDTO requestDto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByEarningId(requestDto.getEarningId())
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        if (!earning.getOrganization().getId().equals(org.getId())) {
            throw new RuntimeException("Earning does not belong to this organization");
        }

        if (requestDto.getEmployees() == null || requestDto.getEmployees().isEmpty()) {
            return List.of();
        }

        List<OneTimePayout> toSave = new ArrayList<>();

        for (OneTimePayoutRequestDTO.EmployeePayoutDTO empDto : requestDto.getEmployees()) {
            BasicDetails employee = basicDetailsRepository.findById(empDto.getEmployeeId())
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + empDto.getEmployeeId()));

            if (!employee.getOrganization().getId().equals(org.getId())) {
                throw new RuntimeException("Employee " + empDto.getEmployeeId() + " does not belong to this organization");
            }

            CtcStructure ctc = ctcStructureRepository.findByOrganization_OrganizationIdAndEmployee_Id(
                            organizationId, employee.getId())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("CTC structure not found for employee: " + employee.getId()));

            BigDecimal monthlySalary = ctc.getMonthlySalary();

            // Build payout entity
            OneTimePayout payout = OneTimePayoutMapper.toEntity(empDto, earning, employee, requestDto.getPayDate());
            payout.setOrganization(org);

            // Calculate earningAmount from days if not provided
            if (empDto.getDays() != null &&
                    (empDto.getEarningAmount() == null || empDto.getEarningAmount().compareTo(BigDecimal.ZERO) == 0)) {

                BigDecimal amount = monthlySalary
                        .divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP) // keep more precision
                        .multiply(BigDecimal.valueOf(empDto.getDays()))
                        .setScale(2, RoundingMode.HALF_UP); // final rounding


                payout.setEarningAmount(amount);
            }

            toSave.add(payout);
        }

        List<OneTimePayout> saved = payoutRepository.saveAll(toSave);
        return saved.stream()
                .map(OneTimePayoutMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // -----------------------------
    // Helper: Calculate earning amount from days (always ÷30 now)
    // -----------------------------
    private BigDecimal calculateAmountFromDays(BasicDetails employee, Organization org, Integer days) {
        CtcStructure ctc = ctcStructureRepository.findByOrganization_OrganizationIdAndEmployee_Id(
                        org.getOrganizationId(), employee.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("CTC structure not found for employee: " + employee.getId()));

        BigDecimal monthlySalary = ctc.getMonthlySalary();

        return monthlySalary
                .divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP) // keep more precision
                .multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP); // final rounding
    }


    // -----------------------------
    // Fetch payout by ID
    // -----------------------------
    @Override
    public OneTimePayoutResponseDTO getPayout(String organizationId, Long payoutId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OneTimePayout payout = payoutRepository.findByIdAndOrganization(payoutId, org)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        return OneTimePayoutMapper.toResponseDTO(payout);
    }

    @Override
    public List<OneTimePayoutResponseDTO> getAllPayouts(String organizationId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        return payoutRepository.findByOrganization(org).stream()
                .map(OneTimePayoutMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OneTimePayoutResponseDTO> getPayoutsByEarning(String organizationId, String earningId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        return payoutRepository.findByEarning_EarningIdAndOrganization(earningId, org).stream()
                .map(OneTimePayoutMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<OneTimePayoutResponseDTO> getPayoutsByEmployee(String organizationId, Long employeeId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        return payoutRepository.findByEmployee_IdAndOrganization(employeeId, org).stream()
                .map(OneTimePayoutMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // -----------------------------
    // Import employees
    // -----------------------------
    @Override
    @Transactional
    public List<OneTimePayoutResponseDTO> importEmployeesToPayout(String organizationId, OneTimePayoutImportRequestDTO requestDto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Earning earning = earningRepository.findByEarningId(requestDto.getEarningId())
                .orElseThrow(() -> new RuntimeException("Earning not found"));

        if (!earning.getOrganization().getId().equals(org.getId())) {
            throw new RuntimeException("Earning does not belong to this organization");
        }

        if (requestDto.getEmployees() == null || requestDto.getEmployees().isEmpty()) {
            return List.of();
        }

        List<OneTimePayout> toSave = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        for (OneTimePayoutImportDTO importDto : requestDto.getEmployees()) {
            BasicDetails employee = basicDetailsRepository
                    .findByEmployeeNumberAndOrganization(importDto.getEmployeeNumber(), org)
                    .orElseThrow(() -> new RuntimeException("Employee not found: " + importDto.getEmployeeNumber()));

            OneTimePayout payout = new OneTimePayout();
            payout.setOrganization(org);
            payout.setEarning(earning);
            payout.setEmployee(employee);
            payout.setPayDate(requestDto.getPayDate());
            payout.setEarningAmount(importDto.getEarningAmount());

            // Persist taxes and override reason as JSON
            List<String> taxes = new ArrayList<>();
            if (importDto.getIncomeTax() != null && !importDto.getIncomeTax().isBlank()) {
                taxes.add("IncomeTax:" + importDto.getIncomeTax());
            }
            if (importDto.getIncomeTaxOverrideReason() != null && !importDto.getIncomeTaxOverrideReason().isBlank()) {
                taxes.add("OverrideReason:" + importDto.getIncomeTaxOverrideReason());
            }

            if (!taxes.isEmpty()) {
                try {
                    payout.setTaxes(mapper.writeValueAsString(taxes));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize taxes JSON", e);
                }
            } else {
                payout.setTaxes(null);
            }

            toSave.add(payout);
        }

        return payoutRepository.saveAll(toSave).stream()
                .map(OneTimePayoutMapper::toResponseDTO)
                .toList();
    }

    // -----------------------------
    // Update payout
    // -----------------------------
    @Override
    @Transactional
    public OneTimePayoutResponseDTO updatePayout(String organizationId, Long payoutId, OneTimePayoutRequestDTO requestDto) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OneTimePayout payout = payoutRepository.findByIdAndOrganization(payoutId, org)
                .orElseThrow(() -> new RuntimeException("Payout not found for this organization"));

        // Update payDate if provided
        if (requestDto.getPayDate() != null) {
            payout.setPayDate(requestDto.getPayDate());
        }

        if (requestDto.getEmployees() != null && !requestDto.getEmployees().isEmpty()) {
            OneTimePayoutRequestDTO.EmployeePayoutDTO empDto = requestDto.getEmployees().get(0);

            if (empDto.getEmployeeId() != null) {
                BasicDetails employee = basicDetailsRepository.findById(empDto.getEmployeeId())
                        .orElseThrow(() -> new RuntimeException("Employee not found: " + empDto.getEmployeeId()));
                payout.setEmployee(employee);
            }

            if (empDto.getTaxes() != null) {
                try {
                    payout.setTaxes(new ObjectMapper().writeValueAsString(empDto.getTaxes()));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize taxes", e);
                }
            }

            if (empDto.getDays() != null) {
                payout.setDays(empDto.getDays());
            }

            // If amount is provided, use it; else calculate from 30 days
            if (empDto.getEarningAmount() != null) {
                payout.setEarningAmount(empDto.getEarningAmount());
            } else if (empDto.getDays() != null) {
                CtcStructure ctc = ctcStructureRepository.findByOrganization_OrganizationIdAndEmployee_Id(
                                organizationId, payout.getEmployee().getId())
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("CTC structure not found for employee: " + payout.getEmployee().getId()));

                BigDecimal monthlySalary = ctc.getMonthlySalary();
                BigDecimal amount = monthlySalary
                        .divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP) // keep more precision
                        .multiply(BigDecimal.valueOf(empDto.getDays()))
                        .setScale(2, RoundingMode.HALF_UP); // final rounding

                payout.setEarningAmount(amount);
            }
        }

        OneTimePayout updated = payoutRepository.save(payout);
        return OneTimePayoutMapper.toResponseDTO(updated);
    }

    // -----------------------------
    // Delete payout
    // -----------------------------
    @Override
    @Transactional
    public void deletePayout(String organizationId, Long payoutId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        OneTimePayout payout = payoutRepository.findByIdAndOrganization(payoutId, org)
                .orElseThrow(() -> new RuntimeException("Payout not found"));

        payoutRepository.delete(payout);
    }
}
