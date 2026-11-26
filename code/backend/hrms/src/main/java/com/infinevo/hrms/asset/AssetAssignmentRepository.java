package com.infinevo.hrms.asset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    Optional<AssetAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AssetAssignment> findByTenantIdAndAssetIdAndIsActiveTrue(UUID tenantId, UUID assetId);

    List<AssetAssignment> findByTenantIdAndEmployeeIdAndIsActiveTrue(UUID tenantId, UUID employeeId);

    List<AssetAssignment> findByTenantIdAndAssetIdOrderByAssignedDateDesc(UUID tenantId, UUID assetId);

    List<AssetAssignment> findByTenantIdAndEmployeeIdOrderByAssignedDateDesc(UUID tenantId, UUID employeeId);

    List<AssetAssignment> findByTenantIdAndIsActiveTrue(UUID tenantId);
}

