package com.phegondev.usersmanagementsystem.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.phegondev.usersmanagementsystem.dto.ReqRes;
import com.phegondev.usersmanagementsystem.dto.UserDTO;
import com.phegondev.usersmanagementsystem.dto.useraccess.RoleDTO;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;
import com.phegondev.usersmanagementsystem.service.JWTUtils;
import com.phegondev.usersmanagementsystem.service.UsersManagementService;
import com.phegondev.usersmanagementsystem.service.useraccess.RoleService;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.repository.useraccess.UserActionMappingRepository;

@RestController
public class UserManagementController {

    @Autowired
    private UsersManagementService usersManagementService;

    @Autowired
    private UsersRepo usersRepo;

    @Autowired
private RoleService roleService;

@Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private  UserActionMappingRepository userActionMappingRepo;


    // Register
    // Register
    @PostMapping("/register")
    public ResponseEntity<ReqRes> register(@RequestBody ReqRes reg) {
        System.out.println("Received registration request: " + reg);
        ReqRes response = usersManagementService.register(reg);
        System.out.println("Registration response: " + response);
        return ResponseEntity.ok(response);
    }

    // Login
    @PostMapping("/auth/login")
    public ResponseEntity<ReqRes> login(@RequestBody ReqRes req) {
        return ResponseEntity.ok(usersManagementService.login(req));
    }

    // Refresh Token
    @PostMapping("/auth/refresh")
    public ResponseEntity<ReqRes> refreshToken(@RequestBody ReqRes req) {
        return ResponseEntity.ok(usersManagementService.refreshToken(req));
    }

    // Get all users - permit only to admin
    @GetMapping("/get-all-users")
    public ResponseEntity<ReqRes> getAllUsers() {
        return ResponseEntity.ok(usersManagementService.getAllUsers());
    }

    // Get User by ID
    @GetMapping("/get-user/{userId}")
    public ResponseEntity<ReqRes> getUserById(@PathVariable Integer userId) {
        return ResponseEntity.ok(usersManagementService.getUserByID(userId));
    }

    // Update user
    @PutMapping("/update/{userId}")
public ResponseEntity<ReqRes> updateUser(@PathVariable Integer userId, @RequestBody ReqRes reqRes) {
    return ResponseEntity.ok(usersManagementService.updateUser(userId, reqRes));
}

    // Get the profile
    @GetMapping("/get-profile")
    public ResponseEntity<ReqRes> getMyProfile() {

        // Get the Authentication object for the current user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // getName() -> principle name(Username) for the current authenticated user
        String email = authentication.getName();
        ReqRes response = usersManagementService.getMyInfo(email);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // Delete User
    @DeleteMapping("/deleteUser/{userId}")
    public ResponseEntity<ReqRes> deleteUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(usersManagementService.deleteUser(userId));
    }

    @GetMapping("/get-complete-profile")
    public ResponseEntity<ReqRes> getCompleteProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        System.out.println("Authenticated user email: " + email);

        OurUsers user = (OurUsers) authentication.getPrincipal();
        System.out.println("Authenticated user : " + user);

        ReqRes response = usersManagementService.getCompleteProfile(email);
        System.out.println("Final response: " + response);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

 //   @GetMapping("/managers")
 //   public ResponseEntity<List<OurUsers>> getAllManagers() {
  //      List<OurUsers> managers = usersRepo.findAllByRole("manager");
   //     return ResponseEntity.ok(managers);
   // }
    
    @GetMapping("/managers")
    public ResponseEntity<List<UserDTO>> getAllManagers() {
        return ResponseEntity.ok(usersManagementService.getAllManagers());
    }

    @GetMapping("/reporting-managers")
    public ResponseEntity<List<UserDTO>> getAllReportingManagers() {
        return ResponseEntity.ok(usersManagementService.getAllReportingManagers());
    }

    // Add to UserManagementController.java
@PostMapping("/change-password")
public ResponseEntity<ReqRes> changePassword(@RequestBody ReqRes changePasswordRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String email = authentication.getName();
    return ResponseEntity.ok(usersManagementService.changePassword(email, changePasswordRequest));
}

@PostMapping("/auth/switch-role")
public ResponseEntity<ReqRes> switchRole(@RequestBody Map<String, String> body,
                                         @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
    ReqRes resp = new ReqRes();
    try {
        String chosenRole = body.get("role");
        if (chosenRole == null) {
            resp.setStatusCode(400);
            resp.setMessage("Role is required");
            return ResponseEntity.badRequest().body(resp);
        }

        // extract token and username
        String jwt = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : null;

        if (jwt == null) {
            resp.setStatusCode(401);
            resp.setMessage("Missing token");
            return ResponseEntity.status(401).body(resp);
        }

        String email = jwtUtils.extractUsername(jwt);
        OurUsers user = usersRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        boolean hasRole = user.getRoles() != null &&
                          user.getRoles().stream()
                              .anyMatch(r -> r.getRoleName().equalsIgnoreCase(chosenRole));

        if (!hasRole) {
            resp.setStatusCode(403);
            resp.setMessage("User does not have role: " + chosenRole);
            return ResponseEntity.status(403).body(resp);
        }

        // get role entity
        Role roleEntity = roleService.findByRoleName(chosenRole);
        if (roleEntity == null) {
            resp.setStatusCode(404);
            resp.setMessage("Role not found: " + chosenRole);
            return ResponseEntity.status(404).body(resp);
        }

        // get actions for this role
        // List<String> roleActions = roleService.findActionNamesByRoleId(roleEntity.getRoleId());

        // ✅ get user-specific direct actions ONLY for this role
List<String> userDirectActions = userActionMappingRepo
        .findActionAliasesByUserIdAndRoleId(user.getId().longValue(), roleEntity.getRoleId());


        // merge (role defaults + user-specific overrides for this role only)
        Set<String> mergedActions = new LinkedHashSet<>();
        // if (roleActions != null) mergedActions.addAll(roleActions);
        if (userDirectActions != null) mergedActions.addAll(userDirectActions);

        // generate role-scoped token
        String roleScopedJwt = jwtUtils.generateTokenForRole(
                user, chosenRole.toLowerCase(), new ArrayList<>(mergedActions));

        resp.setStatusCode(200);
        resp.setMessage("Role switched");
        resp.setToken(roleScopedJwt);
        resp.setActiveRole(chosenRole);
        resp.setActions(new ArrayList<>(mergedActions));
        resp.setRoles(user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()));

        return ResponseEntity.ok(resp);
    } catch (Exception e) {
        resp.setStatusCode(500);
        resp.setMessage(e.getMessage());
        return ResponseEntity.status(500).body(resp);
    }
}

}