package com.itsdev.payroll.serviceimpl.dashboard;

import com.itsdev.payroll.dto.dashboard.DashboardResponse;
import com.itsdev.payroll.dto.dashboard.StatutorySummaryDTO;
import com.itsdev.payroll.dto.dashboard.TdsSummaryDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.employee.EmployeeBankDetail;
import com.itsdev.payroll.entity.employee.EmployeeEarning;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employee.CtcStructureRepository;
import com.itsdev.payroll.repository.employee.EmployeeBankDetailRepository;
import com.itsdev.payroll.repository.employee.EarningRepository;
import com.itsdev.payroll.repository.payruns.EmployeePayRunRepository;
import com.itsdev.payroll.repository.payruns.PayRunRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;

import com.itsdev.payroll.repository.organization.IncomeTaxDetailsRepository;
import com.itsdev.payroll.service.dashboard.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.itsdev.payroll.repository.statutorycomponents.EpfRepository;
import com.itsdev.payroll.repository.statutorycomponents.EsiRepository;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final BasicDetailsRepository basicDetailsRepository;
    private final PayRunRepository payRunRepository;
    private final EmployeePayRunRepository employeePayRunRepository;
    private final EmployeeBankDetailRepository employeeBankDetailRepository;
    private final EarningRepository employeeEarningRepository;
    private final EpfRepository epfRepository;
    private final EsiRepository esiRepository;
    private final IncomeTaxDetailsRepository incomeTaxDetailsRepository;
    private final OrganizationRepository organizationRepository;
    private final CtcStructureRepository ctcStructureRepository;



    @Autowired
    public DashboardServiceImpl(BasicDetailsRepository basicDetailsRepository,
                                PayRunRepository payRunRepository,
                                EmployeePayRunRepository employeePayRunRepository,
                                EmployeeBankDetailRepository employeeBankDetailRepository,
                                EarningRepository employeeEarningRepository,
                                EpfRepository epfRepository,
                                EsiRepository esiRepository,
                                IncomeTaxDetailsRepository incomeTaxDetailsRepository,
                                OrganizationRepository organizationRepository,
                                CtcStructureRepository ctcStructureRepository) {
        this.basicDetailsRepository = basicDetailsRepository;
        this.payRunRepository = payRunRepository;
        this.employeePayRunRepository = employeePayRunRepository;
        this.employeeBankDetailRepository = employeeBankDetailRepository;
        this.employeeEarningRepository = employeeEarningRepository;
        this.epfRepository = epfRepository;
        this.esiRepository = esiRepository;
        this.incomeTaxDetailsRepository = incomeTaxDetailsRepository;
        this.organizationRepository = organizationRepository;
        this.ctcStructureRepository = ctcStructureRepository;// ✅ add this line
    }


    @Override
    public DashboardResponse getDashboardSummary(String organizationId) {

        DashboardResponse resp = new DashboardResponse();

        // ------------------ 1. Employee Summary ------------------
        DashboardResponse.EmployeeSummary empSummary = new DashboardResponse.EmployeeSummary();

        List<BasicDetails> allActive = basicDetailsRepository
                .findByOrganization_OrganizationIdAndIsDeletedFalse(organizationId)
                .stream()
                .filter(b -> b.getEmployeeStatus() != null && b.getEmployeeStatus().equalsIgnoreCase("active"))
                .collect(Collectors.toList());

        empSummary.setActiveEmployees(allActive.size());

        int unfinished = 0;
        for (BasicDetails b : allActive) {
            boolean unfinishedFlag = false;
            // ✅ Check ACTIVE CTC instead of salaryStructure (OneToOne removed)
            boolean hasActiveCtc = !ctcStructureRepository
                    .findByOrganization_OrganizationIdAndEmployee_IdAndIsActiveTrue(
                            organizationId,
                            b.getId()
                    ).isEmpty();

            if (!hasActiveCtc) {
                unfinishedFlag = true;
            } else {
                Optional<EmployeeBankDetail> bdOpt =
                        employeeBankDetailRepository.findByEmployee_IdAndOrganization_OrganizationId(
                                b.getId(),
                                organizationId
                        );

                if (bdOpt.isEmpty()) {
                    unfinishedFlag = true;
                }
            }

            if (unfinishedFlag) unfinished++;
        }

        empSummary.setUnfinishedEmployees(unfinished);
        resp.setEmployeeSummary(empSummary);

        // ------------------ 2. PayRun Summary ------------------

        // ✅ Fetch Organization first
        com.itsdev.payroll.entity.organization.Organization org =
                organizationRepository.findByOrganizationId(organizationId)
                        .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        // ✅ Use existing repo method
        List<PayRun> all = payRunRepository.findByOrganization(org);

        List<PayRun> activeDrafts = all.stream()
                .filter(p -> p.getStatus() == com.itsdev.payroll.enumeration.payruns.PayRunStatus.DRAFT
                        || p.getStatus() == com.itsdev.payroll.enumeration.payruns.PayRunStatus.READY)
                .sorted(Comparator.comparing(PayRun::getPayDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());


        List<DashboardResponse.PayRunSummary> prSummaries = new ArrayList<>();
        for (PayRun p : activeDrafts) {
            DashboardResponse.PayRunSummary ps = new DashboardResponse.PayRunSummary();
            ps.setPayrunId(p.getPayrunId());
            ps.setProcessingPeriod(p.getProcessingPeriod());
            ps.setPayDate(p.getPayDate() != null ? p.getPayDate().toString() : null);
            ps.setStatus(p.getStatus() != null ? p.getStatus().name().toLowerCase() : null);
            ps.setPaymentDue(Boolean.TRUE.equals(p.getPaymentDue())); // ✅ fixed getter name
            ps.setPayrollTotal(p.getPayrollTotal());
            ps.setNoOfEmployees(p.getNoOfEmployees() != null ? p.getNoOfEmployees() : 0);
            prSummaries.add(ps);
        }
        resp.setPayrollRuns(prSummaries);

        // ------------------ 3. Bank Details Check ------------------
        boolean isMismatch = false;
        boolean hasDirectDeposit = false;

        List<EmployeeBankDetail> bankDetails = employeeBankDetailRepository.findAllByOrganization_OrganizationId(organizationId);
        Map<Long, EmployeeBankDetail> bankMap = bankDetails.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(bd -> bd.getEmployee().getId(), bd -> bd, (a, b) -> a));

        for (BasicDetails b : allActive) {
            EmployeeBankDetail bd = bankMap.get(b.getId());
            boolean wantsBank = bd != null && "banktransfer".equalsIgnoreCase(bd.getPaymentMode());
            if (wantsBank) {
                if (bd.getBankAccountNumber() != null && bd.getIfscCode() != null &&
                        !bd.getBankAccountNumber().trim().isEmpty() && !bd.getIfscCode().trim().isEmpty()) {
                    hasDirectDeposit = true;
                } else {
                    isMismatch = true;
                }
            }
        }

        resp.setBankAccountsMismatchExist(isMismatch);
        resp.setHasDirectDepositPayments(hasDirectDeposit);

        // ------------------ 4. Monthly Payroll Summary ------------------
        LocalDate today = LocalDate.now();
        int year = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate fiscalStart = LocalDate.of(year, 4, 1);
        LocalDate fiscalEnd = fiscalStart.plusYears(1).minusDays(1);

        List<Object[]> monthRows = employeePayRunRepository.findMonthWisePayrollSummary(organizationId, fiscalStart, fiscalEnd);
        DashboardResponse.PayrollSummary payrollSummary = new DashboardResponse.PayrollSummary();
        List<DashboardResponse.MonthDetail> monthDetails = new ArrayList<>();

        if (monthRows != null) {
            for (Object[] row : monthRows) {
                String monthName = row[0] != null ? row[0].toString() : null;
                BigDecimal totalEarnings = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
                BigDecimal totalTaxes = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
                BigDecimal totalNetPay = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
                BigDecimal totalDeductions = row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO;

                DashboardResponse.MonthDetail md = new DashboardResponse.MonthDetail();
                md.setMonthName(monthName);
                md.setTotalEarnings(totalEarnings);
                md.setTotalTaxes(totalTaxes);
                md.setTotalNetPay(totalNetPay);
                md.setTotalDeductions(totalDeductions);
                monthDetails.add(md);
            }
        }

        payrollSummary.setMonthDetails(monthDetails);
        resp.setPayrollSummary(payrollSummary);

        return resp;
    }


    @Override
    public StatutorySummaryDTO getStatutorySummary(String organizationId, String type, LocalDate fromDate, LocalDate toDate) {
        StatutorySummaryDTO dto = new StatutorySummaryDTO();
        dto.setType(type != null ? type.toUpperCase() : "EPF");

        if (fromDate == null || toDate == null) {
            LocalDate today = LocalDate.now();
            int year = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
            fromDate = LocalDate.of(year, 4, 1);
            toDate = fromDate.plusYears(1).minusDays(1);
        }

        List<EmployeeEarning> earnings = employeeEarningRepository.findAll();
        BigDecimal employeeSum = BigDecimal.ZERO;

        for (EmployeeEarning ee : earnings) {
            if (ee == null || ee.getOrganization() == null ||
                    !organizationId.equals(ee.getOrganization().getOrganizationId()) ||
                    ee.getEarning() == null) continue;

            if ("EPF".equalsIgnoreCase(type)) {
                if (Boolean.TRUE.equals(ee.getEarning().getIsIncludedInEpf())) {
                    employeeSum = employeeSum.add(ee.getAmount() != null ? BigDecimal.valueOf(ee.getAmount()) : BigDecimal.ZERO);
                }
            } else if ("ESI".equalsIgnoreCase(type)) {
                if (Boolean.TRUE.equals(ee.getEarning().getIsIncludedInEsi())) {
                    employeeSum = employeeSum.add(ee.getAmount() != null ? BigDecimal.valueOf(ee.getAmount()) : BigDecimal.ZERO);
                }
            }
        }

        BigDecimal employerSum = BigDecimal.ZERO;
        dto.setEmployeeContribution(employeeSum);
        dto.setEmployerContribution(employerSum);
        dto.setTotal(employeeSum.add(employerSum));

        return dto;
    }

    @Override
    public TdsSummaryDTO getTdsSummary(String organizationId, LocalDate fromDate, LocalDate toDate) {
        TdsSummaryDTO dto = new TdsSummaryDTO();

        if (fromDate == null || toDate == null) {
            LocalDate today = LocalDate.now();
            int year = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
            fromDate = LocalDate.of(year, 4, 1);
            toDate = fromDate.plusYears(1).minusDays(1);
        }

        Double totalTaxes = employeePayRunRepository.sumTotalTaxesForOrgBetween(organizationId, fromDate, toDate);
        BigDecimal total = totalTaxes != null ? BigDecimal.valueOf(totalTaxes) : BigDecimal.ZERO;

        dto.setTotalContribution(total);
        return dto;
    }
}
