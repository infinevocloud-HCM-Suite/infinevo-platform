package com.itsdev.payroll.scheduler;

import com.itsdev.payroll.entity.claimsanddeclarations.IncomeTaxDeclaration;
import com.itsdev.payroll.repository.claimsanddeclarations.IncomeTaxDeclarationRepository;
import com.itsdev.payroll.service.claimsanddeclarations.ITDeclarationEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ITDeclarationAutoLockScheduler {

    private final IncomeTaxDeclarationRepository declarationRepository;
    private final ITDeclarationEmailService emailService; // optional but recommended

    // 🔥 Runs daily at 12:00 AM (after midnight payroll jobs)
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void autoLockExpiredDeclarations() {

        log.info("🔄 IT Declaration auto-lock job started");

        List<IncomeTaxDeclaration> activeList =
                declarationRepository.findAllActiveDeclarations();

        if (activeList.isEmpty()) {
            log.debug("No active IT declarations found for auto-lock");
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        int lockedCount = 0;

        for (IncomeTaxDeclaration decl : activeList) {
            try {
                LocalDate lastDate =
                        LocalDate.parse(decl.getLastDateForItDeclaration());

                // ✅ lock when deadline reached or passed
                if (!lastDate.isAfter(today)) {

                    decl.setItDeclarationLocked(true);
                    declarationRepository.save(decl);
                    lockedCount++;

                    log.info("🔒 Auto-locked IT declaration for org {}",
                            decl.getOrganization().getOrganizationId());

                    // ✅ OPTIONAL: send lock email
                    if (decl.isSendMailOnEmployeeLevelItLockAndRelease()
                            && decl.isSendMailOnItDeclarationLock()) {

                        emailService.sendLockEmails(
                                decl.getOrganization().getOrganizationId());
                    }
                }

            } catch (Exception ex) {
                log.error("❌ Failed to auto-lock declaration id={}",
                        decl.getId(), ex);
            }
        }

        log.info("✅ IT Declaration auto-lock job completed. Locked={}", lockedCount);
    }
}