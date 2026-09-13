package com.phegondev.usersmanagementsystem.serviceimpl.useraccess;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phegondev.usersmanagementsystem.dto.useraccess.ActionDTO;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.useraccess.Action;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;
import com.phegondev.usersmanagementsystem.entity.useraccess.UserActionMapping;
import com.phegondev.usersmanagementsystem.exceptions.useraccess.ActionAlreadyExistsException;
import com.phegondev.usersmanagementsystem.exceptions.useraccess.ActionDeletionException;
import com.phegondev.usersmanagementsystem.exceptions.useraccess.ActionNotFoundException;
import com.phegondev.usersmanagementsystem.repository.useraccess.ActionRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.RoleRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.UserActionMappingRepository;
import com.phegondev.usersmanagementsystem.service.useraccess.ActionService;



@Service
@Transactional(rollbackFor = Exception.class)
public class ActionServiceImpl implements ActionService {

    private final ActionRepo actionRepository;
    private final RoleRepo roleRepository;
    private final UserActionMappingRepository userActionMappingRepository;

    public ActionServiceImpl(ActionRepo actionRepository, RoleRepo roleRepository, UserActionMappingRepository userActionMappingRepository) {
        this.actionRepository = actionRepository;
        this.roleRepository = roleRepository;
        this.userActionMappingRepository = userActionMappingRepository;
    }

    public void createAction(ActionDTO actionDTO) {
        if (actionRepository.existsByActionName(actionDTO.getActionName())) {
            throw new ActionAlreadyExistsException("Action with name " + actionDTO.getActionName() + " already exists!");
        }

        String originalName = actionDTO.getActionName();
        String alias = originalName.trim().toUpperCase().replace(" ", "_");

        System.out.println("Original Name: " + originalName);
        System.out.println("Generated Alias: " + alias);

        Action action = new Action();
        action.setActionName(originalName);
        action.setAlias(alias);
        action.setDescription(actionDTO.getDescription());
        actionRepository.save(action);
    }


    @Override
    public List<ActionDTO> getAllActions() {
        List<Action> actions = actionRepository.findAll();
        return actions.stream()
                .map(action -> new ActionDTO(
                        action.getActionId(),
                        action.getActionName(),
                        action.getAlias(),
                        action.getDescription()))
                .collect(Collectors.toList());
    }

