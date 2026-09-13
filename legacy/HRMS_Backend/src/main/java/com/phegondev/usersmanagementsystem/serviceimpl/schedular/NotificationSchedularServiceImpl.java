package com.phegondev.usersmanagementsystem.serviceimpl.schedular;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.phegondev.usersmanagementsystem.entity.timesheet.ProjectEntry;
import com.phegondev.usersmanagementsystem.entity.timesheet.Timesheets;
import com.phegondev.usersmanagementsystem.enumuration.TimesheetStatus;
import com.phegondev.usersmanagementsystem.repository.timesheet.TimesheetsRepo;
import com.phegondev.usersmanagementsystem.service.ProjectService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import com.phegondev.usersmanagementsystem.entity.Assignment;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Project;
import com.phegondev.usersmanagementsystem.entity.Timesheet;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.ApprovalReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EmployeeReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.EscalationReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.HrReminder;
import com.phegondev.usersmanagementsystem.entity.notificationconfig.SupervisorReminder;
import com.phegondev.usersmanagementsystem.enumuration.ReminderLevel;
import com.phegondev.usersmanagementsystem.repository.TimesheetRepo;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.ApprovalReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.EmployeeReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.EscalationReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.HrReminderRepo;
import com.phegondev.usersmanagementsystem.repository.notificationconfig.SupervisorReminderRepo;
import com.phegondev.usersmanagementsystem.service.MailService;
import com.phegondev.usersmanagementsystem.service.schedular.NotificationSchedularService;

@Service
public class NotificationSchedularServiceImpl implements NotificationSchedularService {

	private final TimesheetsRepo timesheetsRepo;
	private final ProjectService projectService;
	private final UsersRepo usersRepo;
	private final MailService mailService;

	private final ApprovalReminderRepo approvalReminderRepo;
	private final EmployeeReminderRepo employeeReminderRepo;
	private final EscalationReminderRepo escalationReminderRepo;
	private final HrReminderRepo hrReminderRepo;
	private final SupervisorReminderRepo supervisorReminderRepo;

	public NotificationSchedularServiceImpl(TimesheetsRepo timesheetsRepo,
			ProjectService projectService,
			UsersRepo usersRepo,
			MailService mailService,
			ApprovalReminderRepo approvalReminderRepo,
			EmployeeReminderRepo employeeReminderRepo,
			EscalationReminderRepo escalationReminderRepo,
			HrReminderRepo hrReminderRepo,
			SupervisorReminderRepo supervisorReminderRepo) {

		this.timesheetsRepo = timesheetsRepo;
		this.projectService = projectService;
		this.usersRepo = usersRepo;
		this.mailService = mailService;
		this.approvalReminderRepo = approvalReminderRepo;
		this.employeeReminderRepo = employeeReminderRepo;
		this.escalationReminderRepo = escalationReminderRepo;
		this.hrReminderRepo = hrReminderRepo;
		this.supervisorReminderRepo = supervisorReminderRepo;
	}


	@Transactional
	@Override
	public void runEmployeeReminder(EmployeeReminder reminder) {
		System.out.println("=== [runEmployeeReminder] STARTED ===");

		LocalDate today = LocalDate.now();
//		LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
//		LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
		LocalDate prevWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
		LocalDate prevWeekEnd = prevWeekStart.plusDays(6);
		Set<String> notifiedEmpProjectPairs = new HashSet<>();

		System.out.println("Today's date: " + today);
//		System.out.println("Current week range: " + currentWeekStart + " to " + currentWeekEnd);
		System.out.println("Previous week range: " + prevWeekStart + " to " + prevWeekEnd);

		String subject = getReminderSubject(reminder.getLevel());
		System.out.println("Reminder Level: " + reminder.getLevel() + ", Subject: " + subject);

		// Get all active projects
		List<Project> startedProjects = projectService.getProjectsByStatus("STARTED");

		// Notify employees with draft timesheets for specific projects
		notifyDraftTimesheetsByProject(startedProjects, prevWeekStart, prevWeekEnd, subject, notifiedEmpProjectPairs);

		// Notify employees missing timesheets for active projects
		notifyMissingTimesheetEmployeesByProject(startedProjects, prevWeekStart, prevWeekEnd, subject, notifiedEmpProjectPairs);

		reminder.setLastExecutedAt(LocalDateTime.now());
		employeeReminderRepo.save(reminder);

		System.out.println("=== [runEmployeeReminder] COMPLETED ===");
	}


