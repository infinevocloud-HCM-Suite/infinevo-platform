package com.itsdev.payroll.mapper.statutorycomponents;


import com.itsdev.payroll.dto.statutorycomponents.ProfessionalTaxDTO;
import com.itsdev.payroll.dto.statutorycomponents.SlabDetailDTO;
import com.itsdev.payroll.dto.statutorycomponents.SlabRateConfigurationDTO;
import com.itsdev.payroll.entity.statutorycomponents.ProfessionalTax;
import com.itsdev.payroll.entity.statutorycomponents.SlabDetail;
import com.itsdev.payroll.entity.statutorycomponents.SlabRateConfiguration;

import java.util.stream.Collectors;

public class ProfessionalTaxMapper {

    public static ProfessionalTaxDTO toDTO(ProfessionalTax entity) {
        ProfessionalTaxDTO dto = new ProfessionalTaxDTO();
        dto.setTaxId(entity.getTaxId());
        dto.setState(entity.getState());
        dto.setStateCode(entity.getStateCode());
        dto.setLocationName(entity.getLocationName());
        dto.setRegistrationNumber(entity.getRegistrationNumber());
        dto.setRegistrationDate(entity.getRegistrationDate());
        dto.setTaxConfigurationFrequency(entity.getTaxConfigurationFrequency());
        dto.setGrossSalaryConfigurationFrequency(entity.getGrossSalaryConfigurationFrequency());
        dto.setDeductionFrequency(entity.getDeductionFrequency());
        dto.setProfessionalTaxSupported(entity.isProfessionalTaxSupported());
        dto.setReadOnly(entity.isReadOnly());
        dto.setEffectiveFrom(entity.getEffectiveFrom());

        if (entity.getSlabDetails() != null) {
            dto.setSlabDetails(entity.getSlabDetails().stream()
                    .map(ProfessionalTaxMapper::toSlabDetailDto)
                    .collect(Collectors.toList()));
        }

        if (entity.getSlabRateConfigurations() != null) {
            dto.setSlabRateConfigurations(entity.getSlabRateConfigurations().stream()
                    .map(ProfessionalTaxMapper::toSlabRateConfigDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private static SlabDetailDTO toSlabDetailDto(SlabDetail slab) {
        SlabDetailDTO dto = new SlabDetailDTO();
        dto.setId(slab.getId());
        dto.setStartAmount(slab.getStartAmount());
        dto.setEndAmount(slab.getEndAmount());
        dto.setPayAmount(slab.getPayAmount());
        dto.setFemaleExempted(slab.isFemaleExempted());
        dto.setDeductionMonths(slab.getDeductionMonths());
        dto.setDefaultFromMaster(slab.getDefaultFromMaster());
        return dto;
    }

    private static SlabRateConfigurationDTO toSlabRateConfigDto(SlabRateConfiguration config) {
        SlabRateConfigurationDTO dto = new SlabRateConfigurationDTO();
        dto.setTaxRateSettingsId(config.getTaxRateSettingsId());
        dto.setTaxConfigurationFrequency(config.getTaxConfigurationFrequency());
        dto.setGrossSalaryConfigurationFrequency(config.getGrossSalaryConfigurationFrequency());
        dto.setDeductionFrequency(config.getDeductionFrequency());
        dto.setEffectiveFrom(config.getEffectiveFrom());
        dto.setEffectiveTo(config.getEffectiveTo());
        dto.setActive(config.isActive());
        dto.setEditable(config.isEditable());

        if (config.getSlabDetails() != null) {
            dto.setSlabDetails(config.getSlabDetails().stream()
                    .map(ProfessionalTaxMapper::toSlabDetailDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}
