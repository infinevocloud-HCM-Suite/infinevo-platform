package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatus(String status);

    List<Project> findByNameContainingIgnoreCase(String name);

    List<Project> findByManagerId(String managerId);

    @Query("SELECT COUNT(p) > 0 FROM Project p WHERE p.id = :projectId AND p.managerId = :managerId")
    boolean existsByProjectIdAndManagerId(@Param("projectId") Long projectId,
            @Param("managerId") String managerId);
}