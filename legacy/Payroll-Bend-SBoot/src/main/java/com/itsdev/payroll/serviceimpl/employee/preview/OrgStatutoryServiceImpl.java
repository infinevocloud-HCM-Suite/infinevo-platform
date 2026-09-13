package com.itsdev.payroll.serviceimpl.employee.preview;

import com.itsdev.payroll.dto.employee.preview.OrgStatutoryConfigDTO;
import com.itsdev.payroll.entity.statutorycomponents.Epf;
import com.itsdev.payroll.entity.statutorycomponents.Esi;
import com.itsdev.payroll.repository.statutorycomponents.EpfRepository;
import com.itsdev.payroll.repository.statutorycomponents.EsiRepository;
import com.itsdev.payroll.service.employee.preview.OrgStatutoryService;
import org.springframework.stereotype.Service;

@Service
public class OrgStatutoryServiceImpl implements OrgStatutoryService {

    private final EpfRepository epfRepository;
    private final EsiRepository esiRepository;

    public OrgStatutoryServiceImpl(EpfRepository epfRepository,
                                   EsiRepository esiRepository) {
        this.epfRepository = epfRepository;
        this.esiRepository = esiRepository;
    }

    @Override
    public OrgStatutoryConfigDTO getOrgStatutoryConfig(String organizationId) {

        // ---------------- EPF ----------------
        var epfOpt = epfRepository.findByOrganization_OrganizationId(organizationId);

        Boolean epfEnabled = epfOpt
                .map(epf -> Boolean.TRUE.equals(epf.getIsActive()))
                .orElse(false);

        String epfEmployeeContri = epfOpt
                .map(Epf::getEpfEmployeeContribution)
                .orElse(null);

        String epfEmployerContri = epfOpt
                .map(Epf::getEpfEmployerContribution)
                .orElse(null);

        // NEW: EPF visibility flags
        Boolean isEdliIncludedSalaryStructure = epfOpt
                .map(Epf::getIsEdliIncludedSalaryStructure)
                .orElse(false);

        Boolean isAdminChargesIncludedSalaryStructure = epfOpt
                .map(Epf::getIsAdminChargesIncludedSalaryStructure)
                .orElse(false);


        // ---------------- ESI ----------------
        var esiOpt = esiRepository.findByOrganization_OrganizationId(organizationId);

        Boolean esiEnabled = esiOpt
                .map(esi -> Boolean.TRUE.equals(esi.getIsActive()))
                .orElse(false);

        String esiEmployeeContri = esiOpt
                .map(Esi::getEmployeeContribution)
                .orElse(null);

        String esiEmployerContri = esiOpt
                .map(Esi::getEmployerContribution)
                .orElse(null);

        // ---------------- FINAL DTO ----------------
        return new OrgStatutoryConfigDTO(
                epfEnabled,
                epfEmployeeContri,
                epfEmployerContri,
                isEdliIncludedSalaryStructure,
                isAdminChargesIncludedSalaryStructure,
                esiEnabled,
                esiEmployeeContri,
                esiEmployerContri
        );
    }
}
