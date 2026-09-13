package com.itsdev.payroll.serviceimpl;

import com.itsdev.payroll.dto.employee.preview.EmployeePreviewDTO;
import com.itsdev.payroll.dto.employee.preview.PreviewBenefitDTO;
import com.itsdev.payroll.dto.employee.preview.PreviewEarningDTO;
import com.itsdev.payroll.dto.employee.preview.PreviewReimbursementDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.employee.EmployeeBenefit;
import com.itsdev.payroll.entity.employee.EmployeeEarning;
import com.itsdev.payroll.entity.employee.EmployeeReimbursement;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Benefit;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;
import com.itsdev.payroll.mapper.SalaryPreviewMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.salarycomponents.BenefitRepository;
import com.itsdev.payroll.repository.salarycomponents.EarningRepository;
import com.itsdev.payroll.repository.salarycomponents.ReimbursementRepository;
import com.itsdev.payroll.service.SalaryPreviewService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SalaryPreviewServiceImpl implements SalaryPreviewService {

    private final CtcStructureRepository ctcRepository;
    private final EarningRepository earningRepository;
    private final BenefitRepository benefitRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;

    public SalaryPreviewServiceImpl(CtcStructureRepository ctcRepository,
                                    EarningRepository earningRepository,
                                    BenefitRepository benefitRepository,
                                    ReimbursementRepository reimbursementRepository,
                                    OrganizationRepository organizationRepository,
                                    BasicDetailsRepository basicDetailsRepository) {
        this.ctcRepository = ctcRepository;
        this.earningRepository = earningRepository;
        this.benefitRepository = benefitRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.organizationRepository = organizationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
    }

    @Override
    public EmployeePreviewDTO getEmployeePreview(String organizationId, String employeeId) {
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        // --- Try to fetch employee’s CTC structure ---
        Optional<CtcStructure> ctcOpt =
                ctcRepository.findByOrganization_OrganizationIdAndEmployee_EmployeeId(organizationId, employeeId)
                        .stream().findFirst();

        // --- Always load master definitions ---
        List<Earning> masterEarnings = earningRepository.findByOrganizationAndIsDeletedFalse(org)
                .stream()
                .filter(Earning::getIsIncludedInSalaryStructure)
                .collect(Collectors.toList());

        List<Benefit> masterBenefits = benefitRepository.findByOrganizationAndIsDeletedFalse(org)
                .stream()
                .filter(Benefit::isIncludedInSalaryStructure)
                .collect(Collectors.toList());

        List<Reimbursement> masterReimbursements = reimbursementRepository.findByOrganizationAndIsDeletedFalse(org)
                .stream()
                //.filter(Reimbursement::isIncludedInSalaryStructure)
                .filter(r -> Boolean.TRUE.equals(r.getIsIncludedInSalaryStructure()))

                .collect(Collectors.toList());

        EmployeePreviewDTO preview = new EmployeePreviewDTO();
        preview.setEmployeeId(employeeId);

        // --- Case 1: CTC exists ---
        if (ctcOpt.isPresent()) {
            CtcStructure ctc = ctcOpt.get();

            // fill employee info from BasicDetails inside CTC
            if (ctc.getEmployee() != null) {
                preview.setEmployeeNumber(ctc.getEmployee().getEmployeeNumber());
                preview.setEmployeeName(ctc.getEmployee().getFirstName() + " " + ctc.getEmployee().getLastName());
                preview.setEmployeeStatus(ctc.getEmployee().getEmployeeStatus());
            }

            Map<String, EmployeeEarning> employeeEarnings = Optional.ofNullable(ctc.getEarnings())
                    .orElse(List.of()).stream()
                    .collect(Collectors.toMap(e -> e.getEarning().getEarningId(), e -> e));

            Map<String, EmployeeBenefit> employeeBenefits = Optional.ofNullable(ctc.getBenefits())
                    .orElse(List.of()).stream()
                    .collect(Collectors.toMap(b -> b.getBenefit().getBenefitId(), b -> b));

            Map<String, EmployeeReimbursement> employeeReimbursements = Optional.ofNullable(ctc.getReimbursements())
                    .orElse(List.of()).stream()
                    .collect(Collectors.toMap(r -> r.getReimbursement().getReimbursementId(), r -> r));

            // merge
            preview.setEarnings(masterEarnings.stream()
                    .map(master -> {
                        EmployeeEarning emp = employeeEarnings.get(master.getEarningId());
                        return (emp != null)
                                ? SalaryPreviewMapper.toPreviewEarning(emp)
                                : SalaryPreviewMapper.toPreviewEarningFromMaster(master);
                    })
                    .collect(Collectors.toList()));

            preview.setBenefits(masterBenefits.stream()
                    .map(master -> {
                        EmployeeBenefit emp = employeeBenefits.get(master.getBenefitId());
                        return (emp != null)
                                ? SalaryPreviewMapper.toPreviewBenefit(emp)
                                : SalaryPreviewMapper.toPreviewBenefitFromMaster(master);
                    })
                    .collect(Collectors.toList()));

            preview.setReimbursements(masterReimbursements.stream()
                    .map(master -> {
                        EmployeeReimbursement emp = employeeReimbursements.get(master.getReimbursementId());
                        return (emp != null)
                                ? SalaryPreviewMapper.toPreviewReimbursement(emp)
                                : SalaryPreviewMapper.toPreviewReimbursementFromMaster(master);
                    })
                    .collect(Collectors.toList()));

        } else {
            // --- Case 2: No CTC yet → build from master only ---
            BasicDetails basic = basicDetailsRepository.findByOrganization_OrganizationIdAndEmployeeId(organizationId,employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found in organization"));

            // fill employee info
            preview.setEmployeeNumber(basic.getEmployeeNumber());
            preview.setEmployeeName(basic.getFirstName() + " " + basic.getLastName());
            preview.setEmployeeStatus(basic.getEmployeeStatus());

            preview.setEarnings(masterEarnings.stream()
                    .map(SalaryPreviewMapper::toPreviewEarningFromMaster)
                    .collect(Collectors.toList()));

            preview.setBenefits(masterBenefits.stream()
                    .map(SalaryPreviewMapper::toPreviewBenefitFromMaster)
                    .collect(Collectors.toList()));

            preview.setReimbursements(masterReimbursements.stream()
                    .map(SalaryPreviewMapper::toPreviewReimbursementFromMaster)
                    .collect(Collectors.toList()));
        }

        // --- Totals ---
        double earningsTotal = preview.getEarnings().stream()
                .mapToDouble(e -> Optional.ofNullable(e.getAmount()).orElse(0.0)).sum();
        double benefitsTotal = preview.getBenefits().stream()
                .mapToDouble(b -> Optional.ofNullable(b.getAmount()).orElse(0.0)).sum();
        double reimbursementsTotal = preview.getReimbursements().stream()
                .mapToDouble(r -> Optional.ofNullable(r.getAmount()).orElse(0.0)).sum();

        preview.setEarningsTotal(earningsTotal);
        preview.setBenefitsTotal(benefitsTotal);
        preview.setReimbursementsTotal(reimbursementsTotal);
        preview.setGrossAmount(earningsTotal + benefitsTotal + reimbursementsTotal);
        preview.setMonthlySalary(preview.getGrossAmount() / 12);
        preview.setCtc(preview.getGrossAmount());

        return preview;
    }
}
