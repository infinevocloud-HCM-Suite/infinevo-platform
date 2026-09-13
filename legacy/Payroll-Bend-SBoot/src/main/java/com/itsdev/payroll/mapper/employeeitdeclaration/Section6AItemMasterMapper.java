package com.itsdev.payroll.mapper.employeeitdeclaration;

import java.util.Collections;
import java.util.List;

import com.itsdev.payroll.dto.employeeitdeclaration.Section6AItemDTO;
import com.itsdev.payroll.entity.EmployeeITDeclaration.Section6AItemMaster;

public class Section6AItemMasterMapper {

    private Section6AItemMasterMapper() {}

    public static List<Section6AItemDTO> mapSection6AItems(
            List<Section6AItemMaster> masters
    ) {
        if (masters == null) return Collections.emptyList();

        return masters.stream().map(m -> {
            Section6AItemDTO dto = new Section6AItemDTO();

            dto.setId(m.getId());
            dto.setCategory(m.getCategory());
            dto.setCategoryFormatted(m.getCategoryFormatted());
            dto.setType(m.getType());
            dto.setTypeFormatted(m.getTypeFormatted());
            dto.setMaxLimit(m.getMaxLimit());
            dto.setMaxLimitFormatted(m.getMaxLimitFormatted());

            // UI flags
            dto.setIs80c(m.getIs80c());
            dto.setIs80d(m.getIs80d());
            dto.setIsOtherSection(m.getIsOtherSection());
            dto.setIsActive(m.getIsActive()); // 🔴 MISSING earlier

            return dto;
        }).toList();
    }
}
