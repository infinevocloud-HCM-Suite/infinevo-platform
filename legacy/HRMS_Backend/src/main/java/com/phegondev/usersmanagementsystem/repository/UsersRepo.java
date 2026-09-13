package com.phegondev.usersmanagementsystem.repository;

import com.phegondev.usersmanagementsystem.entity.OurUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsersRepo extends JpaRepository<OurUsers, Integer> {

    boolean existsByEmail(String email);
    boolean existsByEmpId(String empId);
    OurUsers findByEmpId(String empId);

    // ✅ Keep old method names but fix implementation
    @Query("SELECT u FROM OurUsers u JOIN u.roles r WHERE r.roleName = :roleName")
    OurUsers findByRole(@Param("roleName") String roleName);

    @Query("SELECT u FROM OurUsers u JOIN u.roles r WHERE r.roleName = :roleName")
    List<OurUsers> findAllByRole(@Param("roleName") String roleName);

    @Query("SELECT u FROM OurUsers u WHERE :roleName NOT IN (SELECT r.roleName FROM u.roles r)")
    List<OurUsers> findByRoleNot(@Param("roleName") String roleName);

    List<OurUsers> findByEmpIdIn(List<String> empIds);
    List<OurUsers> findAllByRoles_RoleName(String roleName);

    @Query("SELECT u FROM OurUsers u JOIN u.roles r WHERE r.roleName = :roleName")
    List<OurUsers> findAllByRoleName(@Param("roleName") String roleName);

    @Query("SELECT u FROM OurUsers u LEFT JOIN FETCH u.roles WHERE u.email = :email")
Optional<OurUsers> findByEmail(@Param("email") String email);


}
