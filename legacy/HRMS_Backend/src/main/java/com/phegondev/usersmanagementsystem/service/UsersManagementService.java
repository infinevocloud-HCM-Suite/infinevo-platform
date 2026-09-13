package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.dto.ReqRes;
import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.entity.Employee;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.UserActionMappingRepository;
import com.phegondev.usersmanagementsystem.service.useraccess.ActionService;
import com.phegondev.usersmanagementsystem.service.useraccess.RoleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phegondev.usersmanagementsystem.entity.useraccess.Role;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UsersManagementService {
	
	    private final UsersRepo usersRepo;
	    private final JWTUtils jwtUtils;
	    private final AuthenticationManager authenticationManager;
	    private final PasswordEncoder passwordEncoder;
	    private final EmployeeService employeeService;
	    private final UserActionMappingRepository userActionMappingRepo;
	    private final ActionService actionService;
        private final RoleService roleService;

	    @Autowired
public UsersManagementService(
        UsersRepo usersRepo,
        JWTUtils jwtUtils,
        AuthenticationManager authenticationManager,
        PasswordEncoder passwordEncoder,
        EmployeeService employeeService,
        UserActionMappingRepository userActionMappingRepo,
        ActionService actionService,
        RoleService roleService) { // ADD parameter
    this.usersRepo = usersRepo;
    this.jwtUtils = jwtUtils;
    this.authenticationManager = authenticationManager;
    this.passwordEncoder = passwordEncoder;
    this.employeeService = employeeService;
    this.userActionMappingRepo = userActionMappingRepo;
    this.actionService = actionService;
    this.roleService = roleService; // ADD assignment
}

    

    // User Registration
    public ReqRes register(ReqRes registrationRequest) {
        System.out.println("Starting registration for email: " + registrationRequest.getEmail() + 
                           ", empId: " + registrationRequest.getEmpId());

        ReqRes resp = new ReqRes();
        try {
            if (usersRepo.existsByEmail(registrationRequest.getEmail())) {
                System.out.println("Email already exists: " + registrationRequest.getEmail());
                resp.setStatusCode(409);
                resp.setMessage("User with this email already exists.");
                return resp;
            }

            if (usersRepo.existsByEmpId(registrationRequest.getEmpId())) {
                System.out.println("Employee ID already registered: " + registrationRequest.getEmpId());
                resp.setStatusCode(409);
                resp.setMessage("Employee ID already registered.");
                return resp;
            }

            if (!employeeService.existsByEmpId(registrationRequest.getEmpId())) {
                System.out.println("No employee found with empId: " + registrationRequest.getEmpId());
                resp.setStatusCode(404);
                resp.setMessage("No employee found with this ID.");
                return resp;
            }

            // Create user
            OurUsers ourUser = new OurUsers();
            ourUser.setEmail(registrationRequest.getEmail());
            ourUser.setCity(registrationRequest.getCity());
            // ourUser.setRole(registrationRequest.getRole());

// Convert comma-separated roles from registrationRequest to Role entities
List<Role> roleEntities = new ArrayList<>();
if (registrationRequest.getRoles() != null && !registrationRequest.getRoles().isEmpty()) {
    roleEntities = registrationRequest.getRoles().stream()
            .map(roleName -> roleService.getRoleEntityByName(roleName))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
ourUser.setRoles(roleEntities);



            ourUser.setName(registrationRequest.getName());
            ourUser.setEmpId(registrationRequest.getEmpId());
            ourUser.setPassword(passwordEncoder.encode(registrationRequest.getPassword()));

            System.out.println("Saving new user: " + ourUser);
            OurUsers ourUsersResult = usersRepo.save(ourUser);
            System.out.println("User saved with ID: " + ourUsersResult.getId());

            if (ourUsersResult.getId() > 0) {
                System.out.println("Attempting to link employee to user...");

                Optional<Employee> employeeOpt = employeeService.getEmployeeByEmpId(registrationRequest.getEmpId());
                if (employeeOpt.isPresent()) {
                    Employee employee = employeeOpt.get();
                    System.out.println("Employee fetched: " + employee.getId());

                 
                    employee.setOurUser(ourUsersResult);
                    ourUsersResult.setEmployee(employee);

                    System.out.println("Saving updated employee with linked user...");
                    employeeService.saveEmployee(employee); 

                    System.out.println("Saving user again to set employee_id...");
                    usersRepo.save(ourUsersResult); 
                    System.out.println("User updated with employee_id: " + employee.getId());
                    
                    actionService.mapDefaultActionsToUser(ourUsersResult);
                } else {
                    System.out.println("Unexpected: Employee not found even after existence check.");
                }
            }

            resp.setStatusCode(200);
            resp.setMessage("User registered successfully.");
            resp.setOurUsers(ourUsersResult);
        } catch (Exception e) {
            System.out.println("Exception occurred during registration: " + e.getMessage());
            e.printStackTrace();
            resp.setStatusCode(500);
            resp.setError(e.getMessage());
        }

        return resp;
    }

    public ReqRes getCompleteProfile(String email) {
        System.out.println("Entered getCompleteProfile with email: " + email);

        ReqRes reqRes = new ReqRes();
        try {
            Optional<OurUsers> optionalUser = usersRepo.findByEmail(email);
            if (optionalUser.isEmpty()) {
                System.out.println("No user found with email: " + email);
                throw new RuntimeException("User not found");
            }

            OurUsers user = optionalUser.get();
            System.out.println("User found: " + user);

            if (user.getEmployeeId() == null) {
                System.out.println("User has no linked employee. EmployeeId is null.");
                reqRes.setStatusCode(400);
                reqRes.setMessage("No employee linked to this user");
                return reqRes;
            }

            Employee employee = user.getEmployee();
            System.out.println("Employee fetched: " + employee);

            String userEmpId = user.getEmpId();
            String employeeEmpId = employee != null ? employee.getEmpId() : null;

            System.out.println("User EmpId: " + userEmpId);
            System.out.println("Employee EmpId: " + employeeEmpId);

            if (userEmpId == null || employeeEmpId == null || !userEmpId.equals(employeeEmpId)) {
                System.out.println("Employee ID mismatch.");
                reqRes.setStatusCode(400);
                reqRes.setMessage("Employee ID mismatch. User: " + userEmpId + 
                                ", Employee: " + employeeEmpId);
                return reqRes;
            }

            Map<String, Object> employeeData = new HashMap<>();
            employeeData.put("personal", employee.getPersonal());
            employeeData.put("identification", employee.getIdentification());
            employeeData.put("contact", employee.getContact());
            employeeData.put("work", employee.getWork());
            employeeData.put("report", employee.getReport());

            System.out.println("Employee personal: " + employee.getPersonal());
            System.out.println("Employee identification: " + employee.getIdentification());
            System.out.println("Employee contact: " + employee.getContact());
            System.out.println("Employee work: " + employee.getWork());
            System.out.println("Employee report: " + employee.getReport());

            reqRes.setStatusCode(200);
            reqRes.setMessage("Complete profile fetched successfully");
      //      reqRes.setOurUsers(user); convertFromEntity
            
            reqRes.setUser(convertFromEntity(user));
            
            reqRes.setEmployeeData(employeeData);

            System.out.println("Profile fetch successful. Returning response.");
        } catch (Exception e) {
            System.out.println("Exception occurred: " + e.getMessage());
            e.printStackTrace();
            reqRes.setStatusCode(500);
            reqRes.setMessage("Error occurred: " + e.getMessage());
        }

        return reqRes;
    }

    // Login implementation
public ReqRes login(ReqRes loginRequest) {
    ReqRes response = new ReqRes();

    try {
        System.out.println("Authenticating user with email: " + loginRequest.getEmail());

        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        System.out.println("Authentication successful");
        SecurityContextHolder.getContext().setAuthentication(auth);

        OurUsers user = usersRepo.findByEmail(loginRequest.getEmail()).orElseThrow();

        // Build roles list from user's Role entities
        List<String> rolesList = new ArrayList<>();
        if (user.getRoles() != null) {
            rolesList = user.getRoles().stream()
                            .map(Role::getRoleName)
                            .collect(Collectors.toList());
        }

        // Generate identity token (contains roles[], no actions[], no activeRole)
        String identityJwt = jwtUtils.generateIdentityToken(user, rolesList);

        response.setStatusCode(200);
        response.setToken(identityJwt);
        response.setRoles(rolesList);
        // For backward compatibility, also set role = first role if present
        // if (!rolesList.isEmpty()) {
        //     response.setRole(rolesList.get(0));
        // }

        // refresh token
        response.setRefreshToken(jwtUtils.generateRefreshToken(new HashMap<>(), user));
        response.setExpirationTime("24Hrs");
        response.setMessage("Successfully authenticated. Please pick a role.");
    } catch (Exception e) {
        System.out.println("Login failed: " + e.getMessage());
        response.setStatusCode(500);
        response.setMessage(e.getMessage());
    }

    return response;
}


// Generate refresh token (role-scoped)
public ReqRes refreshToken(ReqRes refreshTokenRequest) {
    ReqRes response = new ReqRes();

    try {
        String ourEmail = jwtUtils.extractUsername(refreshTokenRequest.getToken());
        OurUsers user = usersRepo.findByEmail(ourEmail).orElseThrow();

        if (jwtUtils.isTokenValid(refreshTokenRequest.getToken(), user)) {
            // ✅ extract activeRole using existing JWTUtils method
            String activeRole = jwtUtils.extractActiveRole(refreshTokenRequest.getToken());

            // 1. Find the role entity
            Role roleEntity = roleService.getRoleEntityByName(activeRole);
            if (roleEntity == null) {
                throw new RuntimeException("Active role not found: " + activeRole);
            }

            // 2. Get default actions for this role
            List<String> roleActions = roleService.findActionNamesByRoleId(roleEntity.getRoleId());

            // 3. ✅ Get user-specific overrides for this role only
            List<String> userActions = userActionMappingRepo
                    .findActionAliasesByUserIdAndRoleId(user.getId().longValue(), roleEntity.getRoleId());

            // 4. Merge (union) role defaults + user overrides
            Set<String> mergedActions = new HashSet<>(roleActions);
            if (userActions != null) mergedActions.addAll(userActions);

            // 5. Generate new role-scoped JWT
            String jwt = jwtUtils.generateTokenForRole(user, activeRole, new ArrayList<>(mergedActions));

            response.setActions(new ArrayList<>(mergedActions));
            response.setActiveRole(activeRole);
            response.setRoles(user.getRoles().stream().map(Role::getRoleName).toList());
            response.setStatusCode(200);
            response.setToken(jwt);
            response.setRefreshToken(refreshTokenRequest.getToken());
            response.setExpirationTime("24Hrs");
            response.setMessage("Successfully refreshed token for active role: " + activeRole);
        } else {
            response.setStatusCode(401);
            response.setMessage("Invalid refresh token");
        }

    } catch (Exception e) {
        response.setStatusCode(500);
        response.setMessage("Error refreshing token: " + e.getMessage());
    }

    return response;
}

    // Get All Users
public ReqRes getAllUsers() {
    ReqRes reqRes = new ReqRes();
    try {
        // ✅ Step 1: Fetch all users
        List<OurUsers> result = usersRepo.findAll();

        // ✅ Step 2: Debug each user's roles before mapping
        for (OurUsers u : result) {
            System.out.println("---- DEBUG USER ----");
            System.out.println("Email: " + u.getEmail() + " | Name: " + u.getName());
            if (u.getRoles() != null && !u.getRoles().isEmpty()) {
                System.out.println("Roles count: " + u.getRoles().size());
                u.getRoles().forEach(r ->
                    System.out.println(" -> Role: " + r.getRoleName())
                );
            } else {
                System.out.println("No roles found for this user!");
            }
        }

        // ✅ Step 3: Convert to DTO
        List<UserDTO> dtoList = result.stream()
            .map(user -> {
                UserDTO dto = new UserDTO();
                dto.setUserId(user.getId());
                dto.setEmail(user.getEmail());
                dto.setName(user.getName());
                dto.setCity(user.getCity());
                dto.setEmpId(user.getEmpId());
                dto.setIsNonBlocked(user.getIsNonBlocked());

                // ✅ Step 4: Safely map multiple roles
                if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                    List<String> roleNames = user.getRoles().stream()
                            .filter(Objects::nonNull)
                            .map(Role::getRoleName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    dto.setRoles(roleNames);             // multiple roles
                    // dto.setRole(roleNames.get(0));       // backward compatible
                } else {
                    dto.setRoles(Collections.emptyList());
                    dto.setRole(null);
                }

                return dto;
            })
            .collect(Collectors.toList());

        // ✅ Step 5: Return proper response
        if (!dtoList.isEmpty()) {
            reqRes.setUserDTOList(dtoList);
            reqRes.setStatusCode(200);
            reqRes.setMessage("Users fetched successfully");
        } else {
            reqRes.setStatusCode(404);
            reqRes.setMessage("No Users Found");
        }

    } catch (Exception e) {
        reqRes.setStatusCode(500);
        reqRes.setMessage("Error occurred: " + e.getMessage());
        e.printStackTrace(); // ✅ Optional: print full trace
    }
    return reqRes;
}


    // Get user by ID
    /*
    public ReqRes getUserByID(Integer id) {
        ReqRes reqRes = new ReqRes();

        try {
            OurUsers userById = usersRepo.findById(id).orElseThrow(() -> new RuntimeException("User Not Found"));
            reqRes.setOurUsers(userById);
            reqRes.setStatusCode(200);
            reqRes.setMessage("User with id '" + id + "' found successfully");

        } catch (Exception e) {
            reqRes.setStatusCode(500);
            reqRes.setMessage(e.getMessage());
        }
        return reqRes;
 
    } */
    
    public ReqRes getUserByID(Integer id) {
        System.out.println("Start: getUserByID with id = " + id);
        ReqRes reqRes = new ReqRes();

        try {
            OurUsers userById = usersRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("User Not Found"));

            System.out.println("User found: " + userById.getEmail());

            UserDTO userDTO = convertFromEntity(userById);

            reqRes.setUser(userDTO);
            reqRes.setStatusCode(200);
            reqRes.setMessage("User with id '" + id + "' found successfully");

            System.out.println("Returning success response for user id: " + id);

        } catch (Exception e) {
            System.out.println("Exception in getUserByID: " + e.getMessage());
            reqRes.setStatusCode(500);
            reqRes.setMessage(e.getMessage());
        }

        System.out.println("End: getUserByID with id = " + id);
        return reqRes;
    }



    // Delete User
    public ReqRes deleteUser(Integer userId) {
        ReqRes reqRes = new ReqRes();

        try {
            Optional<OurUsers> userOptional = usersRepo.findById(userId);

            if (userOptional.isPresent()) {
                usersRepo.deleteById(userId);
                reqRes.setStatusCode(200);
                reqRes.setMessage("User deleted successfully");
            } else {
                reqRes.setStatusCode(404);
                reqRes.setMessage("User not found for deletion");
            }

        } catch (Exception e) {
            reqRes.setStatusCode(500);
            reqRes.setMessage("Error Occurred while deleting user : " + e.getMessage());
        }

        return reqRes;
    }

    // update User
    @Transactional
    public ReqRes updateUser(Integer userId, ReqRes updatedUserReq) {
    ReqRes reqRes = new ReqRes();
    try {
        Optional<OurUsers> userOptional = usersRepo.findById(userId);
        if (userOptional.isPresent()) {
            OurUsers existingUser = userOptional.get();

            // update basic fields
            existingUser.setEmail(updatedUserReq.getEmail());
            existingUser.setName(updatedUserReq.getName());
            existingUser.setCity(updatedUserReq.getCity());

            // ✅ update roles from role names
            if (updatedUserReq.getRoles() != null && !updatedUserReq.getRoles().isEmpty()) {
                List<Role> roleEntities = updatedUserReq.getRoles().stream()
                        .map(roleName -> roleService.getRoleEntityByName(roleName))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                existingUser.setRoles(roleEntities);
            }

            // update password if present
            if (updatedUserReq.getPassword() != null && !updatedUserReq.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(updatedUserReq.getPassword()));
            }

            OurUsers savedUser = usersRepo.save(existingUser);

            // 1) Clear existing per-user mappings (stale role actions)
userActionMappingRepo.deleteByUser(savedUser);

// 2) Recreate defaults for *current* roles (multi-role aware)
actionService.mapDefaultActionsToUser(savedUser);


            reqRes.setStatusCode(200);
            reqRes.setMessage("User updated successfully");
            reqRes.setUser(convertFromEntity(savedUser)); // return updated DTO
        } else {
            reqRes.setStatusCode(404);
            reqRes.setMessage("User not found");
        }
    } catch (Exception e) {
        reqRes.setStatusCode(500);
        reqRes.setMessage("Error Occurred while updating the user : " + e.getMessage());
    }
    return reqRes;
}


    // Get my info
    /*
    public ReqRes getMyInfo(String email) {
        ReqRes reqRes = new ReqRes();
        try {
            Optional<OurUsers> userOptional = usersRepo.findByEmail(email);
            if (userOptional.isPresent()) {
                reqRes.setOurUsers(userOptional.get());
                reqRes.setStatusCode(200);
                reqRes.setMessage("Successful");
            } else {
                reqRes.setStatusCode(404);
                reqRes.setMessage("User is not found");
            }

        } catch (Exception e) {
            reqRes.setStatusCode(500);
            reqRes.setMessage("Error occurred while getting your info : " + e.getMessage());
        }
        return reqRes;
    } */
    
    public ReqRes getMyInfo(String email) {
        ReqRes reqRes = new ReqRes();
        try {
            Optional<OurUsers> optionalUser = usersRepo.findByEmail(email);
            
            if (optionalUser.isPresent()) {
                OurUsers user = optionalUser.get();
                String rolesString = user.getRoles() != null
        ? user.getRoles().stream().map(Role::getRoleName).collect(Collectors.joining(", "))
        : "No roles";
System.out.println("User found: " + user.getUsername() +
        " (Employee ID: " + user.getEmpId() + ", Roles: " + rolesString + ")");

                List<String> actionList = userActionMappingRepo.findActionAliasesByUserId(user.getId());
                System.out.println("Actions fetched for user: " + actionList);

                
if (user.getRoles() != null && !user.getRoles().isEmpty()) {
    // backward compatibility
    reqRes.setRole(user.getRoles().get(0).getRoleName());
    // new multi-role support
    reqRes.setRoles(user.getRoles().stream()
            .map(Role::getRoleName)
            .collect(Collectors.toList()));
}
                reqRes.setActions(actionList);
                reqRes.setStatusCode(200);
                reqRes.setMessage("Successful");
            } else {
                reqRes.setStatusCode(404);
                reqRes.setMessage("User not found");
            }
        } catch (Exception e) {
            reqRes.setStatusCode(500);
            reqRes.setMessage("Error occurred while getting your info: " + e.getMessage());
        }
        return reqRes;
    }
    

    public List<UserDTO> getAllManagers() {
    return usersRepo.findAllByRoleName("manager").stream()
            .map(UsersManagementService::convertFromEntity)
            .collect(Collectors.toList());
}

