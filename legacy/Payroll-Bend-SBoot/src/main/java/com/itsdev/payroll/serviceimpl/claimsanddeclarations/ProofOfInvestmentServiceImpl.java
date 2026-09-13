package com.itsdev.payroll.serviceimpl.claimsanddeclarations;

import com.itsdev.payroll.dto.claimsanddeclarations.ProofOfInvestmentDTO;
import com.itsdev.payroll.dto.claimsanddeclarations.ReminderDTO;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.mapper.claimsanddeclarations.ProofOfInvestmentMapper;
import com.itsdev.payroll.repository.claimsanddeclarations.ProofOfInvestmentRepository;
import com.itsdev.payroll.repository.employeeTDS.EmployeeTdsRepository;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.claimsanddeclarations.ProofOfInvestmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.itsdev.payroll.repository.organization.OrganizationRepository;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List; // ← ADD THIS IMPORT!
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProofOfInvestmentServiceImpl implements ProofOfInvestmentService {


        private final ProofOfInvestmentRepository poiRepository;
        private final OrganizationRepository organizationRepository;
        private final EmployeeTdsRepository employeeTdsRepository;

        public ProofOfInvestmentServiceImpl(ProofOfInvestmentRepository poiRepository,
                        OrganizationRepository organizationRepository,
                                            EmployeeTdsRepository employeeTdsRepository) {
                this.poiRepository = poiRepository;
                this.organizationRepository = organizationRepository;
                this.employeeTdsRepository = employeeTdsRepository;
        }

        @Override
        @Transactional
        public ProofOfInvestmentDTO getProofOfInvestment(String organizationId) {
                Organization org = organizationRepository.findByOrganizationId(organizationId)
                                .orElseThrow(() -> new RuntimeException("Organization not found"));


                ProofOfInvestment poi = poiRepository.findByOrganization(org)
                                .orElseGet(() -> createDefaultProofOfInvestment(org));

                return ProofOfInvestmentMapper.toDTO(poi);
        }

        private ProofOfInvestment createDefaultProofOfInvestment(Organization org) {
                ProofOfInvestment poi = new ProofOfInvestment();
                poi.setOrganization(org);

                // defaults matching your GET payload
                poi.setCanChangeTaxRegimePoi(true);
                poi.setAttachmentMandatoryPoiForPortal(false);
                poi.setSendMailOnPoiRelease(true);
                poi.setSendMailOnPoiLock(true);
                poi.setPoiLocked(true);
                poi.setCommentsMandatoryForPoiApproval(false);
                poi.setAnyReminderBeforeLockdateEnabled(true);
                poi.setSendMailOnEmployeeLevelPoiLockAndRelease(false);
                poi.setAttachmentEnabledForPoi(true);
                poi.setCanTdsExceedAnnualLimit(false);
                poi.setLastDateForPoi("");
                poi.setCanItOverrideInProofMode(false);
                poi.setMonthFormatted("");
                poi.setPayscheduleConfigured(true);
                poi.setMonthToConsiderPoi("");
                poi.setPanMandatoryForAnnualRentOverOneLakh(false);

                // create default reminders
                Reminder r1 = new Reminder();
                r1.setReminderId(generate12DigitId());
                r1.setEnabled(true);
                r1.setNumberOfDays(5);
                r1.setProofOfInvestment(poi);

                Reminder r2 = new Reminder();
                r2.setReminderId(generate12DigitId());
                r2.setEnabled(true);
                r2.setNumberOfDays(1);
                r2.setProofOfInvestment(poi);

                poi.getReminders().addAll(Arrays.asList(r1, r2));

                return poiRepository.save(poi);
        }

        @Override
        @Transactional
        public ProofOfInvestmentDTO updateProofOfInvestment(String organizationId, ProofOfInvestmentDTO dto) {
                Organization org = organizationRepository.findByOrganizationId(organizationId)
                                .orElseThrow(() -> new RuntimeException("Organization not found"));

                ProofOfInvestment poi = poiRepository.findByOrganization(org)
                                .orElseThrow(() -> new RuntimeException("ProofOfInvestment not found"));

                String oldMonth = poi.getMonthToConsiderPoi();

                // ✅ UPDATE ALL FIELDS INCLUDING lastDateForPoi
                poi.setCanTdsExceedAnnualLimit(dto.isCanTdsExceedAnnualLimit());
                poi.setPoiLocked(dto.isPoiLocked());
                poi.setCanChangeTaxRegimePoi(dto.isCanChangeTaxRegimePoi());
                poi.setAttachmentMandatoryPoiForPortal(dto.isAttachmentMandatoryPoiForPortal());
                poi.setSendMailOnPoiRelease(dto.isSendMailOnPoiRelease());
                poi.setSendMailOnPoiLock(dto.isSendMailOnPoiLock());
                poi.setCommentsMandatoryForPoiApproval(dto.isCommentsMandatoryForPoiApproval());
                poi.setAnyReminderBeforeLockdateEnabled(dto.isAnyReminderBeforeLockdateEnabled());
                poi.setSendMailOnEmployeeLevelPoiLockAndRelease(dto.isSendMailOnEmployeeLevelPoiLockAndRelease());
                poi.setAttachmentEnabledForPoi(dto.isAttachmentEnabledForPoi());

                // ✅ CRITICAL FIX: Add these missing fields
                poi.setLastDateForPoi(dto.getLastDateForPoi());
                poi.setCanItOverrideInProofMode(dto.isCanItOverrideInProofMode());
                poi.setMonthFormatted(dto.getMonthFormatted());
                poi.setPayscheduleConfigured(dto.isPayscheduleConfigured());
                poi.setMonthToConsiderPoi(dto.getMonthToConsiderPoi());
                poi.setPanMandatoryForAnnualRentOverOneLakh(dto.isPanMandatoryForAnnualRentOverOneLakh());

                // Update reminders
                updateReminders(poi, dto.getReminders());

                ProofOfInvestment saved = poiRepository.save(poi);

                if (dto.getMonthToConsiderPoi() != null
                        && !dto.getMonthToConsiderPoi().equalsIgnoreCase(oldMonth)) {

                        System.out.println("Org ID = " + org.getOrganizationId());
                        System.out.println("Old Month = " + oldMonth);
                        System.out.println("New Month = " + dto.getMonthToConsiderPoi());

                        int updatedRows =
                                employeeTdsRepository.updateEffectiveMonthByOrganization(
                                        org.getOrganizationId(),
                                        dto.getMonthToConsiderPoi().toLowerCase()
                                );

                        System.out.println("Updated EmployeeTds rows = " + updatedRows);
                }

                return ProofOfInvestmentMapper.toDTO(saved);
        }

        private void updateReminders(
                        ProofOfInvestment poi,
                        List<ReminderDTO> reminderDTOs) {
                if (reminderDTOs == null) {
                        return;
                }

                // 1️⃣ Map existing reminders by reminderId
                Map<String, Reminder> existingMap = poi.getReminders().stream()
                                .collect(Collectors.toMap(
                                                Reminder::getReminderId,
                                                r -> r));

                // 2️⃣ Remove reminders deleted in UI
                poi.getReminders().removeIf(existing -> reminderDTOs.stream()
                                .noneMatch(dto -> dto.getReminderId() != null &&
                                                dto.getReminderId().equals(existing.getReminderId())));

                // 3️⃣ Add or update reminders
                for (ReminderDTO dto : reminderDTOs) {

                        Reminder reminder = existingMap.get(dto.getReminderId());

                        // New reminder
                        if (reminder == null) {
                                reminder = new Reminder();
                                reminder.setReminderId(
                                                dto.getReminderId() != null && !dto.getReminderId().isEmpty()
                                                                ? dto.getReminderId()
                                                                : generate12DigitId());
                                reminder.setProofOfInvestment(poi);
                                poi.getReminders().add(reminder);
                        }

                        // Update editable fields
                        reminder.setEnabled(dto.isEnabled());
                        reminder.setNumberOfDays(dto.getNumberOfDays());
                }
        }

        private String generate12DigitId() {
                SecureRandom random = new SecureRandom();
                long min = 100_000_000_000L;
                long max = 999_999_999_999L;
                long number = min + (long) (random.nextDouble() * (max - min + 1));
                return String.valueOf(number);
        }
}