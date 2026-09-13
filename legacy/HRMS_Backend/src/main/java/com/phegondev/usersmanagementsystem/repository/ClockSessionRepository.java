package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.ClockSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClockSessionRepository extends JpaRepository<ClockSession, Long> {
}
