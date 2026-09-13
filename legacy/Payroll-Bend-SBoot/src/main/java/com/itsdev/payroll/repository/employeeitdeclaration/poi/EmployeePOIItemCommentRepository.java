package com.itsdev.payroll.repository.employeeitdeclaration.poi;

import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeePOIItemComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeePOIItemCommentRepository extends JpaRepository<EmployeePOIItemComment, Long> {

    List<EmployeePOIItemComment> findByPoiItem_IdOrderByCreatedTimeAsc(Long poiItemId);

    @Query("SELECT c FROM EmployeePOIItemComment c WHERE c.poiItem.id = :poiItemId AND c.responseTo IS NULL ORDER BY c.createdTime ASC")
    List<EmployeePOIItemComment> findRootCommentsByPoiItemId(@Param("poiItemId") Long poiItemId);

    List<EmployeePOIItemComment> findByResponseTo_IdOrderByCreatedTimeAsc(Long parentCommentId);

    @Query("SELECT COUNT(c) FROM EmployeePOIItemComment c WHERE c.poiItem.id = :poiItemId")
    Long countByPoiItemId(@Param("poiItemId") Long poiItemId);

    // Optional: For admin dashboard
    @Query("SELECT c FROM EmployeePOIItemComment c WHERE c.poiItem.proofOfInvestment.organization.id = :organizationId AND c.commentedByEmployee IS NOT NULL ORDER BY c.createdTime DESC")
    List<EmployeePOIItemComment> findEmployeeCommentsByOrganization(@Param("organizationId") Long organizationId);
}