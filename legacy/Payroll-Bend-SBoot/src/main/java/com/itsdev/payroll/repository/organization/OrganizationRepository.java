package com.itsdev.payroll.repository.organization;

import java.util.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.itsdev.payroll.entity.organization.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
	Optional<Organization> findByOrganizationId(String organizationId);

	boolean existsByOrganizationId(String organizationId);

	List<Organization> findByCreatedByAndIsDeletedFalse(String createdBy);
	List<Organization> findByCreatedByAndIsDeletedFalseAndIsOrgActiveTrue(String createdBy);

	List<Organization> findByOrganizationIdInAndIsDeletedFalse(List<String> organizationIds);
	List<Organization> findByOrganizationIdInAndIsDeletedFalseAndIsOrgActiveTrue(List<String> organizationIds);


	@EntityGraph(attributePaths = {"workLocations"})
	Optional<Organization> findWithWorkLocationsByOrganizationId(@Param("organizationId") String organizationId);


}