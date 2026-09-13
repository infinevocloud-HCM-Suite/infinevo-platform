package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.OvertimeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OvertimeRequestRepository extends JpaRepository<OvertimeRequest, Long> {

    List<OvertimeRequest> findByEmployeeIdOrderByCreatedAtDesc(String employeeId);
    
   // List<OvertimeRequest> findByStatusOrderByCreatedAtDesc(String status);
    
  //  List<OvertimeRequest> findByEmployeeIdAndStatusOrderByCreatedAtDesc(String employeeId, String status);
    
    List<OvertimeRequest> findByProjectIgnoreCaseOrderByCreatedAtDesc(String project);
    
    List<OvertimeRequest> findByCategoryIgnoreCaseOrderByCreatedAtDesc(String category);
    
    List<OvertimeRequest> findAllByOrderByCreatedAtDesc();
    
    Optional<OvertimeRequest> findById(Long id);

    List<OvertimeRequest> findByManagerEmployeeIdOrderByCreatedAtDesc(String managerEmployeeId);

}