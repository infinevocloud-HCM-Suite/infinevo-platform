package com.itsdev.payroll.service;

import com.itsdev.payroll.dto.PayScheduleDTO;

public interface PayScheduleService {
    public PayScheduleDTO create(String organizationId, PayScheduleDTO dto);
    PayScheduleDTO getByOrganizationId(String organizationId);
    PayScheduleDTO update(String organizationId, String payScheduleId, PayScheduleDTO dto);
}