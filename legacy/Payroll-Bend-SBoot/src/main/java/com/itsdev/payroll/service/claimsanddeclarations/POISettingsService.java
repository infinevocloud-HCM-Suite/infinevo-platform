package com.itsdev.payroll.service.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ReminderConfig;
import com.itsdev.payroll.dto.employeeitdeclaration.poi.POISettingsDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.repository.claimsanddeclarations.ProofOfInvestmentRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class POISettingsService {

    private final ProofOfInvestmentRepository poiSettingsRepository;
    private final OrganizationRepository organizationRepository;

    @Cacheable(value = "poiSettings", key = "#organizationId")
    public ProofOfInvestment getSettings(String organizationId) {
        var org = organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        return poiSettingsRepository.findByOrganization(org)
                .orElseThrow(() -> new RuntimeException("POI settings not found for organization: " + organizationId));
    }

    // New method to get POI settings as DTO (for reminder service)
    @Cacheable(value = "poiSettingsDTO", key = "#organizationId")
    public POISettingsDTO getSettingsDTO(String organizationId) {
        ProofOfInvestment settings = getSettings(organizationId);

        POISettingsDTO dto = new POISettingsDTO();
        dto.setCanChangeTaxRegimePoi(settings.isCanChangeTaxRegimePoi());
        dto.setAttachmentMandatoryPoiForPortal(settings.isAttachmentMandatoryPoiForPortal());
        dto.setPoiLocked(settings.isPoiLocked());
        dto.setAttachmentEnabledForPoi(settings.isAttachmentEnabledForPoi());
        dto.setLastDateForPoi(settings.getLastDateForPoi());
        dto.setCommentsMandatoryForPoiApproval(settings.isCommentsMandatoryForPoiApproval());
        dto.setCanItOverrideInProofMode(settings.isCanItOverrideInProofMode());
        dto.setPanMandatoryForAnnualRentOverOneLakh(settings.isPanMandatoryForAnnualRentOverOneLakh());
        dto.setSendMailOnPoiRelease(settings.isSendMailOnPoiRelease());
        dto.setSendMailOnPoiLock(settings.isSendMailOnPoiLock());
        dto.setSendMailOnEmployeeLevelPoiLockAndRelease(settings.isSendMailOnEmployeeLevelPoiLockAndRelease());
        dto.setAnyReminderBeforeLockdateEnabled(settings.isAnyReminderBeforeLockdateEnabled());

        // Convert reminders from entity to DTO
        if (settings.getReminders() != null) {
            List<ReminderConfig> reminderConfigs = settings.getReminders().stream()
                    .map(reminder -> new ReminderConfig(
                            reminder.isEnabled(),
                            reminder.getNumberOfDays(),
                            reminder.getReminderId()))
                    .collect(Collectors.toList());
            dto.setReminders(reminderConfigs);
        }

        return dto;
    }

    // New method to get organizations with POI enabled (for scheduler)
    public List<Organization> getOrganizationsWithPOIEnabled() {
        return organizationRepository.findAll().stream()
                .filter(org -> {
                    try {
                        ProofOfInvestment poiSettings = getSettings(org.getOrganizationId());
                        return poiSettings != null &&
                                poiSettings.isAnyReminderBeforeLockdateEnabled() &&
                                poiSettings.getLastDateForPoi() != null;
                    } catch (Exception e) {
                        log.warn("Error getting POI settings for org {}: {}",
                                org.getOrganizationId(), e.getMessage());
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    // Convenience methods
    public boolean isPoiLocked(String organizationId) {
        return getSettings(organizationId).isPoiLocked();
    }

    public boolean isAttachmentMandatory(String organizationId) {
        return getSettings(organizationId).isAttachmentMandatoryPoiForPortal();
    }

    public boolean canChangeTaxRegime(String organizationId) {
        return getSettings(organizationId).isCanChangeTaxRegimePoi();
    }

    public boolean isAttachmentEnabled(String organizationId) {
        return getSettings(organizationId).isAttachmentEnabledForPoi();
    }

    public String getLastDateForPoi(String organizationId) {
        return getSettings(organizationId).getLastDateForPoi();
    }

    public boolean isCommentsMandatoryForApproval(String organizationId) {
        return getSettings(organizationId).isCommentsMandatoryForPoiApproval();
    }

    public boolean isSendMailOnPoiRelease(String organizationId) {
        return getSettings(organizationId).isSendMailOnPoiRelease();
    }

    public boolean isSendMailOnEmployeeLevelPoiLockAndRelease(String organizationId) {
        return getSettings(organizationId).isSendMailOnEmployeeLevelPoiLockAndRelease();
    }

    // New method to parse last date
    public LocalDate getLastDateAsLocalDate(String organizationId) {
        String lastDateStr = getLastDateForPoi(organizationId);
        if (lastDateStr == null || lastDateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(lastDateStr);
        } catch (Exception e) {
            log.error("Invalid date format for lastDateForPoi: {}", lastDateStr);
            return null;
        }
    }
}