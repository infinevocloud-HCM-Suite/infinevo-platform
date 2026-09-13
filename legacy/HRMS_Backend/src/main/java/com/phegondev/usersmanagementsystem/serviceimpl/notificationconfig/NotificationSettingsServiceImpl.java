package com.phegondev.usersmanagementsystem.serviceimpl.notificationconfig;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phegondev.usersmanagementsystem.dto.notificationconfig.ApprovalReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.EmployeeReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.EscalationReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.HrReminderDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.NotificationSettingsDto;
import com.phegondev.usersmanagementsystem.dto.notificationconfig.SupervisorReminderDto;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.ApprovalReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EmployeeReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EscalationReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.HrReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.SupervisorReminder;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.ApprovalReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.EmployeeReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.EscalationReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.HrReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.SupervisorReminderRepo;
import com.phegondev.usersmanagementsystem.service.notificationconfig.NotificationSettingsService;
@Service
@Transactional(rollbackFor = Exception.class)
public class NotificationSettingsServiceImpl implements NotificationSettingsService {

    private final HrReminderRepo hrReminderRepo;
    private final SupervisorReminderRepo supervisorReminderRepo;
    private final EmployeeReminderRepo employeeReminderRepo;
    private final ApprovalReminderRepo approvalReminderRepo;
    private final EscalationReminderRepo escalationReminderRepo;

    public NotificationSettingsServiceImpl(
        HrReminderRepo hrReminderRepo,
        SupervisorReminderRepo supervisorReminderRepo,
        EmployeeReminderRepo employeeReminderRepo,
        ApprovalReminderRepo approvalReminderRepo,
        EscalationReminderRepo escalationReminderRepo
    ) {
        this.hrReminderRepo = hrReminderRepo;
        this.supervisorReminderRepo = supervisorReminderRepo;
        this.employeeReminderRepo = employeeReminderRepo;
        this.approvalReminderRepo = approvalReminderRepo;
        this.escalationReminderRepo = escalationReminderRepo;
    }

    @Override
    public void saveAll(NotificationSettingsDto dto) {
        System.out.println("Starting saveAll method...");

        // Clear existing data if needed (optional)
        System.out.println("Clearing existing reminders...");
        hrReminderRepo.deleteAllInBatch();
        supervisorReminderRepo.deleteAllInBatch();
        employeeReminderRepo.deleteAllInBatch();
        approvalReminderRepo.deleteAllInBatch();
        escalationReminderRepo.deleteAllInBatch();

        System.out.println("Existing reminders cleared.");

        // Save new data
        System.out.println("Saving HR reminders...");
        hrReminderRepo.saveAll(mapHrReminderDtos(dto.getHrReminders()));
        System.out.println("Saved HR reminders.");

        System.out.println("Saving Supervisor reminders...");
        supervisorReminderRepo.saveAll(mapSupervisorReminderDtos(dto.getSupervisorReminders()));
        System.out.println("Saved Supervisor reminders.");

        System.out.println("Saving Employee reminders...");
        employeeReminderRepo.saveAll(mapEmployeeReminderDtos(dto.getEmployeeReminders()));
        System.out.println("Saved Employee reminders.");

        System.out.println("Saving Approval reminders...");
        approvalReminderRepo.saveAll(mapApprovalReminderDtos(dto.getApprovalReminders()));
        System.out.println("Saved Approval reminders.");

        if (dto.getEscalationSettings() != null) {
            System.out.println("Saving Escalation settings...");
            escalationReminderRepo.save(mapEscalationReminderDto(dto.getEscalationSettings()));
            System.out.println("Saved Escalation settings.");
        }

        System.out.println("Finished saving all notification settings.");
    }
    

    @Override
    public NotificationSettingsDto getAllSettings() {
        NotificationSettingsDto dto = new NotificationSettingsDto();

        dto.setHrReminders(mapHrReminders(hrReminderRepo.findAll()));
        dto.setSupervisorReminders(mapSupervisorReminders(supervisorReminderRepo.findAll()));
        dto.setEmployeeReminders(mapEmployeeReminders(employeeReminderRepo.findAll()));
        dto.setApprovalReminders(mapApprovalReminders(approvalReminderRepo.findAll()));

        escalationReminderRepo.findAll().stream().findFirst().ifPresent(
            e -> dto.setEscalationSettings(mapEscalationReminder(e))
        );

        return dto;
    }
    
