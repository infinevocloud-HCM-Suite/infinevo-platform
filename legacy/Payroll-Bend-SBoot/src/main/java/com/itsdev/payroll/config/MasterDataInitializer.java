package com.itsdev.payroll.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itsdev.payroll.entity.MasterConfig;
import com.itsdev.payroll.repository.MasterConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MasterDataInitializer implements CommandLineRunner {

    private final MasterConfigRepository masterConfigRepository;
    private final ObjectMapper objectMapper;

    public MasterDataInitializer(MasterConfigRepository masterConfigRepository,
                                 ObjectMapper objectMapper) {
        this.masterConfigRepository = masterConfigRepository;
        this.objectMapper = objectMapper;
    }

    private void createMasterData(String componentName, String jsonConfig) throws Exception {
        if (masterConfigRepository.findByComponentName(componentName).isEmpty()) {
            MasterConfig config = new MasterConfig();
            config.setComponentName(componentName);
            JsonNode node = objectMapper.readTree(jsonConfig);
            config.setConfigData(node);
            masterConfigRepository.save(config);
        }
    }

    @Override
    public void run(String... args) throws Exception {

        // Default EPF JSON
        String epfJson = """
        {
          "isAdminChargesIncludedCtc": false,
          "isEdliIncludedCtc": false,
          "considerEarnedSalaryForEpf": true,
          "registrationNumber": "",
          "epfAdminChargesEmployerContribution": "0.50%",
          "isEmployerContributionIncludedCtc": false,
          "isEmployeeRestrictedBasicEnabled": false,
          "isEmployerContributionIncludedSalaryStructure": false,
          "isEligibleForAbryScheme": false,
          "edliEmployerContribution": "0.50%",
          "epfEmployeeContribution": "12.00%",
          "canEnableEdliPfAdminChargesInSalaryStructure": true,
          "canProRateRestrictedBasic": false,
          "isAssociatedWithEmployee": false,
          "epsSeniorCategoryAge": 58,
          "isActive": false,
          "canOverrideRestrictedBasic": false,
          "epsEmployeeContribution": "NA",
          "epfAdminChargesEmployeeContribution": "NA",
          "isEdliIncludedSalaryStructure": false,
          "isAdminChargesIncludedSalaryStructure": false,
          "epsEmployerContribution": "8.33%",
          "deductionCycleFormatted": "Monthly",
          "isSubsidyApplicableForBothContributions": false,
          "registrationDate": "",
          "registrationDateFormatted": "",
          "epsEmployerContributionForSeniorCategory": "0.00%",
          "name": "Employee Provident Fund",
          "isEmployerRestrictedBasicEnabled": false,
          "epfEmployerContribution": "3.67%",
          "canEnableEdliPfAdminChargesInCtc": true,
          "deductionCycle": "monthly",
          "epfEmployerContributionForSeniorCategory": "12.00%",
          "edliEmployeeContribution": "NA"
        }
        """;

        // Default ESI JSON
        String esiJson = """
        {
          "isActive": false,
          "employeeContribution": "0.75%",
          "registrationNumber": "",
          "canEnableEmployerEsiInCtc": true,
          "isIncludedInSalaryStructure": false,
          "deductionCycleFormatted": "Monthly",
          "registrationDate": "",
          "registrationDateFormatted": "",
          "isIncludedInCtc": false,
          "name": "Employee State Insurance",
          "deductionCycle": "monthly",
          "employerContribution": "3.25%",
          "isAssociatedWithEmployee": false
        }
        """;


        // Create EPF Master if not exists
        createMasterData("EPF", epfJson);
        // Create ESI Master if not exists
        createMasterData("ESI", esiJson);

    }
}
