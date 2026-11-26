package com.infinevo.hrms.asset;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyAssetRepository extends JpaRepository<CompanyAsset, UUID> {

    Optional<CompanyAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CompanyAsset> findByTenantIdAndAssetTag(UUID tenantId, String assetTag);

    boolean existsByTenantIdAndAssetTag(UUID tenantId, String assetTag);

    List<CompanyAsset> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<CompanyAsset> findByTenantIdAndCurrentStatusOrderByCreatedAtDesc(UUID tenantId, AssetStatus status);

    List<CompanyAsset> findByTenantIdAndCategoryOrderByCreatedAtDesc(UUID tenantId, AssetCategory category);

    List<CompanyAsset> findByTenantIdAndCurrentStatusAndCategoryOrderByCreatedAtDesc(
            UUID tenantId, AssetStatus status, AssetCategory category);

    List<CompanyAsset> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}