    @Override
    public boolean isEscalationReminderPresent() {
        return escalationReminderRepo.count() > 0;
    }



    private List<HrReminderDto> mapHrReminders(List<HrReminder> entities) {
        return entities.stream().map(e -> new HrReminderDto(
            e.getId(), e.isEnabled(), e.getDay(), e.getTime(), e.getLevel(),
            e.getRecipients(), e.getCreatedAt(), e.getUpdatedAt()
        )).toList();
    }

    private List<SupervisorReminderDto> mapSupervisorReminders(List<SupervisorReminder> entities) {
        return entities.stream().map(e -> new SupervisorReminderDto(
            e.getId(), e.isEnabled(), e.getDay(), e.getTime(), e.getLevel(),
            e.getRecipients(), e.getCreatedAt(), e.getUpdatedAt()
        )).toList();
    }

    private List<EmployeeReminderDto> mapEmployeeReminders(List<EmployeeReminder> entities) {
        return entities.stream().map(e -> new EmployeeReminderDto(
            e.getId(), e.isEnabled(), e.getDay(), e.getTime(), e.getLevel(),
            e.getCreatedAt(), e.getUpdatedAt()
        )).toList();
    }

    private List<ApprovalReminderDto> mapApprovalReminders(List<ApprovalReminder> entities) {
        return entities.stream().map(e -> new ApprovalReminderDto(
            e.getId(), e.isEnabled(), e.getDay(), e.getTime(), e.getLevel(),
            e.getRecipients(), e.getCreatedAt(), e.getUpdatedAt()
        )).toList();
    }

    private EscalationReminderDto mapEscalationReminder(EscalationReminder e) {
        return new EscalationReminderDto(
            e.getId(), e.isEnabled(), e.getDay(), e.getTime(),
            e.getRecipients(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }



    private List<HrReminder> mapHrReminderDtos(List<HrReminderDto> dtos) {
        System.out.println("Mapping HR reminders DTOs to entities...");
        return dtos.stream().map(dto -> {
            HrReminder entity = new HrReminder();
            entity.setEnabled(dto.isEnabled());
            entity.setLevel(dto.getLevel());
            entity.setDay(dto.getDay());
            entity.setTime(dto.getTime());
            entity.setRecipients(dto.getRecipients());
            return entity;
        }).toList();
    }

    private EscalationReminder mapEscalationReminderDto(EscalationReminderDto dto) {
        System.out.println("Mapping Escalation reminder DTO to entity...");
        EscalationReminder entity = new EscalationReminder();
        entity.setEnabled(dto.isEnabled());
        entity.setDay(dto.getDay());
        entity.setTime(dto.getTime());
        entity.setRecipients(dto.getRecipients());
        return entity;
    }

    private List<SupervisorReminder> mapSupervisorReminderDtos(List<SupervisorReminderDto> dtos) {
        System.out.println("Mapping Supervisor reminders DTOs to entities...");
        return dtos.stream().map(dto -> {
            SupervisorReminder entity = new SupervisorReminder();
            entity.setEnabled(dto.isEnabled());
            entity.setLevel(dto.getLevel());
            entity.setDay(dto.getDay());
            entity.setTime(dto.getTime());
            entity.setRecipients(dto.getRecipients());
            return entity;
        }).toList();
    }

    private List<ApprovalReminder> mapApprovalReminderDtos(List<ApprovalReminderDto> dtos) {
        System.out.println("Mapping Approval reminders DTOs to entities...");
        return dtos.stream().map(dto -> {
            ApprovalReminder entity = new ApprovalReminder();
            entity.setEnabled(dto.isEnabled());
            entity.setLevel(dto.getLevel());
            entity.setDay(dto.getDay());
            entity.setTime(dto.getTime());
            entity.setRecipients(dto.getRecipients());
            return entity;
        }).toList();
    }

    private List<EmployeeReminder> mapEmployeeReminderDtos(List<EmployeeReminderDto> dtos) {
        System.out.println("Mapping Employee reminders DTOs to entities...");
        return dtos.stream().map(dto -> {
            EmployeeReminder entity = new EmployeeReminder();
            entity.setEnabled(dto.isEnabled());
            entity.setLevel(dto.getLevel());
            entity.setDay(dto.getDay());
            entity.setTime(dto.getTime());
            return entity;
        }).toList();
    }
}