	private void notifyDraftTimesheetsByProject(List<Project> activeProjects, LocalDate weekStart, LocalDate weekEnd,
												String subject, Set<String> notifiedEmpProjectPairs) {
		System.out.println("=== [notifyDraftTimesheetsByProject] STARTED ===");

		List<Timesheets> draftTimesheets = timesheetsRepo.findByStatusAndWeekStartDateBetween(
				TimesheetStatus.DRAFT, weekStart, weekEnd
		);

		System.out.println("Found DRAFT timesheets: " + draftTimesheets.size());

		// Group timesheets by employeeId -> projectId -> list
		Map<String, Map<Long, List<Timesheets>>> employeeProjectTimesheets = new HashMap<>();

		for (Timesheets ts : draftTimesheets) {
			String empId = ts.getEmployeeId();
			for (ProjectEntry pe : ts.getProjects()) {
				employeeProjectTimesheets
						.computeIfAbsent(empId, k -> new HashMap<>())
						.computeIfAbsent(pe.getProjectId(), k -> new ArrayList<>())
						.add(ts);
			}
		}

		for (Project project : activeProjects) {
			Long projectId = project.getId();
			for (Assignment assignment : project.getAssignments()) {
				String empId = assignment.getEmpId();
				String empProjKey = empId + "-" + projectId;

				if (notifiedEmpProjectPairs.contains(empProjKey)) {
					System.out.println("⏩ Skipping (already notified): " + empProjKey);
					continue;
				}

				if (employeeProjectTimesheets.containsKey(empId)
						&& employeeProjectTimesheets.get(empId).containsKey(projectId)) {
					try {
						OurUsers employee = usersRepo.findByEmpId(empId);
						if (employee == null) {
							System.err.println("⚠️ Employee not found: " + empId);
							continue;
						}

						List<Timesheets> tsList = employeeProjectTimesheets.get(empId).get(projectId);
						String tsIds = tsList.stream()
								.map(Timesheets::getTimesheetId)
								.collect(Collectors.joining(", "));

						String toEmail = employee.getEmail();
						String emailBody = String.format(
								"Dear %s,\n\nThis is a reminder that your timesheet(s) (IDs: %s) for project '%s' " +
										"for the week %s to %s is still in DRAFT status.\n\nPlease submit it at your earliest convenience.\n\n" +
										"Regards,\nTimesheet Management Team",
								employee.getName(), tsIds, project.getName(), weekStart, weekEnd
						);

						System.out.println("==== Email Details Draft ====");
						System.out.println("To: " + toEmail);
						System.out.println("Subject: " + subject);
						System.out.println("Body:\n" + emailBody);

						mailService.sendEmail(toEmail, subject, emailBody);
						System.out.println("✅ Draft reminder email sent to: " + toEmail);
						notifiedEmpProjectPairs.add(empProjKey);

					} catch (Exception e) {
						System.err.println("❌ Error sending DRAFT email to " + empId);
						e.printStackTrace();
					}
				}
			}
		}

		System.out.println("=== [notifyDraftTimesheetsByProject] COMPLETED ===");
	}


