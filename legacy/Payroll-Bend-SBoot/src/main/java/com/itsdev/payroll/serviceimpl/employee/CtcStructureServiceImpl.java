package com.itsdev.payroll.serviceimpl.employee;

// import com.itsdev.payroll.controller.employee.CtcStructureController;
import com.itsdev.payroll.dto.employee.*;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionExportRowDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.EmployeeCtcRevisionListDTO;
// import com.itsdev.payroll.dto.employee.SalaryRevision.ProcessLaterRevisionDTO;
import com.itsdev.payroll.dto.employee.SalaryRevision.ProcessLaterRevisionDTO;
import com.itsdev.payroll.entity.employee.*;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.salarycomponents.Benefit;
import com.itsdev.payroll.entity.salarycomponents.Earning;
import com.itsdev.payroll.entity.salarycomponents.Reimbursement;
import com.itsdev.payroll.enumeration.employee.CalculationBasis;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.employeeTDS.EmployeeTdsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.repository.salarycomponents.BenefitRepository;
import com.itsdev.payroll.repository.salarycomponents.EarningRepository;
import com.itsdev.payroll.repository.salarycomponents.ReimbursementRepository;
import com.itsdev.payroll.service.employee.CtcStructureService;
import com.itsdev.payroll.service.employeeTDS.TdsSalaryRevisionService;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CtcStructureServiceImpl implements CtcStructureService {

    private final CtcStructureRepository ctcStructureRepository;
    private final OrganizationRepository organizationRepository;
    private final ReimbursementRepository reimbursementRepository;
    private final BenefitRepository benefitRepository;
    private final EarningRepository earningRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final TdsSalaryRevisionService tdsSalaryRevisionService;
    @Autowired
    private EmployeeTdsRepository employeeTdsRepository;


    private static final Logger log = LoggerFactory.getLogger(CtcStructureServiceImpl.class);



    public CtcStructureServiceImpl(
            CtcStructureRepository ctcStructureRepository,
            OrganizationRepository organizationRepository,
            ReimbursementRepository reimbursementRepository,
            BenefitRepository benefitRepository,
            EarningRepository earningRepository,
            BasicDetailsRepository basicDetailsRepository,
            TdsSalaryRevisionService tdsSalaryRevisionService
    ) {
        this.ctcStructureRepository = ctcStructureRepository;
        this.organizationRepository = organizationRepository;
        this.reimbursementRepository = reimbursementRepository;
        this.benefitRepository = benefitRepository;
        this.earningRepository = earningRepository;
        this.basicDetailsRepository=basicDetailsRepository;
        this.tdsSalaryRevisionService = tdsSalaryRevisionService;
    }


@Transactional
@Override
public EmployeeCTCDTO create(String organizationId, EmployeeCTCDTO dto) {

    Organization org = organizationRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

    // --- fetch employee ---
    BasicDetails employee = basicDetailsRepository
            .findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
            .orElseThrow(() ->
                    new IllegalArgumentException("Employee not found: " + dto.getEmployeeId())
            );

    // --- build CTC entity ---
    CtcStructure entity = new CtcStructure();
    entity.setAnnualCtc(dto.getCtc());
    entity.setOrganization(org);
    entity.setEmployee(employee);
    entity.setRevision(false);
    entity.setAppliedInPayrun(false);


    // ✅ ADD THIS: mark new CTC as active
    entity.setActive(true);


//// ✅ ADD THIS: set effective date (if you don't take from DTO, use today)
//    entity.setEffectiveDate(LocalDate.now());

    if (employee.getDateOfJoining() == null || employee.getDateOfJoining().isBlank()) {
        throw new IllegalArgumentException("Date of Joining is required to set effective date");
    }

    LocalDate doj = LocalDate.parse(employee.getDateOfJoining()); // yyyy-MM-dd
    entity.setEffectiveDate(doj);



// ✅ ADD THIS: updatedAt
    entity.setUpdatedAt(LocalDateTime.now());

    // --- monthly salary ---
    if (dto.getMonthlySalary() != null) {
        entity.setMonthlySalary(BigDecimal.valueOf(dto.getMonthlySalary()));
    } else if (dto.getCtc() != null) {
        entity.setMonthlySalary(
                BigDecimal.valueOf(dto.getCtc())
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
        );
    }

    // ------------------- Earnings -------------------
    if (dto.getEarnings() != null && !dto.getEarnings().isEmpty()) {
        entity.setEarnings(dto.getEarnings().stream().map(eDTO -> {
            EmployeeEarning e = new EmployeeEarning();
            e.setEnabled(eDTO.getEnabled());
            e.setAmount(eDTO.getAmount());
            e.setAmountInPercentage(eDTO.getAmountInPercentage());
            e.setEditable(eDTO.getEditable());
            e.setIsVariable(eDTO.getIsVariable());
            e.setEarningFrequency(eDTO.getEarningFrequency());
            e.setOverrideAmount(eDTO.getOverrideAmount());

            if (eDTO.getCalculationBasis() != null) {
                e.setCalculationBasis(
                        CalculationBasis.valueOf(eDTO.getCalculationBasis())
                );
            }

            Earning earningMaster = earningRepository.findByEarningId(eDTO.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Earning not found: " + eDTO.getId()));

            e.setEarning(earningMaster);
            e.setOrganization(org);
            e.setCtcStructure(entity);
            return e;
        }).collect(Collectors.toList()));
    }

    // ------------------- Benefits -------------------
    if (dto.getBenefits() != null && !dto.getBenefits().isEmpty()) {
        entity.setBenefits(dto.getBenefits().stream().map(bDTO -> {
            EmployeeBenefit b = new EmployeeBenefit();
            b.setEnabled(bDTO.getEnabled());
            b.setAmount(bDTO.getAmount());

            Benefit benefitMaster =
                    benefitRepository.findByBenefitIdAndOrganization(bDTO.getId(), org)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Benefit not found for id: " + bDTO.getId()
                            ));

            b.setBenefit(benefitMaster);
            b.setOrganization(org);
            b.setCtcStructure(entity);
            return b;
        }).collect(Collectors.toList()));
    }

    // ------------------- Reimbursements -------------------
    if (dto.getReimbursements() != null && !dto.getReimbursements().isEmpty()) {
        entity.setReimbursements(dto.getReimbursements().stream().map(rDTO -> {
            EmployeeReimbursement r = new EmployeeReimbursement();
            r.setEnabled(rDTO.getEnabled());
            r.setAmount(rDTO.getAmount());
            r.setCarryForwardOption(rDTO.getCarryForwardOption());

            Reimbursement reimbursementMaster =
                    reimbursementRepository.findByReimbursementId(rDTO.getId())
                            .orElseThrow(() ->
                                    new IllegalArgumentException("Reimbursement not found: " + rDTO.getId())
                            );

            r.setReimbursement(reimbursementMaster);
            r.setOrganization(org);
            r.setCtcStructure(entity);
            return r;
        }).collect(Collectors.toList()));
    }

    // ------------------- Variable Earnings -------------------
    if (dto.getVariableEarnings() != null && !dto.getVariableEarnings().isEmpty()) {
        entity.setVariableEarnings(dto.getVariableEarnings().stream().map(vDTO -> {
            VariableEarning v = new VariableEarning();
            v.setEnabled(vDTO.getEnabled());
            v.setAmount(vDTO.getAmount());
            v.setAmountInPercentage(vDTO.getAmountInPercentage());
            v.setEditable(vDTO.getEditable());
            v.setVariableCode(vDTO.getVariableCode());
            v.setOrganization(org);
            v.setCtcStructure(entity);
            return v;
        }).collect(Collectors.toList()));
    }

    // ------------------- FBP Components -------------------
    if (dto.getFbpComponents() != null && !dto.getFbpComponents().isEmpty()) {
        entity.setFbpComponents(dto.getFbpComponents().stream().map(fDTO -> {
            FbpComponent f = new FbpComponent();
            f.setEnabled(fDTO.getEnabled());
            f.setAmount(fDTO.getAmount());
            f.setComponentCode(fDTO.getComponentCode());
            f.setOrganization(org);
            f.setCtcStructure(entity);
            return f;
        }).collect(Collectors.toList()));
    }

    // =================================================================
    //   STEP 5  → ADD EPF COMPONENTS BEFORE SAVING PARENT
    // =================================================================
    if (dto.getEpfComponents() != null && !dto.getEpfComponents().isEmpty()) {

        List<CtcEpfComponent> epfList = dto.getEpfComponents().stream().map(epfDTO -> {
            CtcEpfComponent epf = new CtcEpfComponent();
            epf.setCtcStructure(entity);
            epf.setOrganization(org);
            epf.setComponentCode(epfDTO.getComponentCode());
            epf.setComponentLabel(epfDTO.getComponentLabel());
            epf.setPercentage(epfDTO.getPercentage());
            epf.setMonthlyAmount(epfDTO.getMonthlyAmount());
            epf.setAnnualAmount(epfDTO.getAnnualAmount());
            epf.setCalculationType(epfDTO.getCalculationType());
            return epf;
        }).collect(Collectors.toList());

        entity.setEpfComponents(epfList);
    }

    // =================================================================
    //   STEP 5  → ADD ESI COMPONENTS BEFORE SAVING PARENT
    // =================================================================
    if (dto.getEsiComponents() != null && !dto.getEsiComponents().isEmpty()) {

        List<CtcEsiComponent> esiList = dto.getEsiComponents().stream().map(esiDTO -> {
            CtcEsiComponent esi = new CtcEsiComponent();
            esi.setCtcStructure(entity);
            esi.setOrganization(org);
            esi.setComponentCode(esiDTO.getComponentCode());
            esi.setComponentLabel(esiDTO.getComponentLabel());
            esi.setPercentage(esiDTO.getPercentage());
            esi.setMonthlyAmount(esiDTO.getMonthlyAmount());
            esi.setAnnualAmount(esiDTO.getAnnualAmount());
            esi.setCalculationType(esiDTO.getCalculationType());
            return esi;
        }).collect(Collectors.toList());

        entity.setEsiComponents(esiList);
    }

    // ------------------- SAVE EVERYTHING IN ONE GO -------------------
    CtcStructure saved = ctcStructureRepository.save(entity);

    // ------------------- RETURN DTO -------------------

    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    return mapToDto(saved);
}


    @Override
    @Transactional
    public EmployeeCTCDTO update(String organizationId, EmployeeCTCDTO dto) {

        // 1️⃣ Validate organization
        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        // 2️⃣ Validate employee
        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Employee not found: " + dto.getEmployeeId())
                );

        if (dto.getCtc() == null) {
            throw new IllegalArgumentException("CTC cannot be null");
        }

        // 3️⃣ Load active CTC
        CtcStructure entity = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrueOrderByCreatedAtDesc(
                        organizationId, employee.getId()
                )
                .orElseThrow(() ->
                        new RuntimeException("Active CTC Structure not found for employee: " + dto.getEmployeeId())
                );

        // 4️⃣ Update top-level fields
        entity.setAnnualCtc(dto.getCtc());
        entity.setEmployee(employee);
        entity.setOrganization(org);
        entity.setActive(true);            // keep active
        entity.setRevision(false);
        // update ≠ revision
        entity.setAppliedInPayrun(false);

        // Effective Date → DOJ (same as create)
        if (employee.getDateOfJoining() == null || employee.getDateOfJoining().isBlank()) {
            throw new IllegalArgumentException("Date of Joining is required to set effective date");
        }
        entity.setEffectiveDate(LocalDate.parse(employee.getDateOfJoining()));

        // Monthly salary
        if (dto.getMonthlySalary() != null) {
            entity.setMonthlySalary(BigDecimal.valueOf(dto.getMonthlySalary()));
        } else {
            entity.setMonthlySalary(
                    BigDecimal.valueOf(dto.getCtc())
                            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
            );
        }

        // ==========================================================
        // 5️⃣ Replace child collections (orphanRemoval = true)
        // ==========================================================

        // ---- Earnings ----
        entity.getEarnings().clear();
        if (dto.getEarnings() != null) {
            dto.getEarnings().forEach(eDTO -> {
                EmployeeEarning e = new EmployeeEarning();
                e.setEnabled(eDTO.getEnabled());
                e.setAmount(eDTO.getAmount());
                e.setAmountInPercentage(eDTO.getAmountInPercentage());
                e.setEditable(eDTO.getEditable());
                e.setIsVariable(eDTO.getIsVariable());
                e.setEarningFrequency(eDTO.getEarningFrequency());
                e.setOverrideAmount(eDTO.getOverrideAmount());

                if (eDTO.getCalculationBasis() != null) {
                    e.setCalculationBasis(
                            CalculationBasis.valueOf(eDTO.getCalculationBasis())
                    );
                }

                Earning master = earningRepository.findByEarningId(eDTO.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Earning not found: " + eDTO.getId()));

                e.setEarning(master);
                e.setOrganization(org);
                e.setCtcStructure(entity);
                entity.getEarnings().add(e);
            });
        }

        // ---- Benefits ----
        entity.getBenefits().clear();
        if (dto.getBenefits() != null) {
            dto.getBenefits().forEach(bDTO -> {
                EmployeeBenefit b = new EmployeeBenefit();
                b.setEnabled(bDTO.getEnabled());
                b.setAmount(bDTO.getAmount());
                b.setAmountInPercentage(bDTO.getAmountInPercentage());

                Benefit master = benefitRepository
                        .findByBenefitIdAndOrganization(bDTO.getId(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Benefit not found: " + bDTO.getId()));

                b.setBenefit(master);
                b.setOrganization(org);
                b.setCtcStructure(entity);
                entity.getBenefits().add(b);
            });
        }

        // ---- Reimbursements ----
        entity.getReimbursements().clear();
        if (dto.getReimbursements() != null) {
            dto.getReimbursements().forEach(rDTO -> {
                EmployeeReimbursement r = new EmployeeReimbursement();
                r.setEnabled(rDTO.getEnabled());
                r.setAmount(rDTO.getAmount());
                r.setCarryForwardOption(rDTO.getCarryForwardOption());

                Reimbursement master = reimbursementRepository
                        .findByReimbursementId(rDTO.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Reimbursement not found: " + rDTO.getId()));

                r.setReimbursement(master);
                r.setOrganization(org);
                r.setCtcStructure(entity);
                entity.getReimbursements().add(r);
            });
        }

        // ---- Variable Earnings ----
        entity.getVariableEarnings().clear();
        if (dto.getVariableEarnings() != null) {
            dto.getVariableEarnings().forEach(vDTO -> {
                VariableEarning v = new VariableEarning();
                v.setEnabled(vDTO.getEnabled());
                v.setAmount(vDTO.getAmount());
                v.setAmountInPercentage(vDTO.getAmountInPercentage());
                v.setEditable(vDTO.getEditable());
                v.setVariableCode(vDTO.getVariableCode());

                v.setOrganization(org);
                v.setCtcStructure(entity);
                entity.getVariableEarnings().add(v);
            });
        }

        // ---- FBP Components ----
        entity.getFbpComponents().clear();
        if (dto.getFbpComponents() != null) {
            dto.getFbpComponents().forEach(fDTO -> {
                FbpComponent f = new FbpComponent();
                f.setEnabled(fDTO.getEnabled());
                f.setAmount(fDTO.getAmount());
                f.setComponentCode(fDTO.getComponentCode());

                f.setOrganization(org);
                f.setCtcStructure(entity);
                entity.getFbpComponents().add(f);
            });
        }

        // ---- EPF Components ----
        entity.getEpfComponents().clear();
        if (dto.getEpfComponents() != null) {
            dto.getEpfComponents().forEach(epfDTO -> {
                CtcEpfComponent epf = new CtcEpfComponent();
                epf.setCtcStructure(entity);
                epf.setOrganization(org);
                epf.setComponentCode(epfDTO.getComponentCode());
                epf.setComponentLabel(epfDTO.getComponentLabel());
                epf.setPercentage(epfDTO.getPercentage());
                epf.setMonthlyAmount(epfDTO.getMonthlyAmount());
                epf.setAnnualAmount(epfDTO.getAnnualAmount());
                epf.setCalculationType(epfDTO.getCalculationType());

                entity.getEpfComponents().add(epf);
            });
        }

        // ---- ESI Components ----
        entity.getEsiComponents().clear();
        if (dto.getEsiComponents() != null) {
            dto.getEsiComponents().forEach(esiDTO -> {
                CtcEsiComponent esi = new CtcEsiComponent();
                esi.setCtcStructure(entity);
                esi.setOrganization(org);
                esi.setComponentCode(esiDTO.getComponentCode());
                esi.setComponentLabel(esiDTO.getComponentLabel());
                esi.setPercentage(esiDTO.getPercentage());
                esi.setMonthlyAmount(esiDTO.getMonthlyAmount());
                esi.setAnnualAmount(esiDTO.getAnnualAmount());
                esi.setCalculationType(esiDTO.getCalculationType());

                entity.getEsiComponents().add(esi);
            });
        }

        // 6️⃣ Audit
        entity.setUpdatedAt(LocalDateTime.now());

        // 7️⃣ Save
        CtcStructure saved = ctcStructureRepository.save(entity);
        return mapToDto(saved);
    }


    @Override
    @Transactional(readOnly = true)
    public EmployeeCTCDTO get(String organizationId, Long id) {
        CtcStructure entity = ctcStructureRepository
                .findByIdAndOrganization_OrganizationId(id, organizationId)
                .orElseThrow(() -> new RuntimeException("CTC Structure not found"));
        return mapToDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeCTCDTO> list(String organizationId) {
        return ctcStructureRepository.findAllByOrganization_OrganizationId(organizationId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String organizationId, Long id) {
        ctcStructureRepository.deleteByIdAndOrganization_OrganizationId(id, organizationId);
    }

    // ==== helpers ====

    private double getBasicAnnual(CtcStructure entity, Double newCtc) {
        if (entity.getEarnings() == null) return 0.0;
        return entity.getEarnings().stream()
                .filter(e -> "Basic Pay".equalsIgnoreCase(e.getEarning().getEarningName()))
                .findFirst()
                .map(e -> {
                    Double pct = e.getAmountInPercentage();
                    return pct == null ? 0.0 : (newCtc * pct) / 100.0;
                })
                .orElse(0.0);
    }

    public List<EmployeeCTCDTO> getAllByEmployeeId(String organizationId, Long employeeId) {
        return ctcStructureRepository
                .findByOrganization_OrganizationIdAndEmployee_Id(organizationId, employeeId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private EmployeeCTCDTO mapToDto(CtcStructure entity) {
        EmployeeCTCDTO dto = new EmployeeCTCDTO();



        // Annual CTC
        dto.setCtc(entity.getAnnualCtc());

        // ✅ Active flag
        dto.setActive(entity.getActive());

// ✅ Effective Date
        dto.setEffectiveDate(entity.getEffectiveDate());


// ✅ Audit fields (optional but recommended)
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        dto.setPaymentMonth(entity.getPaymentMonth());
        dto.setChangeInPercent(entity.getChangeInPercentage());
        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setRevisionStatus(
                entity.getRevisionStatus() != null
                        ? entity.getRevisionStatus().name()
                        : null
        );

        dto.setAppliedInPayrun(entity.getAppliedInPayrun());
        // 🔹 Employee basic details
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getEmployeeId());
            dto.setEmployeeNumber(entity.getEmployee().getEmployeeNumber());
            dto.setFirstName(entity.getEmployee().getFirstName());
            dto.setMiddleName(entity.getEmployee().getMiddleName());
            dto.setLastName(entity.getEmployee().getLastName());
        }



        // 🔹 Map organizationId
        if (entity.getOrganization() != null) {
            dto.setOrganizationId(entity.getOrganization().getOrganizationId());
        }

        dto.setCtcStructureId(entity.getId());
        //  dto.setRevisionId(entity.getId());

        dto.setCtcStructureId(entity.getId());

        if (Boolean.TRUE.equals(entity.getRevision())) {
            dto.setRevisionId(entity.getId());
        } else {
            dto.setRevisionId(null);
        }



        // 🔹 Map employeeId
        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getEmployeeId());
        }

        // --- Earnings ---
        dto.setEarnings(entity.getEarnings() != null
                ? entity.getEarnings().stream().map(e -> {
            EmployeeEarningDTO ed = new EmployeeEarningDTO();
            ed.setId(e.getEarning().getEarningId());
            ed.setEarningCode(e.getEarning().getEarningName());
            ed.setEnabled(e.getEnabled());
            ed.setAmount(e.getAmount());
            ed.setAmountInPercentage(e.getAmountInPercentage());
            ed.setEditable(e.getEditable());
            ed.setIsVariable(e.getIsVariable());
            ed.setOverrideAmount(e.getOverrideAmount()); // ✅ ADD THIS

            ed.setEarningFrequency(e.getEarningFrequency());
            ed.setCalculationBasis(
                    e.getCalculationBasis() != null
                            ? e.getCalculationBasis().name()
                            : null
            );
            return ed;
        }).collect(Collectors.toList())
                : List.of());

        // --- Benefits ---
        dto.setBenefits(entity.getBenefits() != null
                ? entity.getBenefits().stream().map(b -> {
            EmployeeBenefitDTO bd = new EmployeeBenefitDTO();
            bd.setId(b.getBenefit().getBenefitId());
            bd.setBenefitCode(b.getBenefit().getBenefitName());
            bd.setEnabled(b.getEnabled());
            bd.setAmount(b.getAmount());
            bd.setAmountInPercentage(b.getAmountInPercentage());
            return bd;
        }).collect(Collectors.toList())
                : List.of());

        // --- Reimbursements ---
        dto.setReimbursements(entity.getReimbursements() != null
                ? entity.getReimbursements().stream().map(r -> {
            EmployeeReimbursementDTO rd = new EmployeeReimbursementDTO();
            rd.setId(r.getReimbursement().getReimbursementId());
            rd.setName(r.getReimbursement().getReimbursementName());
            rd.setEnabled(r.getEnabled());
            rd.setAmount(r.getAmount());
            rd.setCarryForwardOption(r.getCarryForwardOption());
            return rd;
        }).collect(Collectors.toList())
                : List.of());

        // --- Variable Earnings ---
        dto.setVariableEarnings(entity.getVariableEarnings() != null
                ? entity.getVariableEarnings().stream().map(v -> {
            EmployeeVariableEarningDTO vd = new EmployeeVariableEarningDTO();
            // id intentionally omitted (handled elsewhere)
            vd.setVariableCode(v.getVariableCode());
            vd.setEnabled(v.getEnabled());
            vd.setAmount(v.getAmount());
            vd.setAmountInPercentage(v.getAmountInPercentage());
            vd.setEditable(v.getEditable());
            return vd;
        }).collect(Collectors.toList())
                : List.of());

        // --- FBP Components ---
        dto.setFbpComponents(entity.getFbpComponents() != null
                ? entity.getFbpComponents().stream().map(f -> {
            EmployeeFBPComponentDTO fd = new EmployeeFBPComponentDTO();
            // id intentionally omitted (handled elsewhere)
            fd.setComponentCode(f.getComponentCode());
            fd.setEnabled(f.getEnabled());
            fd.setAmount(f.getAmount());
            return fd;
        }).collect(Collectors.toList())
                : List.of());

        // --- EPF Components (NEW) ---
        dto.setEpfComponents(entity.getEpfComponents() != null
                ? entity.getEpfComponents().stream().map(epf -> {
            EpfComponentDTO ed = new EpfComponentDTO();
            // id intentionally omitted (handled elsewhere)
            ed.setComponentCode(epf.getComponentCode());
            ed.setComponentLabel(epf.getComponentLabel());
            ed.setPercentage(epf.getPercentage());
            ed.setMonthlyAmount(epf.getMonthlyAmount());
            ed.setAnnualAmount(epf.getAnnualAmount());
            ed.setCalculationType(epf.getCalculationType());
            return ed;
        }).collect(Collectors.toList())
                : List.of());

        // --- ESI Components (NEW) ---
        dto.setEsiComponents(entity.getEsiComponents() != null
                ? entity.getEsiComponents().stream().map(esi -> {
            EsiComponentDTO sd = new EsiComponentDTO();
            // id intentionally omitted (handled elsewhere)
            sd.setComponentCode(esi.getComponentCode());
            sd.setComponentLabel(esi.getComponentLabel());
            sd.setPercentage(esi.getPercentage());
            sd.setMonthlyAmount(esi.getMonthlyAmount());
            sd.setAnnualAmount(esi.getAnnualAmount());
            sd.setCalculationType(esi.getCalculationType());
            return sd;
        }).collect(Collectors.toList())
                : List.of());

        // Monthly Salary
        if (entity.getMonthlySalary() != null) {
            dto.setMonthlySalary(entity.getMonthlySalary().doubleValue());
        }


        // ✅ Fetch Final Annual Tax from EmployeeTds (ACTIVE ONLY)

        if (entity.getEmployee() != null && entity.getEffectiveDate() != null) {

            String employeeId = entity.getEmployee().getEmployeeId();

            Integer fiscalYear = getFiscalYear(entity.getEffectiveDate());

            employeeTdsRepository
                    .findActiveByEmployeeAndFiscalYear(employeeId, fiscalYear)
                    .ifPresent(tds ->
                            dto.setFinalAnnualTax(tds.getFinalAnnualTax())
                    );
        }
        return dto;
    }


    private Integer getFiscalYear(LocalDate effectiveDate) {

        if (effectiveDate.getMonthValue() >= 4) {

            return effectiveDate.getYear() + 1;

        } else {

            return effectiveDate.getYear();

        }
    }


    @Override
    public CtcStructureDTO getSalaryStructureByWorkMail(String email) {
        String method = "getSalaryStructureByWorkMail";
        log.info("[{}] 📥 Incoming request for employee email: {}", method, email);

        BasicDetails employee = basicDetailsRepository.findByWorkMail(email)
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Employee not found with work mail: {}", method, email);
                    return new RuntimeException("Employee not found with mail: " + email);
                });
        log.info("[{}] ✅ Employee found: {} {}", method, employee.getFirstName(), employee.getLastName());

        CtcStructure ctc = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrueOrderByCreatedAtDesc(
                        employee.getOrganization().getOrganizationId(),
                        employee.getId()
                )
                .orElseThrow(() -> new RuntimeException("Active salary structure not defined for employee: " + email));

        if (ctc == null) {
            log.warn("[{}] ⚠️ No salary structure defined for employee: {}", method, employee.getEmployeeNumber());
            throw new RuntimeException("Salary structure not defined for employee: " + email);
        }
        log.info("[{}] 💰 Salary structure found for employee: Annual CTC={}, Monthly Salary={}",
                method, ctc.getAnnualCtc(), ctc.getMonthlySalary());

        CtcStructureDTO dto = new CtcStructureDTO();
        dto.setCtc(ctc.getAnnualCtc());
        dto.setMonthlySalary(ctc.getMonthlySalary().doubleValue());

        // employee info
        dto.setEmployeeNumber(employee.getEmployeeNumber());
        dto.setFirstName(employee.getFirstName());
        dto.setMiddleName(employee.getMiddleName());
        dto.setLastName(employee.getLastName());
        dto.setDateOfJoining(employee.getDateOfJoining());
        dto.setDepartment(employee.getDepartment() != null ? employee.getDepartment().getName() : null);
        dto.setWorkLocation(employee.getWorkLocation() != null ? employee.getWorkLocation().getWorkLocationName() : null);
        dto.setDesignation(employee.getDesignation() != null ? employee.getDesignation().getName() : null);
        log.info("[{}] 🧑 Employee details mapped to DTO for employeeNumber: {}", method, employee.getEmployeeNumber());

        // earnings
        dto.setEarnings(ctc.getEarnings().stream()
                .map(e -> {
                    EmployeeEarningDTO eDto = new EmployeeEarningDTO();
                    eDto.setName(e.getEarning().getEarningName());
                    eDto.setAmount(e.getAmount());
                    eDto.setAmountInPercentage(e.getAmountInPercentage());
                    eDto.setOverrideAmount(e.getOverrideAmount());
                    log.info("[{}] ➕ Earning mapped: {} | Amount={} | %={}", method,
                            eDto.getName(), eDto.getAmount(), eDto.getAmountInPercentage());
                    return eDto;
                }).toList());

        // benefits
        dto.setBenefits(ctc.getBenefits().stream()
                .map(b -> {
                    EmployeeBenefitDTO bDto = new EmployeeBenefitDTO();
                    bDto.setName(b.getBenefit().getBenefitName());
                    bDto.setAmount(b.getAmount());
                    bDto.setAmountInPercentage(b.getAmountInPercentage());
                    log.info("[{}] 🎁 Benefit mapped: {} | Amount={} | %={}", method,
                            bDto.getName(), bDto.getAmount(), bDto.getAmountInPercentage());
                    return bDto;
                }).toList());

        // reimbursements
        dto.setReimbursements(ctc.getReimbursements().stream()
                .map(r -> {
                    EmployeeReimbursementDTO rDto = new EmployeeReimbursementDTO();
                    rDto.setName(r.getReimbursement().getReimbursementName());
                    rDto.setAmount(r.getAmount());
                    log.info("[{}] 💵 Reimbursement mapped: {} | Amount={}", method,
                            rDto.getName(), rDto.getAmount());
                    return rDto;
                }).toList());

        log.info("[{}] ✅ Salary structure DTO fully mapped for employeeNumber: {}", method, employee.getEmployeeNumber());
        return dto;
    }



    @Transactional
    @Override
    public EmployeeCTCDTO revise(String organizationId, EmployeeCtcRevisionDTO dto) {

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        // --- fetch employee ---
        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + dto.getEmployeeId()));


        boolean openRevisionExists =
                ctcStructureRepository
                        .existsByOrganization_OrganizationIdAndEmployee_IdAndAppliedInPayrunFalseAndRevisionStatusIsNotNullAndDeletedFalse(
                                organizationId,
                                employee.getId()
                        );

        if (openRevisionExists) {
            throw new IllegalStateException(
                    "An unprocessed salary revision already exists for this employee"
            );
        }



        if (dto.getCtc() == null) {
            throw new IllegalArgumentException("CTC cannot be null");
        }

        if (dto.getEffectiveDate() == null) {
            throw new IllegalArgumentException("Effective date cannot be null");
        }

        LocalDate today = LocalDate.now();

        // ✅ If revision is effective today/past → make it active immediately
        boolean shouldActivateNow = !dto.getEffectiveDate().isAfter(today);


        CtcStructure previousCtcEntity = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrueOrderByCreatedAtDesc(
                        organizationId,
                        employee.getId()
                )
                .orElse(null);

        Double previousCtc = previousCtcEntity != null ? previousCtcEntity.getAnnualCtc() : null;
        Double previousMonthlySalary = (previousCtcEntity != null && previousCtcEntity.getMonthlySalary() != null)
                ? previousCtcEntity.getMonthlySalary().doubleValue()
                : null;


        // ✅ If activating now → deactivate current active CTC(s)
