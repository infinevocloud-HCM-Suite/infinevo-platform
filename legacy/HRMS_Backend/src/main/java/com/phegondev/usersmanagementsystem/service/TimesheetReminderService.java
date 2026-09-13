package com.phegondev.usersmanagementsystem.service;
 
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
 
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.Timesheet;
import com.phegondev.usersmanagementsystem.repository.AssignmentRepository;
import com.phegondev.usersmanagementsystem.repository.ProjectRepository;
import com.phegondev.usersmanagementsystem.repository.TaskRepository;
import com.phegondev.usersmanagementsystem.repository.TimesheetRepo;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
 
import jakarta.mail.MessagingException;
 
import org.springframework.beans.factory.annotation.Autowired;
 
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
 
@Service
public class TimesheetReminderService {
	
	private final TimesheetRepo timesheetRepo;
	private final  MailService mailService;
	private final UsersRepo usersRepo;
	
	  public TimesheetReminderService(TimesheetRepo timesheetRepo, MailService mailService,
			  UsersRepo usersRepo) {
	        this.timesheetRepo = timesheetRepo;
	        this.mailService = mailService;
	        this.usersRepo = usersRepo;
	    }
	  
	  
	  @Scheduled(cron = "0 59 23 ? * SAT")
	  public void remindEmployeesWithUnsubmittedTimesheets() {
	      System.out.println("=== Reminder Scheduler Triggered ===");
	      
	      LocalDate today = LocalDate.now();
	      LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
	      System.out.println("Today's date: " + today);
	      System.out.println("Current week start (Monday): " + currentWeekStart);
 
	      List<Timesheet> draftTimesheets = timesheetRepo.findByStatusAndWeekStart("DRAFT", currentWeekStart);
	      System.out.println("Total draft timesheets found: " + draftTimesheets.size());
 
	      String subject = "1st Reminder - Timesheet Submission Pending";
 
	      for (Timesheet ts : draftTimesheets) {
	          System.out.println("Processing Timesheet ID: " + ts.getTimesheetId());
 
	          try {
	              OurUsers employee = usersRepo.findByEmpId(ts.getEmpId());
	              System.out.println("Employee found: " + employee.getName() + " (Email: " + employee.getEmail() + ")");
 
	              String toEmail = employee.getEmail();
	              String emailBody =
	                  "Dear " + employee.getName() + ",\n\n" +
	                  "This is a gentle reminder that your timesheet (ID: " + ts.getTimesheetId() + ") submission is still pending.\n\n" +
	                  "Kindly ensure it is completed at the earliest to avoid any delays in processing.\n\n" +
	                  "Regards,\n" +
	                  "Timesheet Management Team";
 
	              System.out.println("Sending email to: " + toEmail);
	              mailService.sendEmail(toEmail, subject, emailBody);
	              System.out.println("✅ Email sent successfully to: " + toEmail);
 
	          } catch (Exception e) {
	              System.err.println("❌ Failed to send email for Timesheet ID: " + ts.getTimesheetId());
	              e.printStackTrace();
	          }
	      }
 
	      System.out.println("=== Reminder Scheduler Completed ===");
	  }
	  
	
	    
	// Reminder 2: Sunday EOD (12 AM) - To Employee and Supervisor
	  
