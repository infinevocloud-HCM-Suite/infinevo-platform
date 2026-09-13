//package com.phegondev.usersmanagementsystem.controller;
//
//import com.phegondev.usersmanagementsystem.entity.LeaveType;
//import com.phegondev.usersmanagementsystem.service.LeaveTypeService;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/admin/leave-types")
//@CrossOrigin(origins = "http://localhost:5173")
//public class LeaveTypeController {
//
//    private final LeaveTypeService leaveTypeService;
//
//    public LeaveTypeController(LeaveTypeService leaveTypeService) {
//        this.leaveTypeService = leaveTypeService;
//    }
//
//    @GetMapping
//    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
//        return ResponseEntity.ok(leaveTypeService.getAllLeaveTypes());
//    }
//
//    @GetMapping("/active")
//    public ResponseEntity<List<LeaveType>> getAllActiveLeaveTypes() {
//        return ResponseEntity.ok(leaveTypeService.getAllActiveLeaveTypes());
//    }
//
//    @GetMapping("/{id}")
//    public ResponseEntity<LeaveType> getLeaveTypeById(@PathVariable Long id) {
//        return ResponseEntity.ok(leaveTypeService.getLeaveTypeById(id));
//    }
//
//    @PostMapping
//    @PreAuthorize("hasAuthority('admin')")
//    public ResponseEntity<LeaveType> createLeaveType(@RequestBody LeaveType leaveType) {
//        System.out.println("Received request to create leave type: " + leaveType);
//        LeaveType createdLeaveType = leaveTypeService.createLeaveType(leaveType);
//        System.out.println("Successfully created leave type: " + createdLeaveType);
//        return new ResponseEntity<>(createdLeaveType, HttpStatus.CREATED);
//    }
//
//
//    @PutMapping("/{id}")
//    @PreAuthorize("hasAuthority('admin')")
//    public ResponseEntity<LeaveType> updateLeaveType(@PathVariable Long id, 
//                                                   @RequestBody LeaveType leaveType) {
//        return ResponseEntity.ok(leaveTypeService.updateLeaveType(id, leaveType));
//    }
//
//    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('admin')")
//    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
//        leaveTypeService.deleteLeaveType(id);
//        return ResponseEntity.noContent().build();
//    }
//
//    @PatchMapping("/{id}/toggle-status")
//    @PreAuthorize("hasAuthority('admin')")
//    public ResponseEntity<LeaveType> toggleLeaveTypeStatus(@PathVariable Long id) {
//        return ResponseEntity.ok(leaveTypeService.toggleLeaveTypeStatus(id));
//    }
//}



package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.entity.LeaveType;
import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.service.LeaveTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    @GetMapping
    public ResponseEntity<List<LeaveType>> getAllLeaveTypes() {
        return ResponseEntity.ok(leaveTypeService.getAllLeaveTypes());
    }

  //  @GetMapping("/active")
 //   public ResponseEntity<List<LeaveType>> getAllActiveLeaveTypes() {
  //      return ResponseEntity.ok(leaveTypeService.getAllActiveLeaveTypes());
  //  }
    




    @GetMapping("/{id}")
    public ResponseEntity<LeaveType> getLeaveTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.getLeaveTypeById(id));
    }

    @PostMapping
    public ResponseEntity<LeaveType> createLeaveType(@RequestBody LeaveType leaveType) {
        System.out.println("[POST] Creating LeaveType: " + leaveType);
        LeaveType createdLeaveType = leaveTypeService.createLeaveType(leaveType);
        System.out.println("[POST] LeaveType created with ID: " + createdLeaveType.getId());
        return new ResponseEntity<>(createdLeaveType, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveType> updateLeaveType(@PathVariable Long id,
                                                     @RequestBody LeaveType leaveType) {
        System.out.println("[PUT] Updating LeaveType ID: " + id);
        LeaveType updated = leaveTypeService.updateLeaveType(id, leaveType);
        System.out.println("[PUT] LeaveType updated: " + updated.getName());
        return ResponseEntity.ok(updated);
    }


//    @DeleteMapping("/{id}")
//    @PreAuthorize("hasAuthority('admin')")
//    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
//        leaveTypeService.deleteLeaveType(id);
//        return ResponseEntity.noContent().build();
//    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {
        System.out.println("[DELETE] Deleting LeaveType ID: " + id); // Debug log
        leaveTypeService.deleteLeaveType(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<LeaveType> toggleLeaveTypeStatus(@PathVariable Long id) {
        return ResponseEntity.ok(leaveTypeService.toggleLeaveTypeStatus(id));
    }
    
 

}