	private void notifyMissingTimesheetEmployeesByProject(List<Project> activeProjects, LocalDate weekStart,
														  LocalDate weekEnd, String subject, Set<String> notifiedEmpProjectPairs) {
		System.out.println("=== [notifyMissingTimesheetEmployeesByProject] STARTED ===");
		int assignmentsChecked = 0;

		for (Project project : activeProjects) {
			Long projectId = project.getId();
			for (Assignment assignment : project.getAssignments()) {
				assignmentsChecked++;
				String empId = assignment.getEmpId();
				String empProjKey = empId + "-" + projectId;

				if (notifiedEmpProjectPairs.contains(empProjKey)) {
					System.out.println("⏩ Skipping (already notified): " + empProjKey);
					continue;
				}

				// Check if employee has timesheet containing this project in this week
				List<Timesheets> timesheets = timesheetsRepo.findByEmployeeIdAndWeekStartDateBetween(empId, weekStart, weekEnd);
				boolean hasTimesheetForProject = timesheets.stream()
						.anyMatch(ts -> ts.getProjects().stream()
								.anyMatch(p -> p.getProjectId().equals(projectId)));

				if (!hasTimesheetForProject) {
					try {
						OurUsers employee = usersRepo.findByEmpId(empId);
						if (employee == null) {
							System.err.println("⚠️ Employee not found: " + empId);
							continue;
						}

						String toEmail = employee.getEmail();
						String emailBody = String.format(
								"Dear %s,\n\nYou have been assigned to project '%s' that is currently in progress, " +
										"but you have not yet created your timesheet for the week %s to %s.\n\n" +
										"Please create your timesheet as soon as possible.\n\nRegards,\nTimesheet Management Team",
								employee.getName(), project.getName(), weekStart, weekEnd
						);

						System.out.println("==== Email Details Missing ====");
						System.out.println("To: " + toEmail);
						System.out.println("Subject: " + subject);
						System.out.println("Body:\n" + emailBody);

						mailService.sendEmail(toEmail, subject, emailBody);
						System.out.println("✅ Missing timesheet email sent to: " + toEmail);
						notifiedEmpProjectPairs.add(empProjKey);

					} catch (Exception e) {
						System.err.println("❌ Error sending MISSING email to " + empId);
						e.printStackTrace();
					}
				} else {
					System.out.println("✔️ Timesheet exists for employeeId " + empId + " in projectId " + projectId);
				}
			}
		}

		System.out.println("Total assignments checked: " + assignmentsChecked);
		System.out.println("=== [notifyMissingTimesheetEmployeesByProject] COMPLETED ===");
	}



