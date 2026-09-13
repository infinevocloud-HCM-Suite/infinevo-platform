package com.itsdev.payroll.service.statutorycomponents;

import com.itsdev.payroll.dto.statutorycomponents.EpfDTO;

public interface EpfService {

    EpfDTO getEpfByOrganizationId(String organizationId);

    EpfDTO createOrUpdateEpf(String organizationId, EpfDTO dto);

    EpfDTO disableEpf(String organizationId);
}
