package com.itsdev.payroll.serviceimpl;

import com.itsdev.payroll.dto.OrgSetupStepsDTO;
import com.itsdev.payroll.entity.OrgSetupSteps;
import com.itsdev.payroll.mapper.OrgSetupStepsMapper;
import com.itsdev.payroll.repository.OrgSetupStepsRepository;
import com.itsdev.payroll.repository.PayScheduleRepository;
import com.itsdev.payroll.repository.employee.EmployeePersonalDetailRepository;
import com.itsdev.payroll.repository.organization.IncomeTaxDetailsRepository;
import com.itsdev.payroll.repository.organization.WorkLocationRepository;
import com.itsdev.payroll.repository.payruns.PayRunRepository;
import com.itsdev.payroll.repository.salarycomponents.EarningRepository;
import com.itsdev.payroll.repository.statutorycomponents.EpfRepository;
import com.itsdev.payroll.repository.statutorycomponents.EsiRepository;
import com.itsdev.payroll.repository.statutorycomponents.ProfessionalTaxRepository;
import com.itsdev.payroll.service.OrgSetupStepsService;
import org.springframework.stereotype.Service;

@Service
public class OrgSetupStepsServiceImpl implements OrgSetupStepsService {

    private final OrgSetupStepsRepository repository;

    private final WorkLocationRepository workLocationRepository;
    private final EmployeePersonalDetailRepository employeeRepository;
    private final PayScheduleRepository payScheduleRepository;
    private final IncomeTaxDetailsRepository orgTaxRepository;
    private final EarningRepository salaryComponentsRepository;
    private final EpfRepository epfRepository;
    private final EsiRepository esiRepository;
    private final ProfessionalTaxRepository ptax;
    private final PayRunRepository payRunRepository;

    public OrgSetupStepsServiceImpl(
            OrgSetupStepsRepository repository,
            WorkLocationRepository workLocationRepository,
            EmployeePersonalDetailRepository employeeRepository,
            PayScheduleRepository payScheduleRepository,
            IncomeTaxDetailsRepository orgTaxRepository,
            EarningRepository salaryComponentsRepository,
            EpfRepository epfRepository,
            EsiRepository esiRepository,
            ProfessionalTaxRepository ptax,
            PayRunRepository payRunRepository) {
        this.repository = repository;
        this.workLocationRepository = workLocationRepository;
        this.employeeRepository = employeeRepository;
        this.payScheduleRepository = payScheduleRepository;
        this.orgTaxRepository = orgTaxRepository;
        this.salaryComponentsRepository = salaryComponentsRepository;
        this.epfRepository = epfRepository;
        this.esiRepository = esiRepository;
        this.ptax = ptax;
        this.payRunRepository = payRunRepository;
    }

    @Override
    public OrgSetupStepsDTO getOrgSetupSteps(String organizationId) {
        OrgSetupSteps entity = repository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    OrgSetupSteps steps = new OrgSetupSteps();
                    steps.setOrganizationId(organizationId);
                    return repository.save(steps);
                });

        // check each repository and update status
        entity.setWorkLocationSetup(workLocationRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setEmployeeSetup(employeeRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setPayScheduleSetup(payScheduleRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setOrgTaxSetup(orgTaxRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setSalaryComponentsSetup(salaryComponentsRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setEPFSetup(epfRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setESISetup(esiRepository.existsByOrganization_OrganizationId(organizationId));
        entity.setPTAXSetup(ptax.existsByOrganization_OrganizationId(organizationId));
        entity.setPriorPayrollSetup(payRunRepository.existsByOrganization_OrganizationId(organizationId));

        repository.save(entity);

        return OrgSetupStepsMapper.toDto(entity);
    }
}
