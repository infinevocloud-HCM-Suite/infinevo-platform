package com.phegondev.usersmanagementsystem.repository.useraccess;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.useraccess.UserActionMapping;


@Repository
public interface UserActionMappingRepository extends JpaRepository<UserActionMapping, Long> {

    @Query("SELECT DISTINCT a.actionName FROM UserActionMapping uam " +
           "JOIN uam.action a " +
           "WHERE uam.user.id = :userId")
    List<String> findActionNamesByUserId(@Param("userId") Integer userId);
    
    @Query("SELECT DISTINCT a.alias FROM UserActionMapping uam " +
    	       "JOIN uam.action a " +
    	       "WHERE uam.user.id = :userId")
    	List<String> findActionAliasesByUserId(@Param("userId") Integer userId);

    
    @Modifying
    @Query("DELETE FROM UserActionMapping uam WHERE uam.user = :user")
    void deleteByUser(@Param("user") OurUsers user);
    
    @Query("SELECT u FROM UserActionMapping u WHERE u.action.id = :actionId")
    List<UserActionMapping> findByActionId(@Param("actionId") Long actionId);
    
  //  List<UserActionMapping> findByRoleId(Long roleId);
    
    @Query("SELECT u.name FROM UserActionMapping uam JOIN uam.user u WHERE uam.role.roleId = :roleId")
    List<String> findUsernamesByRoleId(@Param("roleId") Long roleId);
    
    @Query("SELECT DISTINCT r.roleName FROM UserActionMapping uam " +
    	       "JOIN uam.role r " +
    	       "WHERE uam.user.id = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);

    @Query("SELECT a.alias FROM UserActionMapping uam " +
       "JOIN uam.action a " +
       "WHERE uam.user.id = :userId AND uam.role.id = :roleId")
List<String> findActionAliasesByUserIdAndRoleId(@Param("userId") Long userId, @Param("roleId") Long roleId);



}

