package com.phegondev.usersmanagementsystem.service.schedular;

import com.phegondev.usersmanagementsystem.entity.notificationconfig.ApprovalReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EmployeeReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EscalationReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.HrReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.SupervisorReminder;

public interface NotificationSchedularService {
	
	    void runEmployeeReminder(EmployeeReminder reminder);
	    void runApprovalReminder(ApprovalReminder reminder);
        void runSupervisorReminder(SupervisorReminder reminder);
	    void runHrReminder(HrReminder reminder);
	    void runEscalationReminder(EscalationReminder reminder);

}
