package com.itsdev.payroll.serviceimpl.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.EmployeeInvestmentDeclarationDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.IncomeTaxDeclarationDTO;
import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvestmentDeclaration;
import com.itsdev.payroll.entity.EmployeeITDeclaration.Section6AItemMaster;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.employeeitdeclaration.EmployeeInvestmentDeclarationMapper;
import com.itsdev.payroll.mapper.employeeitdeclaration.Section6AItemMasterMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.EmployeeInvestmentDeclarationRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.claimsanddeclarations.IncomeTaxDeclarationService;
import com.itsdev.payroll.service.employeeitdeclaration.EmployeeInvestmentDeclarationService;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.itsdev.payroll.dto.employeeitdeclaration.EmployeeInvestmentDeclarationRequestDTO;
import com.itsdev.payroll.repository.employeeitdeclaration.Section6AItemMasterRepository;

@Service
public class EmployeeInvestmentDeclarationServiceImpl
        implements EmployeeInvestmentDeclarationService {

    private final EmployeeInvestmentDeclarationRepository declarationRepository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final IncomeTaxDeclarationService incomeTaxDeclarationService;
    private final Section6AItemMasterRepository section6AItemMasterRepository;


    public EmployeeInvestmentDeclarationServiceImpl(
            EmployeeInvestmentDeclarationRepository declarationRepository,
            OrganizationRepository organizationRepository,
            BasicDetailsRepository basicDetailsRepository,
            IncomeTaxDeclarationService incomeTaxDeclarationService,
            Section6AItemMasterRepository section6AItemMasterRepository
    ) {
        this.declarationRepository = declarationRepository;
        this.organizationRepository = organizationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.incomeTaxDeclarationService = incomeTaxDeclarationService;
        this.section6AItemMasterRepository = section6AItemMasterRepository;
    }

    // ======================= GET =======================

@Override
@Transactional(readOnly = true)
public EmployeeInvestmentDeclarationDTO getDeclaration(
        String organizationId,
        String employeeId,
        Integer fiscalYear
) {


    // Validate ORG & EMPLOYEE

    Organization org = organizationRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new RuntimeException("Organization not found"));

    BasicDetails employee = basicDetailsRepository
            .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found"));


    // Fetch ORG-level IT settings (ALWAYS)
    IncomeTaxDeclarationDTO settings =
            incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);


    // Try fetching employee declaration (OPTIONAL)

    EmployeeInvestmentDeclaration declaration =
            declarationRepository
                    .findByOrganizationAndEmployeeAndFiscalYear(org, employee, fiscalYear)
                    .orElse(null);

    EmployeeInvestmentDeclarationDTO dto;


    // CASE A: EMPLOYEE DECLARATION EXISTS → NORMAL FLOW

    if (declaration != null) {

        // 🔹 Derive permissions from settings (NOT DB)
        boolean canEdit =
        !settings.isItDeclarationLocked()
        && "DRAFT".equalsIgnoreCase(declaration.getStatus());

        declaration.setCanAllowEdit(canEdit);

        declaration.setCanChangeTaxRegime(settings.isCanChangeTaxRegimeIt());
        declaration.setIsLenderPanMandatory(
                settings.isPanMandatoryForAnnualRentOverOneLakh()
        );

        // 🔹 Map entity → DTO
        dto = EmployeeInvestmentDeclarationMapper.toDTO(declaration);

    }

    // CASE B: EMPLOYEE DECLARATION DOES NOT EXIST → DRAFT

    else {

        dto = new EmployeeInvestmentDeclarationDTO();

        // 🔹 Mandatory identifiers
        dto.setFiscalYear(fiscalYear);

        // 🔹 Derived permissions from ORG settings
        dto.setCanAllowEdit(!settings.isItDeclarationLocked());
        dto.setCanChangeTaxRegime(settings.isCanChangeTaxRegimeIt());
        dto.setIsLenderpanMandatory(
                settings.isPanMandatoryForAnnualRentOverOneLakh()
        );

        // 🔹 Default status
        dto.setStatus("NOT_CREATED");
        dto.setStatusFormatted("Not Created");

        // 🔹 Empty collections for UI safety
        dto.setHouseRentDeclarations(List.of());
        dto.setSection6aDeclarations(List.of());
        dto.setHomeLoanDeclarations(List.of());
        dto.setOtherIncomesDeclarations(List.of());
        dto.setPreviousEmploymentDeclarations(List.of());
        dto.setLetOutPropertyDeclarations(List.of());

        // 🔹 Tax year boundaries (optional but helpful)
        dto.setDeclarationTaxYearStart((fiscalYear - 1) + "-04");
        dto.setDeclarationTaxYearEnd(fiscalYear + "-03");


        // Current tax year from TODAY
        String[] currentRange = getCurrentTaxYearRange();
        dto.setCurrentTaxYearStart(currentRange[0]);
        dto.setCurrentTaxYearEnd(currentRange[1]);
    }

   
    //Attach Section 6A MASTER DATA (ALWAYS)

    List<Section6AItemMaster> masters =
            section6AItemMasterRepository.findByIsActiveTrue();

    dto.setSection6aItems(
            Section6AItemMasterMapper.mapSection6AItems(masters)
    );

  
    // Attach read-only ORG info

    dto.setLastDateForItDeclaration(settings.getLastDateForItDeclaration());
    dto.setCanChangeTaxRegimeIt(settings.isCanChangeTaxRegimeIt());


    // Return FINAL RESPONSE

    return dto;
}

    // ======================= CREATE =======================