    @Override
    public ActionDTO getActionById(Long actionId) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException("Action not found with actionId: " + actionId));
        return new ActionDTO(
                action.getActionId(),
                action.getActionName(),
                action.getAlias(),
                action.getDescription());
    }

    @Override
    public ActionDTO updateAction(Long actionId, ActionDTO actionDTO) {
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException("Action not found with actionId: " + actionId));

        action.setActionName(actionDTO.getActionName());
        action.setDescription(actionDTO.getDescription());
        Action updatedAction = actionRepository.save(action);

        return new ActionDTO(
                updatedAction.getActionId(),
                updatedAction.getActionName(),
                updatedAction.getAlias(),
                updatedAction.getDescription());
    }

    @Override
    public void deleteActionById(Long id) {
        System.out.println("Fetching Action with ID: " + id);
        Action action = actionRepository.findById(id).orElseThrow(() -> 
            new ActionNotFoundException("Action not found with ID: " + id));

        System.out.println("Checking associations for Action ID: " + id);

   
        List<Role> associatedRoles = roleRepository.findRolesByActionId(id);
        List<String> roleNames = associatedRoles.stream()
                                                 .map(Role::getRoleName)
                                                 .toList();
        if (!roleNames.isEmpty()) {
            System.out.println("Associated Roles for Action ID " + id + ": " + roleNames);
        } else {
            System.out.println("No associated roles for Action ID: " + id);
        }

     
        List<UserActionMapping> associatedUsers = userActionMappingRepository.findByActionId(id);
        List<String> usernames = associatedUsers.stream()
                                                 .map(user -> user.getUser().getName())
                                                 .toList();
        if (!usernames.isEmpty()) {
            System.out.println("Associated Users for Action ID " + id + ": " + usernames);
        } else {
            System.out.println("No associated users for Action ID: " + id);
        }

     
        if (!roleNames.isEmpty() || !usernames.isEmpty()) {
            String message = "Please remove mapping before delete Action as it is associated with ";
            if (!roleNames.isEmpty() && usernames.isEmpty()) {
                message += "roles.";
            } else if (roleNames.isEmpty() && !usernames.isEmpty()) {
                message += "users.";
            } else {
                message += "roles and users.";
            }
            System.out.println("Deletion blocked for Action ID " + id + ". " + message);
            throw new ActionDeletionException(message, roleNames, usernames);
        }

    
        System.out.println("Deleting Action with ID: " + id);
        actionRepository.delete(action);
        System.out.println("Action deleted successfully for ID: " + id);
    }

    @Override
    public void createActionIfNotExist() {

        System.out.println("Checking and creating actions...");

        List<Action> predefinedActions = List.of(

                //Actions for everyone
                new Action("Change Password", "CHANGE_PASSWORD", "TO Change Password"),
                new Action("View Settings", "VIEW_SETTINGS", "View Setting Section"),

                // Actions for Dashboard Module
                new Action("View Admin Dashboard", "VIEW_ADMIN_DASHBOARD", "View Admin dashboard"),
                new Action("View HR Dashboard", "VIEW_HR_DASHBOARD", "View HR dashboard"),
                new Action("View Manager Dashboard", "VIEW_MANAGER_DASHBOARD", "View Manager dashboard"),
                new Action("View Supervisor Dashboard", "VIEW_SUPERVISOR_DASHBOARD", "View Supervisor dashboard"),
                new Action("View Employee Dashboard", "VIEW_EMPLOYEE_DASHBOARD", "View Employee dashboard"),
                new Action("View Reporting Manager Dashboard", "VIEW_REPORTING_MANAGER_DASHBOARD", "View Reporting Manager dashboard"),

            
                // Actions for Employee Modules
                new Action("Add Employee", "ADD_EMPLOYEE", "Adding a new employee"),
                new Action("update employee", "UPDATE_EMPLOYEE", "Updating the existing employee"),
                new Action("delete employee", "DELETE_EMPLOYEE", "Deleting an employee"),
                new Action("view employee", "VIEW_EMPLOYEE", "Viewing an employee data"),
                new Action("Export Employee Data", "EXPORT_EMPLOYEE_DATA", "Exporting employee data to CSV"),
                new Action("Manage Employees", "MANAGE_EMPLOYEES", "Complete Employee Management"),

                // Actions for User Management and User Access Modules
                new Action("Create User", "CREATE_USER", "Creating a user"),
                new Action("Update User", "UPDATE_USER", "Updating an existing user"),
                new Action("Delete User", "DELETE_USER", "Deleting a user"),
                new Action("Granting Permissions", "GRANTING_PERMISSIONS", "Granting permissions to user"),
                new Action("Block Unblock User", "BLOCK_UNBLOCK_USER", "Block or unblock a user"),
                new Action("Manage Users Management", "MANAGE_USERS_MANAGEMENT", "Complete User Management"),

                // Actions for Action Submodule
                new Action("Create Actions", "CREATE_ACTIONS", "Create new actions"),
                new Action("Update Actions", "UPDATE_ACTIONS", "Update an existing actions"),
                new Action("Delete Actions", "DELETE_ACTIONS", "Delete an existing actions"),
                new Action("View List Actions", "VIEW_LIST_ACTIONS", "View list of actions"),

                // Actions for Role Management Submodule
                new Action("Create Role", "CREATE_ROLE", "Create new role"),
                new Action("Update Role", "UPDATE_ROLE", "Update an existing role"),
                new Action("Permission Mapping", "PERMISSION_MAPPING", "Mapping actions permission to a role"),
                new Action("Delete Role", "DELETE_ROLE", "Delete an existing role"),
                new Action("View List Roles", "VIEW_LIST_ROLES", "View list of roles"),

                // Actions for Projects and Tasks module
                // From authority side
                new Action("create project", "CREATE_PROJECT", "Create a project"),
                new Action("assign employee", "ASSIGN_EMPLOYEE", "Assigning employees to a project"),
                new Action("view assign employee", "VIEW_ASSIGN_EMPLOYEE", "View Assigned employees to a project"),
                new Action("update project", "UPDATE_PROJECT", "Update an existing project"),
                new Action("delete project", "DELETE_PROJECT", "Delete an existing project"),
                new Action("Manage Projects", "MANAGE_PROJECTS", "Complete Project management"),
                // From manager side
                new Action("View My Team", "VIEW_MY_TEAM", "View my team members"),
                // From Reporting Manager side
                new Action("View Reporting Team", "VIEW_REPORTING_TEAM", "View reporting team members"),

                new Action("create task", "CREATE_TASK", "Create a task"),
                new Action("update task", "UPDATE_TASK", "Update an existing task"),
                new Action("delete task", "DELETE_TASK", "Delete an existing task"),
                new Action("Manage Tasks", "MANAGE_TASKS", "Complete Task Management"),

                // From employee side
                new Action("View Project", "VIEW_PROJECT", "View assigned project to employee"),
                new Action("View Task", "VIEW_TASK", "View assigned task to employee"),

                // Actions for Time & Management Modules
                // From employee side
                new Action("Add Entry", "ADD_ENTRY", "Add timesheet entry"),
                new Action("Update Timesheet", "UPDATE_TIMESHEET", "Update timesheet entry"),
                new Action("Delete Timesheet", "DELETE_TIMESHEET", "Delete timesheet entry"),
                new Action("Cancel Timesheet", "CANCEL_TIMESHEET", "Cancel timesheet entry"),
                new Action("View Timesheet", "VIEW_TIMESHEET", "View timesheet entry"),
                new Action("Submit Timesheet", "SUBMIT_TIMESHEET", "Submit a timesheet entry"),
                new Action("Submit all Timesheet", "SUBMIT_ALL_TIMESHEET", "Submit all timesheet entries"),
                new Action("Manage Timesheet", "MANAGE_TIMESHEET", "Complete Timesheet management from employee side"),
                
                // From manager side
                new Action("Manager Manage Timesheets", "MANAGER_MANAGE_TIMESHEETS", "Manager will manage its all-teams timesheet."),
                new Action("Manager View Timesheet Report", "MANAGER_VIEW_TIMESHEET_REPORT", "Manager can view timesheet report of a team member."),

                // From reporting manager side
                new Action("Reporting Manager Manage Timesheets", "REPORTING_MANAGER_MANAGE_TIMESHEETS", "Reporting Manager will manage its all-teams timesheet."),

                // From authority side
                new Action("Manage Timesheets", "MANAGE_TIMESHEETS", "Complete Timesheet management from higher authority side"),
                new Action("Export Timesheet Data", "EXPORT_TIMESHEET_DATA", "Export timesheet data"),
                new Action("View Timesheet Report", "VIEW_TIMESHEET_REPORT", "View timesheet report"),
                new Action("Approve Timesheet", "APPROVE_TIMESHEET", "Approve timesheet entry"),
                new Action("Reject Timesheet", "REJECT_TIMESHEET", "Reject timesheet entry"),
                new Action("Notification settings", "NOTIFICATION_SETTINGS", "Manage notification settings for timesheet reminders"),

                // Actions for Attendance submodule Module
                // From employee side
                new Action("View Attendance", "VIEW_ATTENDANCE", "View attendance"),
                // new Action("Clock In", "CLOCK_IN", "Clock in for attendance"),
                // new Action("Clock Out", "CLOCK_OUT", "Clock out for attendance"),

                // From authority side
                new Action("Manage Attendances", "MANAGE_ATTENDANCES", "Complete Attendance management from higher authority side"),

                // Actions for Leave Management Modules and Overtime & Holiday Submodules
                // From Authority side
                new Action("Manage Leaves", "MANAGE_LEAVES", "Complete applied leaves management from higher authority side"),
                new Action("View Employees Leave", "VIEW_EMPLOYEES_LEAVE", "View employees leaves"),
                new Action("Edit Leave Status", "EDIT_LEAVE_STATUS", "Edit applied leaves status"),
                new Action("Approve Leave", "APPROVE_LEAVE", "Approve applied leaves"),
                new Action("Reject Leave", "REJECT_LEAVE", "Reject applied leaves"),
                new Action("Manage Employees Leaves Details", "MANAGE_EMPLOYEES_LEAVES_DETAILS", "Complete employees leaves details management from higher authority side"),
                new Action("View Employees Leaves Balances", "VIEW_EMPLOYEES_LEAVES_BALANCES", "View employees leaves balances"),
                new Action("View Leave Request Details", "VIEW_LEAVE_REQUEST_DETAILS", "all can View details of leave request except user"),

                new Action("Manage Leave Types", "MANAGE_LEAVE_TYPES", "Complete leave types management from higher authority side"),
                new Action("Manage Leave Balances", "MANAGE_LEAVE_BALANCES", "Complete leave balances management from higher authority side"),
                new Action("Edit Leave Types", "EDIT_LEAVE_TYPES", "Edit leave types"),
                new Action("Delete Leave Types", "DELETE_LEAVE_TYPES", "Delete leave types"),

                new Action("Manage Overtime Requests", "MANAGE_OVERTIME_REQUESTS", "Complete overtime requests management from higher authority side"),
                new Action("View Employees Overtime Requests", "VIEW_EMPLOYEES_OVERTIME_REQUESTS", "View employees overtime requests"),
                new Action("Edit Overtime Request Status", "EDIT_OVERTIME_REQUEST_STATUS", "Edit overtime request status"),
                new Action("Approve Overtime Request", "APPROVE_OVERTIME_REQUEST", "Approve overtime request"),
                new Action("Reject Overtime Request", "REJECT_OVERTIME_REQUEST", "Reject overtime request"),

                new Action("Manage Holidays", "MANAGE_HOLIDAYS", "Complete holidays management from higher authority side"),

                // From employee side
                new Action("Apply Leave", "APPLY_LEAVE", "Apply for leave"),
                new Action("View Leave", "VIEW_LEAVE", "View applied leave"),
                new Action("View Leave balance", "VIEW_LEAVE_BALANCE", "Employee can check their leave balance"),

                new Action("Apply Overtime Request", "APPLY_OVERTIME_REQUEST", "Apply for overtime request"),
                new Action("View Overtime Request", "VIEW_OVERTIME_REQUEST", "View applied overtime request"),

                new Action("View Holiday", "VIEW_HOLIDAY", "user can see their holiday"),

                // Actions for Profile Modules
                new Action("View Profile", "VIEW_PROFILE", "View user profile")
                
            );
        
        for (Action action : predefinedActions) {
            if (!actionRepository.existsByAlias(action.getAlias())){
                actionRepository.save(action);
                System.out.println("Created action: " + action.getActionName());
            }
        }
    }

    @Override
    public void mapDefaultActionsToRoles() {
        System.out.println("Mapping default actions to roles...");

        Role adminRole = roleRepository.findByRoleName("admin");
        Role hrRole = roleRepository.findByRoleName("hr");
        Role reportingManagerRole = roleRepository.findByRoleName("reporting manager");
        Role managerRole = roleRepository.findByRoleName("manager");
        Role supervisorRole = roleRepository.findByRoleName("supervisor");
        Role userRole = roleRepository.findByRoleName("user");

        List<Action> allActions = actionRepository.findAll();

        // Admin gets all actions
        // adminRole.setActionList(allActions);
        // System.out.println("Mapped all actions to ADMIN.");
        List<String> adminAliases = List.of("VIEW_ADMIN_DASHBOARD","MANAGE_EMPLOYEES","EXPORT_EMPLOYEE_DATA","ADD_EMPLOYEE","UPDATE_EMPLOYEE","DELETE_EMPLOYEE","VIEW_EMPLOYEE","MANAGE_USERS_MANAGEMENT","CREATE_USER","UPDATE_USER","GRANTING_PERMISSIONS","BLOCK_UNBLOCK_USER","DELETE_USER", "CREATE_ACTIONS","UPDATE_ACTIONS","DELETE_ACTIONS","VIEW_LIST_ACTIONS","CREATE_ROLE","UPDATE_ROLE","DELETE_ROLE","PERMISSION_MAPPING","VIEW_LIST_ROLES","MANAGE_PROJECTS","CREATE_PROJECT","ASSIGN_EMPLOYEE","VIEW_ASSIGN_EMPLOYEE","UPDATE_PROJECT","DELETE_PROJECT","MANAGE_TASKS","CREATE_TASK","UPDATE_TASK","DELETE_TASK","MANAGE_TIMESHEETS","EXPORT_TIMESHEET_DATA","NOTIFICATION_SETTINGS","VIEW_TIMESHEET_REPORT","TIMESHEET_REMINDER","MANAGE_ATTENDANCES","MANAGE_LEAVES","VIEW_EMPLOYEES_LEAVE","EDIT_LEAVE_STATUS","APPROVE_LEAVE","REJECT_LEAVE","MANAGE_EMPLOYEES_LEAVES_DETAILS","VIEW_EMPLOYEES_LEAVES_BALANCES","MANAGE_LEAVE_TYPES","MANAGE_LEAVE_BALANCES","EDIT_LEAVE_TYPES","DELETE_LEAVE_TYPES","MANAGE_OVERTIME_REQUESTS","VIEW_EMPLOYEES_OVERTIME_REQUESTS","EDIT_OVERTIME_REQUEST_STATUS","APPROVE_OVERTIME_REQUEST","REJECT_OVERTIME_REQUEST","MANAGE_HOLIDAYS","VIEW_PROFILE","VIEW_SETTINGS","CHANGE_PASSWORD");
        List<Action> adminActions = allActions.stream().filter(a -> adminAliases.contains(a.getAlias())).collect(Collectors.toList());
        adminRole.setActionList(adminActions);
        System.out.println("Mapped actions to ADMIN.");


        // HR specific actions
        List<String> hrAliases = List.of("VIEW_HR_DASHBOARD","MANAGE_EMPLOYEES","EXPORT_EMPLOYEE_DATA","ADD_EMPLOYEE","UPDATE_EMPLOYEE","DELETE_EMPLOYEE","VIEW_EMPLOYEE", "MANAGE_USERS_MANAGEMENT","CREATE_USER","UPDATE_USER","GRANTING_PERMISSIONS","BLOCK_UNBLOCK_USER","DELETE_USER","VIEW_LIST_ACTIONS","PERMISSION_MAPPING","VIEW_LIST_ROLES","MANAGE_PROJECTS","CREATE_PROJECT","ASSIGN_EMPLOYEE","VIEW_ASSIGN_EMPLOYEE","UPDATE_PROJECT","DELETE_PROJECT","MANAGE_TASKS","MANAGE_TIMESHEETS","EXPORT_TIMESHEET_DATA","NOTIFICATION_SETTINGS","VIEW_TIMESHEET_REPORT","TIMESHEET_REMINDER","MANAGE_ATTENDANCES","MANAGE_LEAVES","VIEW_EMPLOYEES_LEAVE","EDIT_LEAVE_STATUS","APPROVE_LEAVE","REJECT_LEAVE","MANAGE_EMPLOYEES_LEAVES_DETAILS","VIEW_EMPLOYEES_LEAVES_BALANCES","VIEW_LEAVE_REQUEST_DETAILS","MANAGE_LEAVE_TYPES","MANAGE_LEAVE_BALANCES","EDIT_LEAVE_TYPES","DELETE_LEAVE_TYPES","MANAGE_OVERTIME_REQUESTS","VIEW_EMPLOYEES_OVERTIME_REQUESTS","EDIT_OVERTIME_REQUEST_STATUS","APPROVE_OVERTIME_REQUEST","REJECT_OVERTIME_REQUEST" ,"MANAGE_HOLIDAYS","VIEW_PROFILE","VIEW_SETTINGS","CHANGE_PASSWORD");
        List<Action> hrActions = allActions.stream().filter(a -> hrAliases.contains(a.getAlias())).collect(Collectors.toList());
        hrRole.setActionList(hrActions);
        System.out.println("Mapped actions to HR.");

        // Reporting Manager specific actions
        List<String> reportingManagerAliases = List.of("VIEW_REPORTING_MANAGER_DASHBOARD","VIEW_REPORTING_TEAM","REPORTING_MANAGER_MANAGE_TIMESHEETS","VIEW_TIMESHEET_REPORT","EXPORT_TIMESHEET_DATA","MANAGE_LEAVES","EDIT_LEAVE_STATUS","APPROVE_LEAVE","REJECT_LEAVE","VIEW_LEAVE_REQUEST_DETAILS","VIEW_HOLIDAY","VIEW_PROFILE","VIEW_SETTINGS","CHANGE_PASSWORD");
        List<Action> reportingManagerActions = allActions.stream().filter(a -> reportingManagerAliases.contains(a.getAlias())).collect(Collectors.toList());
        reportingManagerRole.setActionList(reportingManagerActions);
        System.out.println("Mapped actions to Reporting Manager.");

        // Manager specific actions
        List<String> managerAliases = List.of("VIEW_MANAGER_DASHBOARD","VIEW_MY_TEAM","MANAGE_PROJECTS","ASSIGN_EMPLOYEE","VIEW_ASSIGN_EMPLOYEE","MANAGE_TASKS","CREATE_TASK","UPDATE_TASK","DELETE_TASK","MANAGER_MANAGE_TIMESHEETS","MANAGER_VIEW_TIMESHEET_REPORT","APPROVE_TIMESHEET","REJECT_TIMESHEET","EXPORT_TIMESHEET_DATA","VIEW_LEAVE","MANAGE_OVERTIME_REQUESTS","VIEW_EMPLOYEES_OVERTIME_REQUESTS","EDIT_OVERTIME_REQUEST_STATUS","APPROVE_OVERTIME_REQUEST","REJECT_OVERTIME_REQUEST","VIEW_HOLIDAY","VIEW_PROFILE","VIEW_SETTINGS","CHANGE_PASSWORD");
        List<Action> managerActions = allActions.stream().filter(a -> managerAliases.contains(a.getAlias())).collect(Collectors.toList());
        managerRole.setActionList(managerActions);
        System.out.println("Mapped actions to Manager.");

        // Supervisor specific actions
        List<String> supervisorAliases = List.of("VIEW_SUPERVISOR_DASHBOARD","MANAGE_PROJECTS","VIEW_ASSIGN_EMPLOYEE","MANAGE_TASKS","VIEW_PROJECT","VIEW_TASK","MANAGE_TIMESHEET","ADD_ENTRY","UPDATE_TIMESHEET","DELETE_TIMESHEET","SUBMIT_TIMESHEET","SUBMIT_ALL_TIMESHEET","VIEW_TIMESHEET", "VIEW_ATTENDANCE","APPLY_LEAVE","VIEW_LEAVE", "VIEW_LEAVE_BALANCE","APPLY_OVERTIME_REQUEST","VIEW_OVERTIME_REQUEST","MANAGE_LEAVES","VIEW_EMPLOYEES_LEAVE","VIEW_HOLIDAY","VIEW_PROFILE","VIEW_SETTINGS","CHANGE_PASSWORD");
        List<Action> supervisorActions = allActions.stream().filter(a -> supervisorAliases.contains(a.getAlias())).collect(Collectors.toList());
        supervisorRole.setActionList(supervisorActions);
        System.out.println("Mapped actions to SUPERVISOR.");

        // User specific actions
        List<String> userAliases = List.of("VIEW_EMPLOYEE_DASHBOARD","VIEW_PROJECT","VIEW_TASK","MANAGE_TIMESHEET","ADD_ENTRY","UPDATE_TIMESHEET","DELETE_TIMESHEET","CANCEL_TIMESHEET","SUBMIT_TIMESHEET","SUBMIT_ALL_TIMESHEET","VIEW_TIMESHEET", "VIEW_ATTENDANCE","APPLY_LEAVE","VIEW_LEAVE", "VIEW_LEAVE_BALANCE","APPLY_OVERTIME_REQUEST","VIEW_OVERTIME_REQUEST","VIEW_HOLIDAY","VIEW_PROFILE","VIEW_SETTINGS","CHANGE_PASSWORD");
        List<Action> userActions = allActions.stream().filter(a -> userAliases.contains(a.getAlias())).collect(Collectors.toList());
        userRole.setActionList(userActions);
        System.out.println("Mapped actions to USER.");

        roleRepository.save(adminRole);
        roleRepository.save(hrRole);
        roleRepository.save(managerRole);
        roleRepository.save(supervisorRole);
        roleRepository.save(userRole);

        System.out.println("All role-action mappings saved.");
    }
	
	public void mapAllActionsToAdminUser(OurUsers adminUser) {
		
		 System.out.println("Mapping all actions to admin user...");

	    // 2. Get ADMIN role
	    Role adminRole = roleRepository.findByRoleName("admin");
	
	    // 3. Fetch all actions
	    // List<Action> allActions = actionRepository.findAll();
        List<Action> allActions = adminRole.getActionList();

	    // 4. Create mappings
	    List<UserActionMapping> mappings = new ArrayList<>();
	    for (Action action : allActions) {
	    	
	            UserActionMapping mapping = new UserActionMapping();
	            mapping.setUser(adminUser);
	            mapping.setRole(adminRole);
	            mapping.setAction(action);
	            mappings.add(mapping);
	        
	    }

	    // 5. Save all mappings
	    userActionMappingRepository.saveAll(mappings);
	    System.out.println("Mapped " + mappings.size() + " actions to admin user successfully.");
	}

	@Override