	@Transactional
	@Override
	public void runApprovalReminder(ApprovalReminder reminder) {
		System.out.println("=== Approval Reminder Scheduler Triggered ===");

		LocalDate today = LocalDate.now();
//		LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
//		LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
		LocalDate prevWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
		LocalDate prevWeekEnd = prevWeekStart.plusDays(6);

		System.out.println("Today's date: " + today);
//		System.out.println("Current week range: " + currentWeekStart + " to " + currentWeekEnd);
		System.out.println("Previous week range: " + prevWeekStart + " to " + prevWeekEnd);

		String subject = getReminderSubjectForApproval(reminder.getLevel());
		System.out.println("Reminder Level: " + reminder.getLevel() + ", Subject: " + subject);

		// 1. Fetch all timesheets with SUBMITTED status for current week
		List<Timesheets> submittedTimesheets = timesheetsRepo.findByStatusAndWeekStartDateBetween(
				TimesheetStatus.SUBMITTED,
				prevWeekStart,
				prevWeekEnd);

		if (submittedTimesheets.isEmpty()) {
			System.out.println("No submitted timesheets found for approval.");
			return;
		}

		// 2. Track which managers need to be notified for which projects
		Map<String, Map<Long, List<ProjectEntry>>> managerNotifications = new HashMap<>();

		// Initialize all projects collections before processing
		for (Timesheets timesheet : submittedTimesheets) {
			// This forces Hibernate to load the projects collection
			timesheet.getProjects().size();
		}

		for (Timesheets timesheet : submittedTimesheets) {
			for (ProjectEntry project : timesheet.getProjects()) {
				// Only process projects with SUBMITTED status
				if (project.getStatus() == TimesheetStatus.SUBMITTED) {
					// In a real implementation, you would fetch the manager ID from project or
					// associated data
					String managerId = projectService.getProjectManagerId(project.getProjectId()); // Implement this
																									// method

					managerNotifications
							.computeIfAbsent(managerId, k -> new HashMap<>())
							.computeIfAbsent(project.getProjectId(), k -> new ArrayList<>())
							.add(project);
				}
			}
		}

		if (managerNotifications.isEmpty()) {
			System.out.println("No projects requiring approval found.");
			return;
		}

		// 3. Send notifications to each manager
		for (Map.Entry<String, Map<Long, List<ProjectEntry>>> entry : managerNotifications.entrySet()) {
			String managerId = entry.getKey();
			Map<Long, List<ProjectEntry>> projectsMap = entry.getValue();

			try {
				OurUsers manager = usersRepo.findByEmpId(managerId);
				if (manager == null) {
					System.err.println("Manager not found with ID: " + managerId);
					continue;
				}

				StringBuilder body = new StringBuilder();
				body.append("Dear ").append(manager.getName()).append(",\n\n");
				body.append("The following project timesheets require your approval for the week of ")
						.append(prevWeekStart).append(" to ").append(prevWeekEnd).append(":\n\n");

				for (Map.Entry<Long, List<ProjectEntry>> projectEntry : projectsMap.entrySet()) {
					Long projectId = projectEntry.getKey();
					List<ProjectEntry> projects = projectEntry.getValue();

					body.append("Project: ").append(projects.get(0).getProjectName())
							.append(" (ID: ").append(projectId).append(")\n");
					body.append("Submitted by:\n");

					// List all employees who submitted this project
					projects.forEach(p -> {
						Timesheets ts = p.getTimesheet();
						body.append("- ").append(ts.getEmployeeName())
								.append(" (Timesheet ID: ").append(ts.getTimesheetId()).append(")\n");
					});
					body.append("\n");
				}

				body.append("\nPlease review and approve these submissions at your earliest convenience.")
						.append("\n\nRegards,\nTimesheet Management System");

				System.out.println("Sending approval request to manager: " + manager.getEmail());
				mailService.sendEmail(manager.getEmail(), subject, body.toString());

			} catch (Exception e) {
				System.err.println("Failed to process manager notification for ID: " + managerId);
				e.printStackTrace();
			}
		}

		// Update last execution time
		reminder.setLastExecutedAt(LocalDateTime.now());
		approvalReminderRepo.save(reminder);

		System.out.println("=== Approval Reminder Scheduler Completed ===");
	}