public List<UserDTO> getAllReportingManagers() {
    return usersRepo.findAllByRoleName("reporting manager").stream()
            .map(UsersManagementService::convertFromEntity)
            .collect(Collectors.toList());
}



    
    public static UserDTO convertFromEntity(OurUsers user) {
    UserDTO dto = new UserDTO();
    dto.setUserId(user.getId());
    dto.setEmail(user.getEmail());
    dto.setName(user.getName());
    dto.setCity(user.getCity());

    // Convert multiple Role entities to List<String>
    if (user.getRoles() != null) {
        dto.setRoles(user.getRoles().stream()
                     .map(Role::getRoleName)
                     .collect(Collectors.toList()));
    }

    dto.setEmpId(user.getEmpId());
    dto.setIsNonBlocked(user.getIsNonBlocked());
    return dto;
}



    // Add to UsersManagementService.java
public ReqRes changePassword(String email, ReqRes changePasswordRequest) {
    ReqRes response = new ReqRes();
    try {
        OurUsers user = usersRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Verify old password
        if (!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())) {
            response.setStatusCode(400);
            response.setMessage("Old password is incorrect");
            return response;
        }
        
        // Validate new password
        if (changePasswordRequest.getNewPassword().equals(changePasswordRequest.getOldPassword())) {
            response.setStatusCode(400);
            response.setMessage("New password must be different from old password");
            return response;
        }
        
        // Update password
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        usersRepo.save(user);
        
        response.setStatusCode(200);
        response.setMessage("Password changed successfully");
    } catch (Exception e) {
        response.setStatusCode(500);
        response.setMessage("Error changing password: " + e.getMessage());
    }
    return response;
}




}