//        if (shouldActivateNow) {
//            ctcStructureRepository.deactivateActiveCtcs(
//                    organizationId,
//                    employee.getId(),
//                    LocalDateTime.now()
//            );
//        }
//        if (dto.getPaymentMonth() == null || dto.getPaymentMonth().isBlank()) {
//            throw new IllegalArgumentException("Payment month cannot be null");
//        }


        // --- build NEW CTC entity (history entry) ---
        CtcStructure entity = new CtcStructure();
        entity.setAnnualCtc(dto.getCtc());
        entity.setOrganization(org);
        entity.setEmployee(employee);

        entity.setChangeInPercentage(dto.getChangeInPercent());

        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setPaymentMonth(dto.getPaymentMonth());

        entity.setUpdatedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        // ✅ Active only if effective date is today/past
       // entity.setActive(shouldActivateNow);
        entity.setActive(false);

        entity.setRevision(true);

        entity.setAppliedInPayrun(false);
        entity.setRevisionStatus(CtcRevisionStatus.APPROVED);


        // monthly salary
        if (dto.getMonthlySalary() != null) {
            entity.setMonthlySalary(BigDecimal.valueOf(dto.getMonthlySalary()));
        } else {
            entity.setMonthlySalary(
                    BigDecimal.valueOf(dto.getCtc())
                            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
            );
        }

        // ------------------- Earnings -------------------
        if (dto.getEarnings() != null && !dto.getEarnings().isEmpty()) {
            entity.setEarnings(dto.getEarnings().stream().map(eDTO -> {
                EmployeeEarning e = new EmployeeEarning();
                e.setEnabled(eDTO.getEnabled());
                e.setAmount(eDTO.getAmount());
                e.setAmountInPercentage(eDTO.getAmountInPercentage());
                e.setEditable(eDTO.getEditable());
                e.setIsVariable(eDTO.getIsVariable());
                e.setOverrideAmount(eDTO.getOverrideAmount());
                e.setEarningFrequency(eDTO.getEarningFrequency());

                if (eDTO.getCalculationBasis() != null) {
                    e.setCalculationBasis(
                            CalculationBasis.valueOf(eDTO.getCalculationBasis())
                    );
                }

                Earning earningMaster = earningRepository.findByEarningId(eDTO.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Earning not found: " + eDTO.getId()));

                e.setEarning(earningMaster);
                e.setOrganization(org);
                e.setCtcStructure(entity);
                return e;
            }).collect(Collectors.toList()));
        }

        // ------------------- Benefits -------------------
        if (dto.getBenefits() != null && !dto.getBenefits().isEmpty()) {
            entity.setBenefits(dto.getBenefits().stream().map(bDTO -> {
                EmployeeBenefit b = new EmployeeBenefit();
                b.setEnabled(bDTO.getEnabled());
                b.setAmount(bDTO.getAmount());

                Benefit benefitMaster = benefitRepository.findByBenefitIdAndOrganization(bDTO.getId(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Benefit not found for id: " + bDTO.getId()));

                b.setBenefit(benefitMaster);
                b.setOrganization(org);
                b.setCtcStructure(entity);
                return b;
            }).collect(Collectors.toList()));
        }

        // ------------------- Reimbursements -------------------
        if (dto.getReimbursements() != null && !dto.getReimbursements().isEmpty()) {
            entity.setReimbursements(dto.getReimbursements().stream().map(rDTO -> {
                EmployeeReimbursement r = new EmployeeReimbursement();
                r.setEnabled(rDTO.getEnabled());
                r.setAmount(rDTO.getAmount());
                r.setCarryForwardOption(rDTO.getCarryForwardOption());

                Reimbursement reimbursementMaster = reimbursementRepository.findByReimbursementId(rDTO.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Reimbursement not found: " + rDTO.getId()));

                r.setReimbursement(reimbursementMaster);
                r.setOrganization(org);
                r.setCtcStructure(entity);
                return r;
            }).collect(Collectors.toList()));
        }

        // ------------------- EPF Components -------------------
        if (dto.getEpfComponents() != null && !dto.getEpfComponents().isEmpty()) {
            List<CtcEpfComponent> epfList = dto.getEpfComponents().stream().map(epfDTO -> {
                CtcEpfComponent epf = new CtcEpfComponent();
                epf.setCtcStructure(entity);
                epf.setOrganization(org);
                epf.setComponentCode(epfDTO.getComponentCode());
                epf.setComponentLabel(epfDTO.getComponentLabel());
                epf.setPercentage(epfDTO.getPercentage());
                epf.setMonthlyAmount(epfDTO.getMonthlyAmount());
                epf.setAnnualAmount(epfDTO.getAnnualAmount());
                epf.setCalculationType(epfDTO.getCalculationType());
                return epf;
            }).collect(Collectors.toList());

            entity.setEpfComponents(epfList);
        }

        // ------------------- ESI Components -------------------
        if (dto.getEsiComponents() != null && !dto.getEsiComponents().isEmpty()) {
            List<CtcEsiComponent> esiList = dto.getEsiComponents().stream().map(esiDTO -> {
                CtcEsiComponent esi = new CtcEsiComponent();
                esi.setCtcStructure(entity);
                esi.setOrganization(org);
                esi.setComponentCode(esiDTO.getComponentCode());
                esi.setComponentLabel(esiDTO.getComponentLabel());
                esi.setPercentage(esiDTO.getPercentage());
                esi.setMonthlyAmount(esiDTO.getMonthlyAmount());
                esi.setAnnualAmount(esiDTO.getAnnualAmount());
                esi.setCalculationType(esiDTO.getCalculationType());
                return esi;
            }).collect(Collectors.toList());

            entity.setEsiComponents(esiList);
        }

        // ✅ save revision
        CtcStructure saved = ctcStructureRepository.save(entity);

        // =====================================================
        // RECALCULATE TDS AFTER SALARY REVISION
        // =====================================================

        recalculateTdsAfterSalaryChange(
                organizationId,
                dto.getEmployeeId(),
                dto.getEffectiveDate()
        );

        EmployeeCTCDTO response = mapToDto(saved);
        response.setPreviousCtc(previousCtc);
        response.setPreviousMonthlySalary(previousMonthlySalary);
        return response;

    }

    @Transactional
    @Override
    public EmployeeCTCDTO updateRevision(String organizationId, EmployeeCtcRevisionDTO dto) {

        if (dto.getRevisionId() == null && dto.getCtcStructureId() == null) {
            throw new IllegalArgumentException("Either revisionId or ctcStructureId is required for update revision");
        }

// ✅ Use one id
        Long idToUpdate = dto.getRevisionId() != null
                ? dto.getRevisionId()
                : dto.getCtcStructureId();


        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + dto.getEmployeeId()));

        CtcStructure entity = ctcStructureRepository
                .findByIdAndOrganization_OrganizationId(idToUpdate, organizationId)
                .orElseThrow(() -> new RuntimeException("Revision CTC not found: " + idToUpdate));


//        if (dto.getPaymentMonth() == null || dto.getPaymentMonth().isBlank()) {
//            throw new IllegalArgumentException("Payment month cannot be null");
//        }

        // ✅ Extra safety: ensure revision belongs to same employee
        if (entity.getEmployee() == null || !entity.getEmployee().getId().equals(employee.getId())) {
            throw new RuntimeException("This revision does not belong to the employee");
        }

        if (dto.getCtc() == null) {
            throw new IllegalArgumentException("CTC cannot be null");
        }
        if (dto.getEffectiveDate() == null) {
            throw new IllegalArgumentException("Effective date cannot be null");
        }

//        LocalDate today = LocalDate.now();
//        boolean shouldActivateNow = !dto.getEffectiveDate().isAfter(today);
//
//        // ✅ If this revision becomes active now, deactivate other active CTCs
//        if (shouldActivateNow) {
//            ctcStructureRepository.deactivateOtherActiveCtcs(
//                    organizationId,
//                    employee.getId(),
//                    entity.getId(),
//                    LocalDateTime.now()
//            );
//        }

        // ✅ Update main fields
        entity.setAnnualCtc(dto.getCtc());
        entity.setEffectiveDate(dto.getEffectiveDate());
        entity.setPaymentMonth(dto.getPaymentMonth());
        entity.setChangeInPercentage(dto.getChangeInPercent());
        entity.setUpdatedAt(LocalDateTime.now());

        // ✅ set active based on effective date logic
      //  entity.setActive(shouldActivateNow);

        entity.setActive(false);


        entity.setRevisionStatus(CtcRevisionStatus.APPROVED);

        // monthly salary
        if (dto.getMonthlySalary() != null) {
            entity.setMonthlySalary(BigDecimal.valueOf(dto.getMonthlySalary()));
        } else {
            entity.setMonthlySalary(
                    BigDecimal.valueOf(dto.getCtc())
                            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
            );
        }

        // ✅ Replace children safely (orphanRemoval=true required)

        // ---- Earnings ----
        entity.getEarnings().clear();
        if (dto.getEarnings() != null && !dto.getEarnings().isEmpty()) {
            List<EmployeeEarning> earnings = dto.getEarnings().stream().map(eDTO -> {
                EmployeeEarning e = new EmployeeEarning();
                e.setEnabled(eDTO.getEnabled());
                e.setAmount(eDTO.getAmount());
                e.setAmountInPercentage(eDTO.getAmountInPercentage());
                e.setEditable(eDTO.getEditable());
                e.setIsVariable(eDTO.getIsVariable());
                e.setOverrideAmount(eDTO.getOverrideAmount());
                e.setEarningFrequency(eDTO.getEarningFrequency());

                if (eDTO.getCalculationBasis() != null) {
                    e.setCalculationBasis(
                            CalculationBasis.valueOf(eDTO.getCalculationBasis())
                    );
                }

                Earning earningMaster = earningRepository.findByEarningId(eDTO.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Earning not found: " + eDTO.getId()));

                e.setEarning(earningMaster);
                e.setOrganization(org);
                e.setCtcStructure(entity);
                return e;
            }).collect(Collectors.toList());
            entity.getEarnings().addAll(earnings);
        }

        // ---- Benefits ----
        entity.getBenefits().clear();
        if (dto.getBenefits() != null && !dto.getBenefits().isEmpty()) {
            List<EmployeeBenefit> benefits = dto.getBenefits().stream().map(bDTO -> {
                EmployeeBenefit b = new EmployeeBenefit();
                b.setEnabled(bDTO.getEnabled());
                b.setAmount(bDTO.getAmount());

                Benefit benefitMaster = benefitRepository.findByBenefitIdAndOrganization(bDTO.getId(), org)
                        .orElseThrow(() -> new IllegalArgumentException("Benefit not found for id: " + bDTO.getId()));

                b.setBenefit(benefitMaster);
                b.setOrganization(org);
                b.setCtcStructure(entity);
                return b;
            }).collect(Collectors.toList());
            entity.getBenefits().addAll(benefits);
        }

        // ---- Reimbursements ----
        entity.getReimbursements().clear();
        if (dto.getReimbursements() != null && !dto.getReimbursements().isEmpty()) {
            List<EmployeeReimbursement> reimbursements = dto.getReimbursements().stream().map(rDTO -> {
                EmployeeReimbursement r = new EmployeeReimbursement();
                r.setEnabled(rDTO.getEnabled());
                r.setAmount(rDTO.getAmount());
                r.setCarryForwardOption(rDTO.getCarryForwardOption());

                Reimbursement reimbursementMaster = reimbursementRepository.findByReimbursementId(rDTO.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Reimbursement not found: " + rDTO.getId()));

                r.setReimbursement(reimbursementMaster);
                r.setOrganization(org);
                r.setCtcStructure(entity);
                return r;
            }).collect(Collectors.toList());
            entity.getReimbursements().addAll(reimbursements);
        }

        // ---- EPF ----
        entity.getEpfComponents().clear();
        if (dto.getEpfComponents() != null && !dto.getEpfComponents().isEmpty()) {
            List<CtcEpfComponent> epfList = dto.getEpfComponents().stream().map(epfDTO -> {
                CtcEpfComponent epf = new CtcEpfComponent();
                epf.setCtcStructure(entity);
                epf.setOrganization(org);
                epf.setComponentCode(epfDTO.getComponentCode());
                epf.setComponentLabel(epfDTO.getComponentLabel());
                epf.setPercentage(epfDTO.getPercentage());
                epf.setMonthlyAmount(epfDTO.getMonthlyAmount());
                epf.setAnnualAmount(epfDTO.getAnnualAmount());
                epf.setCalculationType(epfDTO.getCalculationType());
                return epf;
            }).collect(Collectors.toList());
            entity.getEpfComponents().addAll(epfList);
        }

        // ---- ESI ----
        entity.getEsiComponents().clear();
        if (dto.getEsiComponents() != null && !dto.getEsiComponents().isEmpty()) {
            List<CtcEsiComponent> esiList = dto.getEsiComponents().stream().map(esiDTO -> {
                CtcEsiComponent esi = new CtcEsiComponent();
                esi.setCtcStructure(entity);
                esi.setOrganization(org);
                esi.setComponentCode(esiDTO.getComponentCode());
                esi.setComponentLabel(esiDTO.getComponentLabel());
                esi.setPercentage(esiDTO.getPercentage());
                esi.setMonthlyAmount(esiDTO.getMonthlyAmount());
                esi.setAnnualAmount(esiDTO.getAnnualAmount());
                esi.setCalculationType(esiDTO.getCalculationType());
                return esi;
            }).collect(Collectors.toList());
            entity.getEsiComponents().addAll(esiList);
        }

        // ✅ Save updated revision
        // ✅ Fetch previous ACTIVE CTC for response (not the same record)
// Fetch previous CTC based on timeline, not active flag
        CtcStructure previousCtcEntity = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanOrderByEffectiveDateDesc(
                        organizationId,
                        employee.getId(),
                        entity.getEffectiveDate()
                )
                .orElse(null);

        Double previousCtc = previousCtcEntity != null
                ? previousCtcEntity.getAnnualCtc()
                : null;

        Double previousMonthlySalary =
                (previousCtcEntity != null && previousCtcEntity.getMonthlySalary() != null)
                        ? previousCtcEntity.getMonthlySalary().doubleValue()
                        : null;


         previousCtc = previousCtcEntity != null ? previousCtcEntity.getAnnualCtc() : null;
         previousMonthlySalary = (previousCtcEntity != null && previousCtcEntity.getMonthlySalary() != null)
                ? previousCtcEntity.getMonthlySalary().doubleValue()
                : null;

// ✅ Save updated revision
        CtcStructure saved = ctcStructureRepository.save(entity);

        // =====================================================
        // RECALCULATE TDS AFTER REVISION UPDATE
        // =====================================================
        recalculateTdsAfterSalaryChange(
                organizationId,
                dto.getEmployeeId(),
                entity.getEffectiveDate()
        );

// ✅ Response
        EmployeeCTCDTO response = mapToDto(saved);
        response.setPreviousCtc(previousCtc);
        response.setPreviousMonthlySalary(previousMonthlySalary);
        return response;

    }


    @Transactional
    @Override
    public void deleteRevision(String organizationId, Long revisionId, Long ctcStructureId) {

        if (revisionId == null && ctcStructureId == null) {
            throw new IllegalArgumentException("Either revisionId or ctcStructureId is required");
        }

        Long idToDelete = (revisionId != null) ? revisionId : ctcStructureId;

        CtcStructure entity = ctcStructureRepository
                .findByIdAndOrganization_OrganizationId(idToDelete, organizationId)
                .orElseThrow(() -> new RuntimeException("CTC revision not found: " + idToDelete));

        // ❌ Never allow deleting ACTIVE CTC
        if (Boolean.TRUE.equals(entity.getActive())) {
            throw new RuntimeException(
                    "Active CTC cannot be deleted. Please revise with a new CTC instead."
            );
        }

        // ❌ Never allow deleting the ONLY remaining CTC
        long remainingCtcs =
                ctcStructureRepository.countByOrganization_OrganizationIdAndEmployee_IdAndDeletedFalse(
                        organizationId,
                        entity.getEmployee().getId()
                );

        if (remainingCtcs <= 1) {
            throw new RuntimeException(
                    "Cannot delete the only CTC of an employee"
            );
        }

        // ✅ SOFT DELETE
        entity.setDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        ctcStructureRepository.save(entity);
    }



    @Transactional(readOnly = true)
    @Override
    public EmployeeCTCDTO getRevision(String organizationId, Long revisionId, Long ctcStructureId) {

        if (revisionId == null && ctcStructureId == null) {
            throw new IllegalArgumentException("Either revisionId or ctcStructureId is required");
        }

        Long idToFetch = (revisionId != null) ? revisionId : ctcStructureId;

        CtcStructure entity = ctcStructureRepository
                .findByIdAndOrganization_OrganizationId(idToFetch, organizationId)
                .orElseThrow(() -> new RuntimeException("CTC revision not found: " + idToFetch));

        // ✅ Fetch previous CTC (latest before this revision effectiveDate)
        CtcStructure prev = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanOrderByEffectiveDateDesc(
                        organizationId,
                        entity.getEmployee().getId(),
                        entity.getEffectiveDate()
                )
                .orElse(null);

        EmployeeCTCDTO response = mapToDto(entity);

        if (prev != null) {
            response.setPreviousCtc(prev.getAnnualCtc());
            response.setPreviousMonthlySalary(prev.getMonthlySalary() != null ? prev.getMonthlySalary().doubleValue() : null);
        }

        return response;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeCtcRevisionListDTO> getAllRevisedCtcs(
            String organizationId,
            int page,
            int size
    ) {

        Pageable pageable = PageRequest.of(page, size);

        Page<CtcStructure> revisedPage =
                ctcStructureRepository.findRevisedCtcs(organizationId, pageable);

        return revisedPage.map(this::mapToRevisionListDto);
    }




    private EmployeeCtcRevisionListDTO mapToRevisionListDto(CtcStructure entity) {

        EmployeeCtcRevisionListDTO dto = new EmployeeCtcRevisionListDTO();

        // Revision / CTC ID
        dto.setRevisionId(entity.getId());

        // Employee details
        dto.setEmployeeId(entity.getEmployee().getEmployeeId());
        dto.setEmployeeNumber(entity.getEmployee().getEmployeeNumber());
        dto.setEmployeeName(
                entity.getEmployee().getFirstName() + " " +
                        entity.getEmployee().getLastName()
        );

        // CTC details
        dto.setCtc(entity.getAnnualCtc());

        dto.setEffectiveDate(entity.getEffectiveDate());
        dto.setPaymentMonth(entity.getPaymentMonth());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setChangeInPercent(entity.getChangeInPercentage());
        dto.setRevisionStatus(
                entity.getRevisionStatus() != null
                        ? entity.getRevisionStatus().name()
                        : null
        );



        CtcStructure previous = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanAndDeletedFalseOrderByEffectiveDateDesc(
                        entity.getOrganization().getOrganizationId(),
                        entity.getEmployee().getId(),
                        entity.getEffectiveDate()
                )
                .orElse(null);

        dto.setPreviousCtc(previous != null ? previous.getAnnualCtc() : null);


        return dto;
    }


    private EmployeeCtcRevisionExportRowDTO mapToExportRow(CtcStructure entity) {

        EmployeeCtcRevisionExportRowDTO row = new EmployeeCtcRevisionExportRowDTO();

        // ================= Employee =================

        row.setEmployeeNumber(entity.getEmployee().getEmployeeNumber());

        String firstName = entity.getEmployee().getFirstName() != null
                ? entity.getEmployee().getFirstName()
                : "";

        String lastName = entity.getEmployee().getLastName() != null
                ? entity.getEmployee().getLastName()
                : "";

        row.setEmployeeName((firstName + " " + lastName).trim());

        // ================= Status =================

        if (entity.getRevisionStatus() != null) {
            row.setStatus(entity.getRevisionStatus().name());
        }

        // ================= CTC =================

        row.setRevisedCtc(entity.getAnnualCtc());
        row.setEffectiveFrom(entity.getEffectiveDate());
        row.setPayoutMonth(entity.getPaymentMonth());

        // ================= Previous CTC =================

        CtcStructure prev = ctcStructureRepository
                .findFirstByOrganization_OrganizationIdAndEmployee_IdAndEffectiveDateLessThanAndDeletedFalseOrderByEffectiveDateDesc(
                        entity.getOrganization().getOrganizationId(),
                        entity.getEmployee().getId(),
                        entity.getEffectiveDate()
                )
                .orElse(null);

        if (prev != null && !prev.getId().equals(entity.getId())) {
            row.setPreviousCtc(prev.getAnnualCtc());
        }

        // ================= Earnings =================

        if (entity.getEarnings() != null) {

            entity.getEarnings().forEach(e -> {

                if (e.getEarning() == null || e.getEarning().getEarningName() == null) {
                    return;
                }

                String name = e.getEarning().getEarningName();

                if ("Basic".equalsIgnoreCase(name)) {
                    row.setBasicName(name);
                    row.setBasicAmount(e.getAmount());
                }

                if ("House Rent Allowance".equalsIgnoreCase(name)) {
                    row.setHraName(name);
                    row.setHraAmount(e.getAmount());
                }

                if ("Conveyance Allowance".equalsIgnoreCase(name)) {
                    row.setConveyanceName(name);
                    row.setConveyanceAmount(e.getAmount());
                }

                if ("Fixed Allowance".equalsIgnoreCase(name)) {
                    row.setFixedAllowanceName(name);
                    row.setFixedAllowanceAmount(e.getAmount());
                }
            });
        }

        // ================= EPF =================

        if (entity.getEpfComponents() != null) {

            BigDecimal epfEmployerTotal = BigDecimal.ZERO;

            for (CtcEpfComponent epf : entity.getEpfComponents()) {
                if (epf.getAnnualAmount() != null) {
                    epfEmployerTotal =
                            epfEmployerTotal.add(BigDecimal.valueOf(epf.getAnnualAmount().doubleValue()));
                }
            }

            row.setEpfEmployerContribution(
                    epfEmployerTotal.compareTo(BigDecimal.ZERO) == 0 ? null : epfEmployerTotal
            );
        }

        // ================= ESI =================

        if (entity.getEsiComponents() != null) {

            BigDecimal esiEmployerTotal = BigDecimal.ZERO;

            for (CtcEsiComponent esi : entity.getEsiComponents()) {
                if (esi.getAnnualAmount() != null) {
                    esiEmployerTotal =
                            esiEmployerTotal.add(BigDecimal.valueOf(esi.getAnnualAmount().doubleValue()));
                }
            }

            row.setEsiEmployerContribution(
                    esiEmployerTotal.compareTo(BigDecimal.ZERO) == 0 ? null : esiEmployerTotal
            );
        }

        return row;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportRevisedCtcs(String organizationId) {

        List<CtcStructure> revisions =
                ctcStructureRepository
                        .findAllByOrganization_OrganizationIdAndRevisionStatusIsNotNullAndDeletedFalseOrderByCreatedAtDesc(
                                organizationId
                        );

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Salary Revisions");

            int rowIdx = 0;

            // =========================
            // Header
            // =========================
            Row header = sheet.createRow(rowIdx++);

            int col = 0;
            header.createCell(col++).setCellValue("Employee Number");
            header.createCell(col++).setCellValue("Employee Name");
            header.createCell(col++).setCellValue("Status");

            header.createCell(col++).setCellValue("CTC (per annum)");
            header.createCell(col++).setCellValue("Revised CTC (per annum)");

            header.createCell(col++).setCellValue("Effective From");
            header.createCell(col++).setCellValue("Payout Month");

            header.createCell(col++).setCellValue("Basic");
            header.createCell(col++).setCellValue("Basic Amount");

            header.createCell(col++).setCellValue("House Rent Allowance");
            header.createCell(col++).setCellValue("House Rent Allowance Amount");

            header.createCell(col++).setCellValue("Conveyance Allowance");
            header.createCell(col++).setCellValue("Conveyance Allowance Amount");

            header.createCell(col++).setCellValue("Fixed Allowance");
            header.createCell(col++).setCellValue("Fixed Allowance Amount");

            header.createCell(col++).setCellValue("EPF Employer Contribution");
            header.createCell(col++).setCellValue("ESI Employer Contribution");

            // =========================
            // Data
            // =========================
            for (CtcStructure entity : revisions) {

                EmployeeCtcRevisionExportRowDTO rowDto = mapToExportRow(entity);

                Row row = sheet.createRow(rowIdx++);
                int c = 0;

                row.createCell(c++).setCellValue(
                        rowDto.getEmployeeNumber() != null ? rowDto.getEmployeeNumber() : ""
                );

                row.createCell(c++).setCellValue(
                        rowDto.getEmployeeName() != null ? rowDto.getEmployeeName() : ""
                );

                row.createCell(c++).setCellValue(
                        rowDto.getStatus() != null ? rowDto.getStatus() : ""
                );

                // Previous CTC
                if (rowDto.getPreviousCtc() != null)
                    row.createCell(c++).setCellValue(rowDto.getPreviousCtc());
                else
                    row.createCell(c++).setBlank();

                // Revised CTC
                if (rowDto.getRevisedCtc() != null)
                    row.createCell(c++).setCellValue(rowDto.getRevisedCtc());
                else
                    row.createCell(c++).setBlank();

                // Effective from
                row.createCell(c++).setCellValue(
                        rowDto.getEffectiveFrom() != null
                                ? rowDto.getEffectiveFrom().toString()
                                : ""
                );

                // Payout month
                row.createCell(c++).setCellValue(
                        rowDto.getPayoutMonth() != null
                                ? rowDto.getPayoutMonth()
                                : ""
                );

                // Basic
                row.createCell(c++).setCellValue(
                        rowDto.getBasicName() != null ? rowDto.getBasicName() : ""
                );

                if (rowDto.getBasicAmount() != null)
                    row.createCell(c++).setCellValue(rowDto.getBasicAmount().doubleValue());
                else
                    row.createCell(c++).setBlank();

                // HRA
                row.createCell(c++).setCellValue(
                        rowDto.getHraName() != null ? rowDto.getHraName() : ""
                );

                if (rowDto.getHraAmount() != null)
                    row.createCell(c++).setCellValue(rowDto.getHraAmount().doubleValue());
                else
                    row.createCell(c++).setBlank();

                // Conveyance
                row.createCell(c++).setCellValue(
                        rowDto.getConveyanceName() != null ? rowDto.getConveyanceName() : ""
                );

                if (rowDto.getConveyanceAmount() != null)
                    row.createCell(c++).setCellValue(rowDto.getConveyanceAmount().doubleValue());
                else
                    row.createCell(c++).setBlank();

                // Fixed allowance
                row.createCell(c++).setCellValue(
                        rowDto.getFixedAllowanceName() != null ? rowDto.getFixedAllowanceName() : ""
                );

                if (rowDto.getFixedAllowanceAmount() != null)
                    row.createCell(c++).setCellValue(rowDto.getFixedAllowanceAmount().doubleValue());
                else
                    row.createCell(c++).setBlank();

                // EPF
                if (rowDto.getEpfEmployerContribution() != null)
                    row.createCell(c++).setCellValue(rowDto.getEpfEmployerContribution().doubleValue());
                else
                    row.createCell(c++).setBlank();

                // ESI
                if (rowDto.getEsiEmployerContribution() != null)
                    row.createCell(c++).setCellValue(rowDto.getEsiEmployerContribution().doubleValue());
                else
                    row.createCell(c++).setBlank();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Failed to export revised CTCs", e);
        }
    }

       private void recalculateTdsAfterSalaryChange(
        String organizationId,
        String employeeId,
        LocalDate effectiveDate
) {
    int fiscalYear =
            (effectiveDate.getMonthValue() >= 4)
                    ? effectiveDate.getYear() + 1
                    : effectiveDate.getYear();

    log.info(
            "[CTC] 🔁 Triggering TDS recalculation | empId={} | fy={}",
            employeeId,
            fiscalYear
    );

    tdsSalaryRevisionService.handleSalaryRevisionTds(
            organizationId,
            employeeId,
            fiscalYear
    );
}




    @Transactional
    @Override
    public void processLaterRevisions(String organizationId,
                                      ProcessLaterRevisionDTO dto) {

        if (dto.getRevisionIds() == null || dto.getRevisionIds().isEmpty()) {
            throw new IllegalArgumentException("revisionIds are required");
        }

        if (dto.getEffectiveDate() == null) {
            throw new IllegalArgumentException("effectiveDate is required");
        }

        List<CtcStructure> revisions =
                ctcStructureRepository
                        .findAllByIdInAndOrganization_OrganizationId(
                                dto.getRevisionIds(),
                                organizationId
                        );

        if (revisions.isEmpty()) {
            throw new RuntimeException("No revisions found");
        }

        for (CtcStructure entity : revisions) {

            // ✅ Only revised rows can be processed later
            if (entity.getRevisionStatus() == null) {
                throw new RuntimeException(
                        "Process later allowed only for revised CTC entries. Invalid id: " + entity.getId()
                );
            }

            // ❌ Do not allow process later for active revision
            if (Boolean.TRUE.equals(entity.getActive())) {
                throw new RuntimeException(
                        "Active revision cannot be processed later. Id: " + entity.getId()
                );
            }

            // ✅ set new effective date
            entity.setEffectiveDate(dto.getEffectiveDate());

            // ✅ mark as PENDING
            entity.setRevisionStatus(CtcRevisionStatus.PENDING);

            entity.setUpdatedAt(LocalDateTime.now());
        }

        ctcStructureRepository.saveAll(revisions);
    }

    }