	@Transactional
	@Override
	public void runSupervisorReminder(SupervisorReminder reminder) {
		System.out.println("=== Project Manager Reminder Scheduler Triggered ===");

		LocalDate today = LocalDate.now();
//		LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
//		LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
		LocalDate prevWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
		LocalDate prevWeekEnd = prevWeekStart.plusDays(6);

		System.out.println("Today's date: " + today);
//		System.out.println("Current week range: " + currentWeekStart + " to " + currentWeekEnd);
		System.out.println("Previous week range: " + prevWeekStart + " to " + prevWeekEnd);

		String subject = getReminderSubject(reminder.getLevel());
		System.out.println("Reminder Level: " + reminder.getLevel() + ", Subject: " + subject);

		try {
			// Get all active projects
			List<Project> activeProjects = projectService.getProjectsByStatus("STARTED");

			// Map: ManagerEmail -> Map<ProjectId, List<EmployeeInfo>>
			Map<String, Map<Long, List<String>>> managerProjectMap = new HashMap<>();

			// Map: ManagerEmail -> Map<ProjectId, ProjectName>
			Map<String, Map<Long, String>> managerProjectNames = new HashMap<>();

			for (Project project : activeProjects) {
				Long projectId = project.getId();
				String managerEmail = projectService.getProjectManagerEmail(projectId);

				if (managerEmail == null || managerEmail.isEmpty()) {
					System.out.println("⚠️ Manager email missing for project: " + project.getName());
					continue;
				}

				List<String> pendingEmployees = new ArrayList<>();

				for (Assignment assignment : project.getAssignments()) {
					String empId = assignment.getEmpId();
					OurUsers employee = usersRepo.findByEmpId(empId);
					if (employee == null) {
						System.err.println("⚠️ Employee not found: " + empId);
						continue;
					}

					List<Timesheets> timesheets = timesheetsRepo.findByEmployeeIdAndWeekStartDateBetween(empId, prevWeekStart, prevWeekEnd);

					boolean submittedForProject = timesheets.stream()
							.anyMatch(ts -> ts.getStatus() == TimesheetStatus.SUBMITTED &&
									ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

					boolean draftForProject = timesheets.stream()
							.anyMatch(ts -> ts.getStatus() == TimesheetStatus.DRAFT &&
									ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

					if (!submittedForProject && (draftForProject || timesheets.isEmpty())) {
						String timesheetId = timesheets.stream()
								.filter(ts -> ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)))
								.map(Timesheets::getTimesheetId)
								.findFirst()
								.orElse("N/A");

						String info = "- " + employee.getName() +
								" (Employee ID: " + employee.getEmpId() +
								", Timesheet ID: " + timesheetId + ")";
						pendingEmployees.add(info);
					}
				}

				if (!pendingEmployees.isEmpty()) {
					managerProjectMap
							.computeIfAbsent(managerEmail, k -> new HashMap<>())
							.computeIfAbsent(projectId, k -> new ArrayList<>())
							.addAll(pendingEmployees);

					managerProjectNames
							.computeIfAbsent(managerEmail, k -> new HashMap<>())
							.putIfAbsent(projectId, project.getName());
				}
			}

			// Send emails to each manager
			for (String managerEmail : managerProjectMap.keySet()) {
				Map<Long, List<String>> projects = managerProjectMap.get(managerEmail);
				Map<Long, String> projectNames = managerProjectNames.get(managerEmail);

				StringBuilder body = new StringBuilder();

				body.append("Dear Project Manager,\n");
				body.append("The following projects have pending timesheet submissions for the\n");
				body.append("Week: ").append(prevWeekStart).append(" to ").append(prevWeekEnd).append("\n\n");

				for (Map.Entry<Long, List<String>> entry : projects.entrySet()) {
					Long projectId = entry.getKey();
					String projectName = projectNames.get(projectId);
					List<String> employees = entry.getValue();

					body.append("────────────────────────────────────────\n");
					body.append("Project Name: ").append(projectName).append("\n");
					body.append("Project ID  : ").append(projectId).append("\n");
					body.append("Pending Employees:\n");

					for (String empInfo : employees) {
						body.append("  ").append(empInfo).append("\n");
					}

					body.append("────────────────────────────────────────\n\n");
				}

				body.append("Please follow up with the above employees to ensure timely submission.\n\n");
				body.append("Regards,\nTimesheet Management System");

				try {
					System.out.println("==== Email to Manager====");
					System.out.println("To: " + managerEmail);
					System.out.println("Subject: " + subject);
					System.out.println("Body:\n" + body.toString());

					System.out.println("Sending reminder to manager: " + managerEmail);
					mailService.sendEmail(managerEmail, subject, body.toString());
				} catch (Exception e) {
					System.err.println("❌ Failed to send reminder to manager: " + managerEmail);
					e.printStackTrace();
				}
			}

			reminder.setLastExecutedAt(LocalDateTime.now());
			supervisorReminderRepo.save(reminder);

			System.out.println("✅ Project manager reminders sent successfully.");
		} catch (Exception e) {
			System.err.println("❌ Exception during Project Manager reminder execution: " + e.getMessage());
			e.printStackTrace();
		}

		System.out.println("=== Project Manager Reminder Scheduler Completed ===");
	}




