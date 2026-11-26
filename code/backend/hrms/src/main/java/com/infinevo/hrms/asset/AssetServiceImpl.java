package com.infinevo.hrms.asset;

import com.infinevo.core.employee.EmployeeService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssetServiceImpl implements AssetService {

    private final CompanyAssetRepository assetRepository;
    private final AssetAssignmentRepository assignmentRepository;
    private final EmployeeService employeeService;

    public AssetServiceImpl(
            CompanyAssetRepository assetRepository,
            AssetAssignmentRepository assignmentRepository,
            EmployeeService employeeService) {
        this.assetRepository = Objects.requireNonNull(assetRepository, "assetRepository must not be null");
        this.assignmentRepository =
                Objects.requireNonNull(assignmentRepository, "assignmentRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> listAssets(UUID tenantId, AssetStatus status, AssetCategory category) {
        List<CompanyAsset> assets;
        if (status != null && category != null) {
            assets = assetRepository.findByTenantIdAndCurrentStatusAndCategoryOrderByCreatedAtDesc(
                    tenantId, status, category);
        } else if (status != null) {
            assets = assetRepository.findByTenantIdAndCurrentStatusOrderByCreatedAtDesc(tenantId, status);
        } else if (category != null) {
            assets = assetRepository.findByTenantIdAndCategoryOrderByCreatedAtDesc(tenantId, category);
        } else {
            assets = assetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        List<AssetAssignment> activeAssignments = assignmentRepository.findByTenantIdAndIsActiveTrue(tenantId);
        Map<UUID, AssetAssignment> activeMap = activeAssignments.stream()
                .collect(Collectors.toMap(AssetAssignment::getAssetId, Function.identity(), (a, b) -> a));

        List<UUID> employeeIds = activeAssignments.stream()
                .map(AssetAssignment::getEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> employeeNames = employeeIds.isEmpty() ? Map.of() : employeeService.displayNames(employeeIds);

        return assets.stream()
                .map(asset -> {
                    AssetAssignment active = activeMap.get(asset.getId());
                    AssetAssignmentResponse activeResp =
                            active != null ? toAssignmentResponse(active, asset, employeeNames) : null;
                    return toAssetResponse(asset, activeResp);
                })
                .toList();
    }

    @Override
    public AssetResponse createAsset(UUID tenantId, AssetRequest request, String createdBy) {
        if (request.assetTag() == null || request.assetTag().isBlank()) {
            throw new AssetValidationException("Asset tag is required");
        }
        if (request.assetName() == null || request.assetName().isBlank()) {
            throw new AssetValidationException("Asset name is required");
        }

        String tag = request.assetTag().trim();
        if (assetRepository.existsByTenantIdAndAssetTag(tenantId, tag)) {
            throw new AssetConflictException("Asset with tag '" + tag + "' already exists");
        }

        CompanyAsset asset = new CompanyAsset();
        asset.setTenantId(tenantId);
        asset.setAssetTag(tag);
        asset.setAssetName(request.assetName().trim());
        asset.setCategory(request.category() != null ? request.category() : AssetCategory.LAPTOP);
        asset.setBrand(request.brand());
        asset.setModelNumber(request.modelNumber());
        asset.setSerialNumber(request.serialNumber());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setPurchaseCost(request.purchaseCost());
        asset.setWarrantyExpiry(request.warrantyExpiry());
        asset.setVendorName(request.vendorName());
        asset.setCurrentStatus(AssetStatus.AVAILABLE);
        asset.setItemCondition(request.itemCondition() != null ? request.itemCondition() : AssetCondition.GOOD);
        asset.setNotes(request.notes());
        asset.setCreatedBy(createdBy != null ? createdBy : "system");
        asset.setUpdatedBy(createdBy != null ? createdBy : "system");

        CompanyAsset saved = assetRepository.save(asset);
        return toAssetResponse(saved, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetResponse getAsset(UUID tenantId, UUID assetId) {
        CompanyAsset asset = assetRepository
                .findByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));

        Optional<AssetAssignment> activeOpt =
                assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(tenantId, assetId);
        AssetAssignmentResponse activeResp = null;
        if (activeOpt.isPresent()) {
            AssetAssignment active = activeOpt.get();
            Map<UUID, String> names = employeeService.displayNames(List.of(active.getEmployeeId()));
            activeResp = toAssignmentResponse(active, asset, names);
        }
        return toAssetResponse(asset, activeResp);
    }

    @Override
    public AssetResponse updateAsset(UUID tenantId, UUID assetId, AssetRequest request, String updatedBy) {
        CompanyAsset asset = assetRepository
                .findByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));

        if (request.assetTag() == null || request.assetTag().isBlank()) {
            throw new AssetValidationException("Asset tag is required");
        }
        if (request.assetName() == null || request.assetName().isBlank()) {
            throw new AssetValidationException("Asset name is required");
        }

        String tag = request.assetTag().trim();
        if (!tag.equalsIgnoreCase(asset.getAssetTag()) && assetRepository.existsByTenantIdAndAssetTag(tenantId, tag)) {
            throw new AssetConflictException("Asset with tag '" + tag + "' already exists");
        }

        asset.setAssetTag(tag);
        asset.setAssetName(request.assetName().trim());
        if (request.category() != null) {
            asset.setCategory(request.category());
        }
        asset.setBrand(request.brand());
        asset.setModelNumber(request.modelNumber());
        asset.setSerialNumber(request.serialNumber());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setPurchaseCost(request.purchaseCost());
        asset.setWarrantyExpiry(request.warrantyExpiry());
        asset.setVendorName(request.vendorName());
        if (request.itemCondition() != null) {
            asset.setItemCondition(request.itemCondition());
        }
        asset.setNotes(request.notes());
        asset.setUpdatedBy(updatedBy != null ? updatedBy : "system");

        CompanyAsset saved = assetRepository.save(asset);

        Optional<AssetAssignment> activeOpt =
                assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(tenantId, assetId);
        AssetAssignmentResponse activeResp = null;
        if (activeOpt.isPresent()) {
            AssetAssignment active = activeOpt.get();
            Map<UUID, String> names = employeeService.displayNames(List.of(active.getEmployeeId()));
            activeResp = toAssignmentResponse(active, saved, names);
        }
        return toAssetResponse(saved, activeResp);
    }

    @Override
    public AssetResponse updateStatus(UUID tenantId, UUID assetId, AssetStatusUpdateRequest request, String updatedBy) {
        CompanyAsset asset = assetRepository
                .findByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));

        if (request.status() == null) {
            throw new AssetValidationException("Status is required");
        }
        if (request.status() == AssetStatus.ASSIGNED) {
            throw new AssetValidationException("Cannot set status to ASSIGNED directly; use the assign workflow");
        }

        // If asset is currently ASSIGNED and changing to another status, close the active assignment
        if (asset.getCurrentStatus() == AssetStatus.ASSIGNED && request.status() != AssetStatus.ASSIGNED) {
            Optional<AssetAssignment> activeOpt =
                    assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(tenantId, assetId);
            if (activeOpt.isPresent()) {
                AssetAssignment assignment = activeOpt.get();
                assignment.setActive(false);
                assignment.setReturnedDate(LocalDate.now());
                assignment.setReturnedBy(updatedBy != null ? updatedBy : "system");
                if (request.condition() != null) {
                    assignment.setReturnCondition(request.condition());
                } else {
                    assignment.setReturnCondition(assignment.getAssignmentCondition());
                }
                assignment.setReturnNotes(
                        request.notes() != null
                                ? request.notes()
                                : "Auto-closed on status transition to " + request.status());
                assignment.setUpdatedBy(updatedBy != null ? updatedBy : "system");
                assignmentRepository.save(assignment);
            }
        }

        asset.setCurrentStatus(request.status());
        if (request.condition() != null) {
            asset.setItemCondition(request.condition());
        }
        if (request.notes() != null && !request.notes().isBlank()) {
            asset.setNotes(request.notes());
        }
        asset.setUpdatedBy(updatedBy != null ? updatedBy : "system");

        CompanyAsset saved = assetRepository.save(asset);
        return toAssetResponse(saved, null);
    }

    @Override
    public AssetResponse assignAsset(UUID tenantId, UUID assetId, AssetAssignRequest request, String assignedBy) {
        CompanyAsset asset = assetRepository
                .findByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));

        if (asset.getCurrentStatus() == AssetStatus.ASSIGNED) {
            throw new AssetConflictException("Asset " + asset.getAssetTag() + " is already assigned");
        }
        if (asset.getCurrentStatus() != AssetStatus.AVAILABLE) {
            throw new AssetValidationException("Asset " + asset.getAssetTag()
                    + " is not available for assignment (current status: " + asset.getCurrentStatus() + ")");
        }

        if (request.employeeId() == null) {
            throw new AssetValidationException("Employee ID is required for assignment");
        }

        Optional<AssetAssignment> activeOpt =
                assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(tenantId, assetId);
        if (activeOpt.isPresent()) {
            throw new AssetConflictException("Asset already has an active assignment");
        }

        LocalDate assignDate = request.assignedDate() != null ? request.assignedDate() : LocalDate.now();
        AssetCondition cond = request.condition() != null ? request.condition() : asset.getItemCondition();

        AssetAssignment assignment = new AssetAssignment();
        assignment.setTenantId(tenantId);
        assignment.setAssetId(assetId);
        assignment.setEmployeeId(request.employeeId());
        assignment.setAssignedDate(assignDate);
        assignment.setAssignedBy(assignedBy != null ? assignedBy : "system");
        assignment.setAssignmentCondition(cond);
        assignment.setAssignmentNotes(request.notes());
        assignment.setActive(true);
        assignment.setCreatedBy(assignedBy != null ? assignedBy : "system");
        assignment.setUpdatedBy(assignedBy != null ? assignedBy : "system");

        AssetAssignment savedAssignment = assignmentRepository.save(assignment);

        asset.setCurrentStatus(AssetStatus.ASSIGNED);
        asset.setItemCondition(cond);
        asset.setUpdatedBy(assignedBy != null ? assignedBy : "system");
        CompanyAsset savedAsset = assetRepository.save(asset);

        Map<UUID, String> names = employeeService.displayNames(List.of(request.employeeId()));
        AssetAssignmentResponse assignmentResp = toAssignmentResponse(savedAssignment, savedAsset, names);

        return toAssetResponse(savedAsset, assignmentResp);
    }

    @Override
    public AssetResponse returnAsset(UUID tenantId, UUID assetId, AssetReturnRequest request, String returnedBy) {
        CompanyAsset asset = assetRepository
                .findByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));

        if (asset.getCurrentStatus() != AssetStatus.ASSIGNED) {
            throw new AssetValidationException("Asset " + asset.getAssetTag() + " is not currently assigned");
        }

        AssetAssignment activeAssignment = assignmentRepository
                .findByTenantIdAndAssetIdAndIsActiveTrue(tenantId, assetId)
                .orElseThrow(() ->
                        new AssetValidationException("No active assignment found for asset " + asset.getAssetTag()));

        LocalDate returnDate = request.returnedDate() != null ? request.returnedDate() : LocalDate.now();
        if (returnDate.isBefore(activeAssignment.getAssignedDate())) {
            throw new AssetValidationException("Return date cannot be before assignment date");
        }

        AssetCondition returnCond = request.condition() != null ? request.condition() : AssetCondition.GOOD;

        activeAssignment.setActive(false);
        activeAssignment.setReturnedDate(returnDate);
        activeAssignment.setReturnedBy(returnedBy != null ? returnedBy : "system");
        activeAssignment.setReturnCondition(returnCond);
        activeAssignment.setReturnNotes(request.notes());
        activeAssignment.setUpdatedBy(returnedBy != null ? returnedBy : "system");
        assignmentRepository.save(activeAssignment);

        asset.setCurrentStatus(AssetStatus.AVAILABLE);
        asset.setItemCondition(returnCond);
        asset.setUpdatedBy(returnedBy != null ? returnedBy : "system");
        CompanyAsset savedAsset = assetRepository.save(asset);

        return toAssetResponse(savedAsset, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetAssignmentResponse> getAssignmentHistory(UUID tenantId, UUID assetId) {
        CompanyAsset asset = assetRepository
                .findByIdAndTenantId(assetId, tenantId)
                .orElseThrow(() -> new AssetNotFoundException("Asset not found with id: " + assetId));

        List<AssetAssignment> history =
                assignmentRepository.findByTenantIdAndAssetIdOrderByAssignedDateDesc(tenantId, assetId);
        List<UUID> employeeIds = history.stream()
                .map(AssetAssignment::getEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> employeeNames = employeeIds.isEmpty() ? Map.of() : employeeService.displayNames(employeeIds);

        return history.stream()
                .map(a -> toAssignmentResponse(a, asset, employeeNames))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeClearanceResponse getEmployeeClearance(UUID tenantId, UUID employeeId) {
        List<AssetAssignment> activeAssignments =
                assignmentRepository.findByTenantIdAndEmployeeIdAndIsActiveTrue(tenantId, employeeId);
        if (activeAssignments.isEmpty()) {
            return new EmployeeClearanceResponse(employeeId, ClearanceStatus.CLEAR, 0, Collections.emptyList());
        }

        List<UUID> assetIds = activeAssignments.stream()
                .map(AssetAssignment::getAssetId)
                .distinct()
                .toList();
        List<CompanyAsset> assets = assetRepository.findByTenantIdAndIdIn(tenantId, assetIds);
        Map<UUID, CompanyAsset> assetMap =
                assets.stream().collect(Collectors.toMap(CompanyAsset::getId, Function.identity(), (a, b) -> a));

        Map<UUID, String> employeeNames = employeeService.displayNames(List.of(employeeId));

        List<AssetResponse> activeResponses = new ArrayList<>();
        for (AssetAssignment assignment : activeAssignments) {
            CompanyAsset asset = assetMap.get(assignment.getAssetId());
            if (asset != null) {
                AssetAssignmentResponse assignResp = toAssignmentResponse(assignment, asset, employeeNames);
                activeResponses.add(toAssetResponse(asset, assignResp));
            }
        }

        return new EmployeeClearanceResponse(
                employeeId, ClearanceStatus.PENDING_RETURN, activeResponses.size(), activeResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> getAssignedAssetsForEmployee(UUID tenantId, UUID employeeId) {
        return getEmployeeClearance(tenantId, employeeId).activeAssets();
    }

    private AssetResponse toAssetResponse(CompanyAsset asset, AssetAssignmentResponse activeAssignment) {
        return new AssetResponse(
                asset.getId(),
                asset.getAssetTag(),
                asset.getAssetName(),
                asset.getCategory(),
                asset.getBrand(),
                asset.getModelNumber(),
                asset.getSerialNumber(),
                asset.getPurchaseDate(),
                asset.getPurchaseCost(),
                asset.getWarrantyExpiry(),
                asset.getVendorName(),
                asset.getCurrentStatus(),
                asset.getItemCondition(),
                asset.getNotes(),
                activeAssignment,
                asset.getCreatedAt(),
                asset.getUpdatedAt());
    }

    private AssetAssignmentResponse toAssignmentResponse(
            AssetAssignment assignment, CompanyAsset asset, Map<UUID, String> employeeNames) {
        String empName = employeeNames.get(assignment.getEmployeeId());
        return new AssetAssignmentResponse(
                assignment.getId(),
                assignment.getAssetId(),
                asset != null ? asset.getAssetTag() : null,
                asset != null ? asset.getAssetName() : null,
                assignment.getEmployeeId(),
                empName,
                assignment.getAssignedDate(),
                assignment.getAssignedBy(),
                assignment.getAssignmentCondition(),
                assignment.getAssignmentNotes(),
                assignment.getReturnedDate(),
                assignment.getReturnedBy(),
                assignment.getReturnCondition(),
                assignment.getReturnNotes(),
                assignment.isActive(),
                assignment.getCreatedAt());
    }
}