	  @Scheduled(cron = "0 24 9 ? * FRI") // Every Thursday at 4:59 PM
	  public void sendSecondReminderToEmployeeAndSupervisor() {
	      try {
	          LocalDate today = LocalDate.now();
	          LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
 
	          System.out.println("Today's date: " + today);
	          System.out.println("Current week start (Monday): " + currentWeekStart);
 
	          List<Timesheet> draftTimesheets = timesheetRepo.findByStatusAndWeekStart("DRAFT", currentWeekStart);
	          System.out.println("Total draft timesheets found: " + draftTimesheets.size());
 
	          OurUsers supervisor;
	          try {
	              supervisor = usersRepo.findByRole("supervisor");
	              if (supervisor == null) {
	                  System.err.println("No supervisor found in the system.");
	                  return;
	              }
	          } catch (Exception e) {
	              System.err.println("Failed to fetch supervisor details: " + e.getMessage());
	              return;
	          }
	          
	          System.out.println("Sending reminder to supervisor: " + supervisor.getName() + " (Email: " + supervisor.getEmail() + ")");
 
	          for (Timesheet ts : draftTimesheets) {
	              try {
	                  OurUsers employee = usersRepo.findByEmpId(ts.getEmpId());
	                  if (employee == null) {
	                      System.err.println("No employee found with empId: " + ts.getEmpId());
	                      continue;
	                  }
 
	                  System.out.println("Sending reminder to: " + employee.getName() + " (Email: " + employee.getEmail() + ")");
 
	                  String employeeBody = "Dear " + employee.getName() + ",\n\n" +
	                          "This is your second reminder that your timesheet (ID: " + ts.getTimesheetId() + ") has not yet been submitted.\n\n" +
	                          "Please take immediate action to complete the submission. Your supervisor has been informed as well.\n\n" +
	                          "Timely submission of timesheets is crucial for accurate tracking and processing. Please ensure that your timesheet is submitted as soon as possible to avoid further escalation.\n\n" +
	                          "If you have already submitted it, kindly disregard this message.\n\n" +
	                          "Thank you for your attention to this matter.\n\n" +
	                          "Regards,\nTimesheet Management Team";
 
	                  mailService.sendEmail(employee.getEmail(), "2nd Reminder - Timesheet Submission Still Pending", employeeBody);
 
	                  String supervisorBody = "Dear " + supervisor.getName() + ",\n\n" +
	                          employee.getName() + " has not submitted their timesheet (ID: " + ts.getTimesheetId() + ").\n\n" +
	                          "Kindly follow up with them to ensure timely submission.\n\n" +
	                          "Regards,\nTimesheet Management Team";
 
	                  mailService.sendEmail(supervisor.getEmail(), "2nd Reminder - Timesheet Submission Still Pending", supervisorBody);
 
	              } catch (Exception e) {
	                  System.err.println("Error processing timesheet ID " + ts.getTimesheetId() + ": " + e.getMessage());
	                  e.printStackTrace();
	              }
	          }
 
	      } catch (Exception e) {
	          System.err.println("Exception in scheduled reminder task: " + e.getMessage());
	          e.printStackTrace();
	      }
	  }
	  
	  @Scheduled(cron = "0 51 9 ? * FRI") // Every Friday at 9:24 AM
	  public void sendSecondReminderToSupervisor() {
	      try {
	          LocalDate today = LocalDate.now();
	          LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
 
	          System.out.println("Today's date: " + today);
	          System.out.println("Current week start (Monday): " + currentWeekStart);
 
	          List<Timesheet> draftTimesheets = timesheetRepo.findByStatusAndWeekStart("DRAFT", currentWeekStart);
	          System.out.println("Total draft timesheets found: " + draftTimesheets.size());
 
	          OurUsers supervisor;
	          try {
	              supervisor = usersRepo.findByRole("supervisor");
	              if (supervisor == null) {
	                  System.err.println("No supervisor found in the system.");
	                  return;
	              }
	          } catch (Exception e) {
	              System.err.println("Failed to fetch supervisor details: " + e.getMessage());
	              return;
	          }
 
	          System.out.println("Preparing email for supervisor: " + supervisor.getName() + " (Email: " + supervisor.getEmail() + ")");
 
	          StringBuilder supervisorBody = new StringBuilder();
	          supervisorBody.append("Dear ").append(supervisor.getName()).append(",\n\n");
	          supervisorBody.append("The following employees have not submitted their timesheets for the week starting ")
	                        .append(currentWeekStart).append(":\n\n");
 
	          boolean hasUnsubmitted = false;
 
	          for (Timesheet ts : draftTimesheets) {
	              try {
	                  OurUsers employee = usersRepo.findByEmpId(ts.getEmpId());
	                  if (employee == null) {
	                      System.err.println("No employee found with empId: " + ts.getEmpId());
	                      continue;
	                  }
 
	                  supervisorBody.append(" - ").append(employee.getName())
	                                .append(" (Timesheet ID: ").append(ts.getTimesheetId()).append(")\n");
	                  hasUnsubmitted = true;
 
	              } catch (Exception e) {
	                  System.err.println("Error processing timesheet ID " + ts.getTimesheetId() + ": " + e.getMessage());
	                  e.printStackTrace();
	              }
	          }
 
	          if (hasUnsubmitted) {
	              supervisorBody.append("\nKindly follow up with them to ensure timely submission.\n\n")
	                            .append("Regards,\nTimesheet Management Team");
 
	              mailService.sendEmail(supervisor.getEmail(), "2nd Reminder - Timesheet Submission Still Pending", supervisorBody.toString());
	          } else {
	              System.out.println("No unsubmitted timesheets found. No email sent to supervisor.");
	          }
 
	      } catch (Exception e) {
	          System.err.println("Exception in scheduled reminder task: " + e.getMessage());
	          e.printStackTrace();
	      }
	  }
 
 
 