@Override
@Transactional
public EmployeeInvestmentDeclarationDTO createDeclaration(
        String organizationId,
        String employeeId,
        Integer fiscalYear,
        EmployeeInvestmentDeclarationRequestDTO dto
) {


    // Fetch Organization
    Organization org = organizationRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new RuntimeException(
                    "Organization not found: " + organizationId
            ));

    // Fetch Employee
    BasicDetails employee = basicDetailsRepository
            .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
            .orElseThrow(() -> new RuntimeException(
                    "Employee not found: " + employeeId + " in org " + organizationId
            ));

    //Check Duplicate
    boolean exists = declarationRepository
            .existsByOrganizationAndEmployeeAndFiscalYear(org, employee, fiscalYear);

    if (exists) {
        throw new RuntimeException(
                "IT Declaration already exists for this employee and fiscal year"
        );
    }


    // Fetch ORG-LEVEL IT SETTINGS
 
    IncomeTaxDeclarationDTO orgConfig =
            incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);


    //Create Declaration

    EmployeeInvestmentDeclaration declaration = new EmployeeInvestmentDeclaration();
    declaration.setOrganization(org);
    declaration.setEmployee(employee);
    declaration.setFiscalYear(fiscalYear);


    // ORG-DERIVED FIELDS

declaration.setIsLenderPanMandatory(
        orgConfig.isPanMandatoryForAnnualRentOverOneLakh()
);

declaration.setCanChangeTaxRegime(
        orgConfig.isCanChangeTaxRegimeIt()
);

// Employee Edit Permission
declaration.setCanAllowEdit(!orgConfig.isItDeclarationLocked());



    //SYSTEM DEFAULTS
    
    declaration.setIsMultipleTaxRegimesApplicable(true);

    declaration.setStatus("DRAFT");
    declaration.setStatusFormatted("Draft");
    declaration.setMessageTypes("INFO");
    declaration.setTaxPlanCount(0);



        // Example: FY 2025 → 2024-04 to 2025-03
        declaration.setDeclarationTaxYearStart((fiscalYear - 1) + "-04");
        declaration.setDeclarationTaxYearEnd(fiscalYear + "-03");

        // CURRENT FINANCIAL YEAR (Apr–Mar)

        String[] currentRange = getCurrentTaxYearRange();
        declaration.setCurrentTaxYearStart(currentRange[0]);
        declaration.setCurrentTaxYearEnd(currentRange[1]);

    // APPLY UI EDITABLE FIELDS
    EmployeeInvestmentDeclarationMapper.updateEntityFromRequestDTO(
            dto, declaration
    );

    //DERIVED FIELDS
    if ("OLD".equalsIgnoreCase(declaration.getTaxRegime())) {
        declaration.setTaxRegimeFormatted("Old Tax Regime");
    } else if ("NEW".equalsIgnoreCase(declaration.getTaxRegime())) {
        declaration.setTaxRegimeFormatted("New Tax Regime");
    }

    // SAVE
    EmployeeInvestmentDeclaration saved =
            declarationRepository.save(declaration);

