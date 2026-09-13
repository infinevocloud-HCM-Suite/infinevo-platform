package com.itsdev.payroll.repository;



import com.itsdev.payroll.entity.CompanyUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {
    boolean existsByUserEmail(String userEmail);
    Optional<CompanyUser> findByUserId(String userId);
    Optional<CompanyUser> findByUserEmail(String userEmail);
    List<CompanyUser> findAllByUserEmail(String userEmail);
    
    List<CompanyUser> findByUserIdIn(List<String> userIds);
}
