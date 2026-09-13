package com.itsdev.payroll.service.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.EsiDTO;

public interface EsiService {
    EsiDTO getEsiByOrganizationId(String organizationId);
    EsiDTO saveOrUpdateEsi(String organizationId, EsiDTO dto);
    EsiDTO disableEsi(String organizationId);
}
