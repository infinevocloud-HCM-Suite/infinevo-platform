package com.itsdev.payroll.serviceimpl.employeeTDS;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.NewTaxCalculationResult;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.OldTaxCalculationResult;
import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.employee.CtcStructure;
import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.TdsSourceType;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.repository.claimsanddeclarations.IncomeTaxDeclarationRepository;
import com.itsdev.payroll.repository.claimsanddeclarations.ProofOfInvestmentRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.employeeTDS.EmployeeTdsRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.poi.EmployeeProofOfInvestmentRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.employeeTDS.DefaultTdsCreationService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.NewTaxCalculationService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.OldTaxCalculationService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class DefaultTdsCreationServiceImpl implements DefaultTdsCreationService {

    private final EmployeeTdsRepository employeeTdsRepository;
    private final IncomeTaxDeclarationRepository incomeTaxDeclarationRepository;
    private final ProofOfInvestmentRepository proofOfInvestmentRepository;
    private final OrganizationRepository organizationRepository;
    private final NewTaxCalculationService newTaxCalculationService;
    private final OldTaxCalculationService oldTaxCalculationService;
    private final EmployeeProofOfInvestmentRepository employeeProofOfInvestmentRepository;

    private final CtcStructureRepository ctcStructureRepository;



    public DefaultTdsCreationServiceImpl(
            EmployeeTdsRepository employeeTdsRepository,
            IncomeTaxDeclarationRepository incomeTaxDeclarationRepository,
            ProofOfInvestmentRepository proofOfInvestmentRepository,
            EmployeeProofOfInvestmentRepository employeeProofOfInvestmentRepository,
            OrganizationRepository organizationRepository,
            NewTaxCalculationService newTaxCalculationService,
            CtcStructureRepository ctcStructureRepository,
            @Lazy OldTaxCalculationService oldTaxCalculationService
    ) {
        this.employeeTdsRepository = employeeTdsRepository;
        this.incomeTaxDeclarationRepository = incomeTaxDeclarationRepository;
        this.proofOfInvestmentRepository = proofOfInvestmentRepository;
        this.organizationRepository = organizationRepository;
        this.newTaxCalculationService = newTaxCalculationService;
        this.oldTaxCalculationService = oldTaxCalculationService;
        this.ctcStructureRepository = ctcStructureRepository;
        this.employeeProofOfInvestmentRepository = employeeProofOfInvestmentRepository;
    }

    @Override
        public EmployeeTds createDefaultTdsIfNotExists(
                String organizationId,
                String employeeId,
                Integer fiscalYear
        ) {
        final String method = "createDefaultTdsIfNotExists";

        log.info("[{}] ▶ START | orgId={} empId={} fy={}", method, organizationId, employeeId, fiscalYear);

        /*
        * 1️⃣ CHECK EXISTING ACTIVE TDS
        */
        Optional<EmployeeTds> existing =
                employeeTdsRepository.findActiveByOrganizationAndEmployeeAndFiscalYear(
                        organizationId,
                        employeeId,
                        fiscalYear
                );

        if (existing.isPresent()) {
                log.info("[{}] 📌 Active TDS already exists | tdsId={}", method, existing.get().getId());
                return existing.get();
        }

        /*
        * 2️⃣ FETCH ORGANIZATION (SAFE)
        */
        Optional<Organization> orgOpt = organizationRepository.findByOrganizationId(organizationId);
        if (orgOpt.isEmpty()) {
                log.warn("[{}] ⚠ Organization not found → skipping TDS creation", method);
                return null;
        }
        Organization organization = orgOpt.get();

        /*
        * 3️⃣ FETCH TAX SETTINGS (SAFE)
        */
        Optional<IncomeTaxDeclaration> itOpt = incomeTaxDeclarationRepository.findByOrganization(organization);
        if (itOpt.isEmpty()) {
                log.warn("[{}] ⚠ IncomeTaxDeclaration missing → skipping TDS creation", method);
                return null;
        }

        String regime = itOpt.get().getDefaultTaxRegime();
        if (regime == null || regime.isBlank()) {
                regime = "NEW"; // fallback allowed
        }

        log.info("[{}] 📊 Default tax regime = {}", method, regime);

        /*
        * 4️⃣ CALCULATE TAX (SAFE)
        */
        BigDecimal annualGrossSalary;
        BigDecimal annualTaxableIncome;
        BigDecimal finalAnnualTax;

        TdsSourceType sourceType = TdsSourceType.DEFAULT_REGIME;


        try {
                if ("NEW".equalsIgnoreCase(regime)) {

                    NewTaxCalculationResult result =
                            newTaxCalculationService.calculateNewTax(organizationId, employeeId, fiscalYear);

                    if (result == null) {
                        log.warn("[{}] ⚠ New tax result null → skipping TDS", method);
                        return null;
                    }

                    annualGrossSalary = result.getGrossIncome();
                    annualTaxableIncome = result.getTaxableIncome();
                    finalAnnualTax = result.getTaxPayable();

//                } else if ("OLD".equalsIgnoreCase(regime)) {
//
//                OldTaxCalculationResult result =
//                        oldTaxCalculationService.calculateOldTax(organizationId, employeeId, fiscalYear);
//
//                if (result == null) {
//                        log.warn("[{}] ⚠ Old tax result null → skipping TDS", method);
//                        return null;
//                }
//
//                annualGrossSalary = result.getGrossIncome();
//                annualTaxableIncome = result.getTaxableIncome();
//                finalAnnualTax = result.getTaxPayable();

                }
                else if ("OLD".equalsIgnoreCase(regime)) {

                    OldTaxCalculationResult result;

                    boolean hasRevisionInFy =
                            hasSalaryRevisionInCurrentFY(
                                    organizationId,
                                    employeeId,
                                    fiscalYear
                            );

                    if (hasRevisionInFy) {

                        log.info("[{}] OLD DEFAULT (Salary Revision detected in FY)", method);


                        result =
                                oldTaxCalculationService
                                        .calculateOldTaxWithRevisedSalary(
                                                organizationId,
                                                employeeId,
                                                fiscalYear
                                        );

                        sourceType = TdsSourceType.DEFAULT_REGIME;

                    } else {

                        log.info("[{}] OLD DEFAULT (Single CTC – No Revision in FY)", method);


                        result =
                                oldTaxCalculationService
                                        .calculateOldTax(
                                                organizationId,
                                                employeeId,
                                                fiscalYear
                                        );

                        sourceType = TdsSourceType.DEFAULT_REGIME;
                    }

                    if (result == null) {
                        log.warn("[{}] ⚠ Old tax result null → skipping TDS", method);
                        return null;
                    }

                    annualGrossSalary = result.getGrossIncome();
                    annualTaxableIncome = result.getTaxableIncome();
                    finalAnnualTax = result.getTaxPayable();
                }




                else {
                log.warn("[{}] ⚠ Unsupported regime={} → skipping TDS", method, regime);
                return null;
                }

        } catch (Exception ex) {
                log.error("[{}] ❌ Tax calculation failed → skipping TDS | error={}", method, ex.getMessage());
                return null;
        }

        /*
        * 5️⃣ FETCH POI SETTINGS (SAFE)
        */
        Optional<ProofOfInvestment> poiOpt = proofOfInvestmentRepository.findByOrganization(organization);
        if (poiOpt.isEmpty()) {
                log.warn("[{}] ⚠ POI settings missing → skipping TDS creation", method);
                return null;
        }

        String effectiveMonth = poiOpt.get().getMonthToConsiderPoi();
        if (effectiveMonth == null || effectiveMonth.isBlank()) {
                log.warn("[{}] ⚠ POI effective month missing → skipping TDS creation", method);
                return null;
        }

        /*
        * 6️⃣ CREATE EMPLOYEE_TDS SNAPSHOT
        */
        EmployeeTds newTds = new EmployeeTds();

        newTds.setOrganizationId(organizationId);
        newTds.setEmployeeId(employeeId);
        newTds.setFiscalYear(fiscalYear);
        newTds.setTdsSourceType(TdsSourceType.DEFAULT_REGIME);

        newTds.setTaxRegime(regime);

        newTds.setAnnualGrossSalary(annualGrossSalary);
        newTds.setAnnualTaxableIncome(annualTaxableIncome);
        newTds.setFinalAnnualTax(finalAnnualTax);

        newTds.setEffectiveFromMonth(effectiveMonth);
        newTds.setIsActive(true);

        EmployeeTds saved = employeeTdsRepository.save(newTds);

        log.info("[{}] ✅ Default EmployeeTds created | id={} | finalTax={}", method, saved.getId(), saved.getFinalAnnualTax());

        return saved;
        }



    private boolean hasSalaryRevisionInCurrentFY(
            String organizationId,
            String employeeId,
            Integer financialYear
    ) {

        LocalDate fyStart = LocalDate.of(financialYear, 4, 1);
        LocalDate fyEnd   = LocalDate.of(financialYear + 1, 3, 31);

        List<CtcStructure> ctcList =
                ctcStructureRepository
                        .findByOrganization_OrganizationIdAndEmployee_EmployeeId(
                                organizationId,
                                employeeId
                        );

        if (ctcList == null || ctcList.isEmpty()) {
            return false;
        }

        long countInFy = ctcList.stream()
                .filter(ctc -> {
                    LocalDate effectiveDate = ctc.getEffectiveDate();
                    if (effectiveDate == null) return false;

                    return !effectiveDate.isBefore(fyStart)
                            && !effectiveDate.isAfter(fyEnd);
                })
                .count();

        return countInFy > 1;
    }



}
