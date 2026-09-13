package com.itsdev.payroll.serviceimpl.employeeTDS;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.NewTaxCalculationResult;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.OldTaxCalculationResult;
import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.enumeration.TdsSourceType;
import com.itsdev.payroll.repository.employeeTDS.EmployeeTdsRepository;
import com.itsdev.payroll.service.claimsanddeclarations.POISettingsService;
import com.itsdev.payroll.service.employeeTDS.TdsSalaryRevisionService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.NewTaxCalculationService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.OldTaxCalculationService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@Transactional
public class TdsSalaryRevisionServiceImpl implements TdsSalaryRevisionService {

    private final EmployeeTdsRepository employeeTdsRepository;
    private final NewTaxCalculationService newTaxCalculationService;
    @Lazy private final OldTaxCalculationService oldTaxCalculationService;
    private final POISettingsService poiSettingsService;

    public TdsSalaryRevisionServiceImpl(
            EmployeeTdsRepository employeeTdsRepository,
            NewTaxCalculationService newTaxCalculationService,
            @Lazy OldTaxCalculationService oldTaxCalculationService,
            POISettingsService poiSettingsService
    ) {
        this.employeeTdsRepository = employeeTdsRepository;
        this.newTaxCalculationService = newTaxCalculationService;
        this.oldTaxCalculationService = oldTaxCalculationService;
        this.poiSettingsService = poiSettingsService;
    }

    /**
     * Recalculate TDS after salary revision.
     * - Detects tax regime from existing employee_tds
     * - Recalculates tax using revised salary
     * - Deactivates old TDS
     * - Inserts new employee_tds with SALARY_REVISION source
     */
    @Override
    public void handleSalaryRevisionTds(
            String organizationId,
            String employeeId,
            Integer fiscalYear
    ) {

        final String method = "handleSalaryRevisionTds";
        log.info(
                "[{}] ▶ START | orgId={} empId={} fy={}",
                method, organizationId, employeeId, fiscalYear
        );

        /*
         * =====================================================
         * 1️⃣ FETCH EXISTING ACTIVE TDS
         * =====================================================
         */
        EmployeeTds existingTds =
                employeeTdsRepository
                        .findActiveByOrganizationAndEmployeeAndFiscalYear(
                    organizationId,
                    employeeId,
                    fiscalYear
            )
                        .orElse(null);

        if (existingTds == null) {
        log.warn(
                "[{}] ⚠ No existing TDS found. Skipping TDS recalculation | empId={} fy={}",
                method, employeeId, fiscalYear
        );
        return; //EXIT WITHOUT ERROR → salary revision continues
        }


        String taxRegime = existingTds.getTaxRegime();

        if (taxRegime == null) {
        log.warn(
                "[{}] ⚠ Tax regime missing in employee_tds. Skipping TDS recalculation | empId={} fy={}",
                method, employeeId, fiscalYear
        );
        return;
        }

        log.info(
                "[{}] 📌 Existing TDS found | regime={} | source={}",
                method, taxRegime, existingTds.getTdsSourceType()
        );

        /*
         * =====================================================
         * 2️⃣ RECALCULATE TAX USING REVISED SALARY
         * =====================================================
         */
        BigDecimal annualGrossSalary;
        BigDecimal annualTaxableIncome;
        BigDecimal finalAnnualTax;

        if ("NEW".equalsIgnoreCase(taxRegime)) {

            NewTaxCalculationResult result =
                    newTaxCalculationService.calculateNewTaxWithRevisedSalary(
                            organizationId,
                            employeeId,
                            fiscalYear
                    );

            annualGrossSalary = result.getGrossIncome();
            annualTaxableIncome = result.getTaxableIncome();
            finalAnnualTax = result.getTaxPayable();

        }   else if ("OLD".equalsIgnoreCase(taxRegime)) {

            OldTaxCalculationResult result;

            if (existingTds.getTdsSourceType() == TdsSourceType.POI_BASED) {

                log.info("[{}] OLD + POI_BASED → revised salary + POI calculation", method);

                result =
                        oldTaxCalculationService
                                .calculateOldTaxWithRevisedSalaryAndApprovedPOI(
                                        organizationId,
                                        employeeId,
                                        fiscalYear
                                );

            } else {

                log.info("[{}] OLD (NON-POI) → revised salary calculation", method);

                result =
                        oldTaxCalculationService
                                .calculateOldTaxWithRevisedSalary(
                                        organizationId,
                                        employeeId,
                                        fiscalYear
                                );
            }

            annualGrossSalary = result.getGrossIncome();
            annualTaxableIncome = result.getTaxableIncome();
            finalAnnualTax = result.getTaxPayable();


        } else {
            throw new RuntimeException("Unsupported tax regime: " + taxRegime);
        }

        log.info(
                "[{}] 🧮 Recalculated Tax | gross={} taxable={} tax={}",
                method, annualGrossSalary, annualTaxableIncome, finalAnnualTax
        );

        /*
         * =====================================================
         * 3️⃣ DEACTIVATE OLD TDS
         * =====================================================
         */
        existingTds.setIsActive(false);
        employeeTdsRepository.save(existingTds);

        /*
         * =====================================================
         * 4️⃣ FETCH EFFECTIVE MONTH (ORG LEVEL)
         * =====================================================
         */
        ProofOfInvestment poiSettings =
                poiSettingsService.getSettings(organizationId);

        String effectiveMonth = poiSettings.getMonthToConsiderPoi();

        if (effectiveMonth == null || effectiveMonth.isBlank()) {
            throw new RuntimeException("POI monthToConsiderPoi not configured");
        }

        /*
         * =====================================================
         * 5️⃣ INSERT NEW EMPLOYEE_TDS (SALARY_REVISION)
         * =====================================================
         */
        EmployeeTds newTds = new EmployeeTds();

        newTds.setEmployeeId(employeeId);
        newTds.setOrganizationId(organizationId);
        newTds.setFiscalYear(fiscalYear);

        newTds.setTdsSourceType(TdsSourceType.SALARY_REVISION);
        newTds.setTaxRegime(taxRegime);

        newTds.setAnnualGrossSalary(annualGrossSalary);
        newTds.setAnnualTaxableIncome(annualTaxableIncome);
        newTds.setFinalAnnualTax(finalAnnualTax);

        // 🔑 Month stored as STRING (same as POI logic)
        newTds.setEffectiveFromMonth(effectiveMonth);

        newTds.setIsActive(true);

        employeeTdsRepository.save(newTds);

        log.info(
                "[{}] ✅ TDS updated after salary revision | newTdsId={} | regime={} | effectiveMonth={}",
                method, newTds.getId(), taxRegime, effectiveMonth
        );
    }

}