	  // Reminder 3: Monday 2 PM - To Supervisor
//	  @Scheduled(cron = "0 0 14 ? * MON")
	  @Scheduled(cron = "0 8 17 ? * THU")
 
	  public void sendThirdReminderToSupervisor() {
	      sendReminder("Action Required - Team Member's Timesheet Not Submitted", (ts, employee, supervisor) -> {
	          if (supervisor != null) {
	              String body = "Dear " + supervisor.getName() + ",\n\n" +
	                  employee.getName() + " has not submitted their timesheet (ID: " + ts.getTimesheetId() + ").\n\n" +
	                  "Kindly follow up with them to ensure timely submission.\n\n" +
	                  "Regards,\nTimesheet Management Team";
	              mailService.sendEmail(supervisor.getEmail(), "Action Required - Team Member's Timesheet Not Submitted", body);
	          }
	      });
	  }
 
	  // Reminder 4: Tuesday 2 PM - To Supervisor and HR
//	  @Scheduled(cron = "0 0 14 ? * TUE")
	  public void sendFourthReminderToSupervisorAndHR() {
	      sendReminder("Escalation - Timesheet Still Not Submitted", (ts, employee, supervisor) -> {
	          String body = "Dear Team,\n\n" +
	              "Please note that " + employee.getName() + " has yet to submit their timesheet (ID: " + ts.getTimesheetId() + "), despite multiple reminders.\n\n" +
	              "We request your intervention to resolve this matter.\n\n" +
	              "Regards,\nTimesheet Management Team";
	          mailService.sendEmail("hr@example.com", "Escalation - Timesheet Still Not Submitted", body);
	          if (supervisor != null) {
	              mailService.sendEmail(supervisor.getEmail(), "Escalation - Timesheet Still Not Submitted", body);
	          }
	      });
	  }
 
	  // Reminder 5: Wednesday 2 PM - To Supervisor and HR
	  @Scheduled(cron = "0 0 14 ? * WED")
	  public void sendFifthReminderToSupervisorAndHR() {
	      sendReminder("Continued Escalation - Pending Timesheet Submission", (ts, employee, supervisor) -> {
	          String body = "Dear Team,\n\n" +
	              "This is a further escalation regarding the pending timesheet (ID: " + ts.getTimesheetId() + ") of " + employee.getName() + ".\n\n" +
	              "Kindly take necessary action to close the pending submission.\n\n" +
	              "Regards,\nTimesheet Management Team";
	          mailService.sendEmail("hr@example.com", "Continued Escalation - Pending Timesheet Submission", body);
	          if (supervisor != null) {
	              mailService.sendEmail(supervisor.getEmail(), "Continued Escalation - Pending Timesheet Submission", body);
	          }
	      });
	  }
 
	  // Final Escalation: Thursday 2 PM - To Top Management
	  @Scheduled(cron = "0 0 14 ? * THU")
	  public void sendFinalEscalationToManagement() {
	      sendReminder("Final Escalation - Unsubmitted Timesheet", (ts, employee, supervisor) -> {
	          String body = "Dear Management,\n\n" +
	              "Despite multiple reminders and escalations, the timesheet (ID: " + ts.getTimesheetId() + ") for employee " + employee.getName() + " remains unsubmitted.\n\n" +
	              "We recommend immediate action as per organizational policy.\n\n" +
	              "Regards,\nTimesheet Management System";
	          mailService.sendEmail("management@example.com", "Final Escalation - Unsubmitted Timesheet", body);
	      });
	  }
 
	  // Utility method (assumed available):
	  private void sendReminder(String subject, ReminderAction action) {
	      LocalDate today = LocalDate.now();
	      LocalDate currentWeekStart = today.with(DayOfWeek.MONDAY);
	      List<Timesheet> draftTimesheets = timesheetRepo.findByStatusAndWeekStart("DRAFT", currentWeekStart);
 
	      for (Timesheet ts : draftTimesheets) {
	          try {
	              OurUsers employee = usersRepo.findByEmpId(ts.getEmpId());
	              OurUsers supervisor = usersRepo.findByEmpId(employee.getSupervisorId());
	              action.execute(ts, employee, supervisor);
	          } catch (Exception e) {
	              System.err.println("Error processing reminder for Timesheet ID: " + ts.getTimesheetId());
	              e.printStackTrace();
	          }
	      }
	  }
 
	  @FunctionalInterface
	  interface ReminderAction {
	      void execute(Timesheet ts, OurUsers employee, OurUsers supervisor) throws Exception;
	  }
 
	  
 
 
 
}