public void mapDefaultActionsToUser(OurUsers user) {
    // Multi-role aware: map defaults for *every* role currently assigned to the user
    if (user.getRoles() == null || user.getRoles().isEmpty()) {
        System.out.println("User has no roles; skipping default action mapping.");
        return;
    }

    System.out.println("Mapping default actions for ALL roles of userId=" + user.getId());

    List<UserActionMapping> mappings = new ArrayList<>();

    // Loop through all roles and add their default actions
    for (Role role : user.getRoles()) {
        if (role == null) continue;

        // Ensure we have the managed Role entity (by name) if needed
        Role managedRole = roleRepository.findByRoleName(role.getRoleName());
        if (managedRole == null) {
            System.out.println("Role not found for name: " + role.getRoleName());
            continue;
        }

        List<Action> roleActions = managedRole.getActionList();
        if (roleActions == null || roleActions.isEmpty()) {
            System.out.println("No default actions for role: " + managedRole.getRoleName());
            continue;
        }

        for (Action action : roleActions) {
            UserActionMapping mapping = new UserActionMapping();
            mapping.setUser(user);
            mapping.setRole(managedRole);
            mapping.setAction(action);
            mappings.add(mapping);
        }
    }

    if (!mappings.isEmpty()) {
        userActionMappingRepository.saveAll(mappings);
        System.out.println("Mapped " + mappings.size() + " default actions across all roles to userId=" + user.getId());
    } else {
        System.out.println("No mappings to save for userId=" + user.getId());
    }
}

	
	
}

