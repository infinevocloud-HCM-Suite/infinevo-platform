package com.itsdev.payroll.repository.organization;

import com.itsdev.payroll.entity.organization.WorkLocation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, Long> {
    Optional<WorkLocation> findByWorkLocationId(String workLocationId);
    List<WorkLocation> findByOrganizationId(Long organizationId);
    boolean existsByWorkLocationId(String workLocationId);
    boolean existsByOrganization_OrganizationId(String organizationId);


    @Modifying
    @Query("UPDATE WorkLocation wl SET wl.status = false " +
           "WHERE wl.workLocationId = :workLocationId " +
           "AND wl.organization.organizationId = :organizationId")
    void softDeleteByWorkLocationIdAndOrganizationId(@Param("workLocationId") String workLocationId,
                                                      @Param("organizationId") String organizationId);
    
    List<WorkLocation> findByStatusTrue();


}