	@Transactional
	@Override
	public void runHrReminder(HrReminder reminder) {
		System.out.println("=== [runHrReminder] STARTED ===");

		LocalDate today = LocalDate.now();
//		LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
//		LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
		LocalDate prevWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
		LocalDate prevWeekEnd = prevWeekStart.plusDays(6);

		System.out.println("Today's date: " + today);
//		System.out.println("Current week range: " + currentWeekStart + " to " + currentWeekEnd);
		System.out.println("Previous week range: " + prevWeekStart + " to " + prevWeekEnd);

		String subject = getReminderSubject(reminder.getLevel());
		System.out.println("Reminder Level: " + reminder.getLevel() + ", Subject: " + subject);

		List<Project> activeProjects = projectService.getProjectsByStatus("STARTED");
		Map<Project, List<OurUsers>> projectToPendingEmployees = new HashMap<>();

		for (Project project : activeProjects) {
			Long projectId = project.getId();
			List<OurUsers> pendingEmployees = new ArrayList<>();

			for (Assignment assignment : project.getAssignments()) {
				String empId = assignment.getEmpId();
				OurUsers employee = usersRepo.findByEmpId(empId);

				if (employee == null) {
					System.err.println("⚠️ Employee not found: " + empId);
					continue;
				}

				List<Timesheets> timesheets = timesheetsRepo.findByEmployeeIdAndWeekStartDateBetween(empId, prevWeekStart, prevWeekEnd);

				boolean submittedForProject = timesheets.stream()
						.anyMatch(ts -> ts.getStatus() == TimesheetStatus.SUBMITTED &&
								ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

				boolean draftForProject = timesheets.stream()
						.anyMatch(ts -> ts.getStatus() == TimesheetStatus.DRAFT &&
								ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

				if (!submittedForProject && (draftForProject || timesheets.isEmpty())) {
					pendingEmployees.add(employee);
				}
			}

			if (!pendingEmployees.isEmpty()) {
				projectToPendingEmployees.put(project, pendingEmployees);
			}
		}

		// Compose grouped email for HR
		if (!projectToPendingEmployees.isEmpty()) {
			StringBuilder bodyBuilder = new StringBuilder();
			bodyBuilder.append(String.format(
					"Dear HR Team,\n\nThe following employees have not yet submitted their timesheet for the week %s to %s for the following projects:\n\n",
					prevWeekStart, prevWeekEnd
			));

			for (Map.Entry<Project, List<OurUsers>> entry : projectToPendingEmployees.entrySet()) {
				Project project = entry.getKey();
				List<OurUsers> pendingEmployees = entry.getValue();

				bodyBuilder.append(String.format("Project '%s':\n", project.getName()));
				for (OurUsers emp : pendingEmployees) {
					bodyBuilder.append(String.format(" - %s (%s) | %s\n", emp.getName(), emp.getEmpId(), emp.getEmail()));
				}
				bodyBuilder.append("\n");
			}

			bodyBuilder.append("Please take necessary follow-up.\n\nRegards,\nTimesheet Management System");

			String emailBody = bodyBuilder.toString();

			// Send to all HR recipients
			for (String hrRecipient : reminder.getRecipients()) {
				try {
					System.out.println("==== Email to HR ====");
					System.out.println("To: " + hrRecipient);
					System.out.println("Subject: " + subject);
					System.out.println("Body:\n" + emailBody);

					System.out.println("Sending HR alert to: " + hrRecipient);
					mailService.sendEmail(hrRecipient, subject, emailBody);
					System.out.println("✅ HR notification sent successfully to: " + hrRecipient);
				} catch (Exception e) {
					System.err.println("❌ Failed to send HR notification to: " + hrRecipient);
					e.printStackTrace();
				}
			}
		}else {
			System.out.println("✅ No pending employees found for HR.");
		}

		reminder.setLastExecutedAt(LocalDateTime.now());
		hrReminderRepo.save(reminder);

		System.out.println("=== [runHrReminder] COMPLETED ===");
	}




	@Transactional
	@Override
	public void runEscalationReminder(EscalationReminder reminder) {
		System.out.println("=== [runEscalationReminder] STARTED ===");

		LocalDate today = LocalDate.now();
//		LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
//		LocalDate currentWeekEnd = currentWeekStart.plusDays(6);
		LocalDate prevWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY);
		LocalDate prevWeekEnd = prevWeekStart.plusDays(6);

		System.out.println("Today's date: " + today);
//		System.out.println("Current week range: " + currentWeekStart + " to " + currentWeekEnd);
		System.out.println("Previous week range: " + prevWeekStart + " to " + prevWeekEnd);

		String subject = "Escalation Reminder - Timesheet Submission Pending";

		// Fetch all active projects
		List<Project> activeProjects = projectService.getProjectsByStatus("STARTED");

		// Group employees with pending timesheets by project
		Map<Project, List<OurUsers>> projectToPendingEmployees = new HashMap<>();

		for (Project project : activeProjects) {
			Long projectId = project.getId();
			List<OurUsers> pendingEmployees = new ArrayList<>();

			for (Assignment assignment : project.getAssignments()) {
				String empId = assignment.getEmpId();
				OurUsers employee = usersRepo.findByEmpId(empId);

				if (employee == null) {
					System.err.println("⚠️ Employee not found: " + empId);
					continue;
				}

				List<Timesheets> timesheets = timesheetsRepo.findByEmployeeIdAndWeekStartDateBetween(empId, prevWeekStart, prevWeekEnd);

				boolean submittedForProject = timesheets.stream()
						.anyMatch(ts -> ts.getStatus() == TimesheetStatus.SUBMITTED &&
								ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

				boolean draftForProject = timesheets.stream()
						.anyMatch(ts -> ts.getStatus() == TimesheetStatus.DRAFT &&
								ts.getProjects().stream().anyMatch(p -> p.getProjectId().equals(projectId)));

				if (!submittedForProject && (draftForProject || timesheets.isEmpty())) {
					pendingEmployees.add(employee);
				}
			}

			if (!pendingEmployees.isEmpty()) {
				projectToPendingEmployees.put(project, pendingEmployees);
			}
		}

		// Send email if any pending employees found
		if (!projectToPendingEmployees.isEmpty()) {
			StringBuilder bodyBuilder = new StringBuilder();
			bodyBuilder.append(String.format(
					"Dear Escalation Team,\n\nThe following employees have not yet submitted their timesheet for the week %s to %s for the following projects:\n\n",
					prevWeekStart, prevWeekEnd
			));

			for (Map.Entry<Project, List<OurUsers>> entry : projectToPendingEmployees.entrySet()) {
				Project project = entry.getKey();
				List<OurUsers> pendingEmployees = entry.getValue();

				bodyBuilder.append(String.format("Project '%s':\n", project.getName()));
				for (OurUsers emp : pendingEmployees) {
					bodyBuilder.append(String.format(" - %s (%s) | %s\n", emp.getName(), emp.getEmpId(), emp.getEmail()));
				}
				bodyBuilder.append("\n");
			}

			bodyBuilder.append("Please take necessary action.\n\nRegards,\nTimesheet Management System");
			String emailBody = bodyBuilder.toString();

			// Send to each escalation recipient
			for (String recipient : reminder.getRecipients()) {
				try {
					System.out.println("Sending escalation to: " + recipient);
					mailService.sendEmail(recipient, subject, emailBody);
					System.out.println("✅ Escalation email sent to: " + recipient);
				} catch (Exception e) {
					System.err.println("❌ Failed to send escalation to: " + recipient);
					e.printStackTrace();
				}
			}
		} else {
			System.out.println("✅ No pending employees found for escalation.");
		}

		reminder.setLastExecutedAt(LocalDateTime.now());
		escalationReminderRepo.save(reminder);

		System.out.println("=== [runEscalationReminder] COMPLETED ===");
	}


	private String getReminderSubject(ReminderLevel level) {
		switch (level) {
			case LEVEL_1:
				return "1st Reminder - Timesheet Submission Pending";
			case LEVEL_2:
				return "2nd Reminder - Timesheet Submission Still Pending";
			default:
				return "Reminder - Timesheet Submission Pending";
		}
	}

	private String getReminderSubjectForApproval(ReminderLevel level) {

		switch (level) {
			case LEVEL_1:
				return "1st Reminder - Timesheet Approval Pending";
			case LEVEL_2:
				return "2nd Reminder - Timesheet Approval Still Pending";
			case LEVEL_3:
				return "3rd Reminder - Timesheet Approval Still Pending";
			default:
				return "Reminder - Timesheet Approval Pending";

		}
	}

}