// 🔹 map employee declaration
EmployeeInvestmentDeclarationDTO response =
        EmployeeInvestmentDeclarationMapper.toDTO(saved);

// 🔹 fetch active master items
List<Section6AItemMaster> masters =
        section6AItemMasterRepository.findByIsActiveTrue();

// 🔹 attach master list for UI
response.setSection6aItems(
        Section6AItemMasterMapper.mapSection6AItems(masters)
);

return response;
}


    // ======================= UPDATE =======================

@Override
@Transactional
public EmployeeInvestmentDeclarationDTO updateDeclaration(
        String organizationId,
        String employeeId,
        Integer fiscalYear,
        EmployeeInvestmentDeclarationRequestDTO dto
) {

    Organization org = organizationRepository.findByOrganizationId(organizationId)
            .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

    BasicDetails employee = basicDetailsRepository
            .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
            .orElseThrow(() -> new RuntimeException(
                    "Employee not found: " + employeeId + " in org " + organizationId
            ));

    EmployeeInvestmentDeclaration declaration = declarationRepository
            .findByOrganizationAndEmployeeAndFiscalYear(org, employee, fiscalYear)
            .orElseThrow(() -> new RuntimeException(
                    "IT Declaration not found for update"
            ));


        // ❌ Prevent edit after submission / approval
        if ("SUBMITTED".equalsIgnoreCase(declaration.getStatus()) ||
        "APPROVED".equalsIgnoreCase(declaration.getStatus())) {

        throw new RuntimeException(
                "IT Declaration is already submitted and cannot be edited");
        }

    IncomeTaxDeclarationDTO settings =
        incomeTaxDeclarationService.getIncomeTaxDeclaration(organizationId);

// org-level lock → employee edit permission
declaration.setCanAllowEdit(!settings.isItDeclarationLocked());



    EmployeeInvestmentDeclarationMapper.updateEntityFromRequestDTO(
            dto, declaration
    );

    EmployeeInvestmentDeclaration saved =
            declarationRepository.save(declaration);

    // 🔹 map employee declaration
EmployeeInvestmentDeclarationDTO response =
        EmployeeInvestmentDeclarationMapper.toDTO(saved);

// 🔹 fetch master items
List<Section6AItemMaster> masters =
        section6AItemMasterRepository.findByIsActiveTrue();

// 🔹 attach to response
response.setSection6aItems(
        Section6AItemMasterMapper.mapSection6AItems(masters)
);

return response;
}


    // ======================= DELETE =======================

    @Override
    @Transactional
    public void deleteDeclaration(
            String organizationId,
            String employeeId,
            Integer fiscalYear
    ) {

        Organization org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException(
                        "Employee not found: " + employeeId + " in org " + organizationId
                ));

        declarationRepository.deleteByOrganizationAndEmployeeAndFiscalYear(
                org, employee, fiscalYear
        );
    }

    // Utility: Current Financial Year (Apr–Mar)

    private String[] getCurrentTaxYearRange() {

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        // Financial year logic: April → March
        if (month <= 3) {
            // Jan–Mar → FY ends this year
            return new String[]{
                    (year - 1) + "-04",
                    year + "-03"
            };
        } else {
            // Apr–Dec → FY ends next year
            return new String[]{
                    year + "-04",
                    (year + 1) + "-03"
            };
        }
    }
}