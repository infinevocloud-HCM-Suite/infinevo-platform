package com.itsdev.payroll.serviceimpl.employeeitdeclaration;

import com.itsdev.payroll.dto.employeeitdeclaration.poi.*;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.NewTaxCalculationResult;
import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.OldTaxCalculationResult;
import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvLetOutProperty;
import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvLetOutPropertyDetail;
import com.itsdev.payroll.entity.EmployeeITDeclaration.EmployeeInvestmentDeclaration;
import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.*;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.mapper.employeeitdeclaration.EmployeePOIMapper;
import com.itsdev.payroll.mapper.employeeitdeclaration.POIItemCommentMapper;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.EmployeeInvestmentDeclarationRepository;
import com.itsdev.payroll.repository.employeeitdeclaration.poi.*;
import com.itsdev.payroll.repository.organization.OrganizationRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.claimsanddeclarations.POISettingsService;
import com.itsdev.payroll.service.employeeitdeclaration.EmployeeProofOfInvestmentService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.NewTaxCalculationService;
import com.itsdev.payroll.service.employeeitdeclaration.taxCalculator.OldTaxCalculationService;
import com.itsdev.payroll.util.MonthUtil;
import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;
import com.itsdev.payroll.enumeration.TdsSourceType;
import com.itsdev.payroll.repository.employeeTDS.EmployeeTdsRepository;
import com.itsdev.payroll.service.BrevoEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import com.itsdev.payroll.entity.claimsanddeclarations.ProofOfInvestment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeProofOfInvestmentServiceImpl implements EmployeeProofOfInvestmentService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProofOfInvestmentServiceImpl.class);
    private static final PayRunStatus SUBMITTED_STATUS = PayRunStatus.valueOf("SUBMITTED");

    private final EmployeeProofOfInvestmentRepository poiRepository;
    private final EmployeePOIItemRepository poiItemRepository;
    private final EmployeePOIDocumentRepository poiDocumentRepository;
    private final EmployeeInvestmentDeclarationRepository itDeclarationRepository;
    private final OrganizationRepository organizationRepository;
    private final BasicDetailsRepository basicDetailsRepository;
    private final EmployeePOIItemCommentRepository commentRepository;
    private final POIItemCommentMapper commentMapper;
    private final CloudinaryService cloudinaryService;
    private final EmployeePOIMapper poiMapper;
    private final POISettingsService poiSettingsService;
    private final BrevoEmailService brevoEmailService;

    private final OldTaxCalculationService oldTaxCalculationService;
    private final NewTaxCalculationService newTaxCalculationService;
    private final EmployeeTdsRepository employeeTdsRepository;

    public EmployeeProofOfInvestmentServiceImpl(
            EmployeeProofOfInvestmentRepository poiRepository,
            EmployeePOIItemRepository poiItemRepository,
            EmployeePOIDocumentRepository poiDocumentRepository,
            EmployeeInvestmentDeclarationRepository itDeclarationRepository,
            OrganizationRepository organizationRepository,
            BasicDetailsRepository basicDetailsRepository,
            EmployeePOIItemCommentRepository commentRepository,
            POIItemCommentMapper commentMapper,
            CloudinaryService cloudinaryService,
            EmployeePOIMapper poiMapper,
            POISettingsService poiSettingsService,
            BrevoEmailService brevoEmailService,
            OldTaxCalculationService oldTaxCalculationService,
            NewTaxCalculationService newTaxCalculationService,
            EmployeeTdsRepository employeeTdsRepository) {
        this.poiRepository = poiRepository;
        this.poiItemRepository = poiItemRepository;
        this.poiDocumentRepository = poiDocumentRepository;
        this.itDeclarationRepository = itDeclarationRepository;
        this.organizationRepository = organizationRepository;
        this.basicDetailsRepository = basicDetailsRepository;
        this.cloudinaryService = cloudinaryService;
        this.poiMapper = poiMapper;
        this.commentRepository = commentRepository;
        this.commentMapper = commentMapper;
        this.poiSettingsService = poiSettingsService;
        this.brevoEmailService = brevoEmailService;
        this.oldTaxCalculationService = oldTaxCalculationService;
        this.newTaxCalculationService = newTaxCalculationService;
        this.employeeTdsRepository = employeeTdsRepository;
    }

    // ======================= HELPER: DEADLINE CHECK =======================
    private void checkDeadlineForEmployee(String organizationId, String action) {
        String lastDateStr = poiSettingsService.getLastDateForPoi(organizationId);

        if (lastDateStr != null && !lastDateStr.trim().isEmpty()) {
            try {
                LocalDate lastDate = LocalDate.parse(lastDateStr);
                LocalDate today = LocalDate.now();

                if (today.isAfter(lastDate)) {
                    throw new RuntimeException(
                            String.format("Cannot %s POI. Submission deadline passed on: %s",
                                    action, lastDateStr));
                }
            } catch (Exception e) {
                log.warn("Invalid last date format: {}", lastDateStr);
                // Don't throw for invalid date format
            }
        }
    }

    // ======================= HELPER: APPROVED STATUS CHECK =======================
    private void checkNotApprovedForEmployee(EmployeeProofOfInvestment poi, String action) {
        if (poi.getStatus() == PayRunStatus.APPROVED) {
            throw new RuntimeException(
                    String.format("Cannot %s. POI is already approved.", action));
        }
    }

    // ======================= HELPER: EMPLOYEE VALIDATION =======================
    private void validateEmployeeEdit(String organizationId, EmployeeProofOfInvestment poi, String action) {
        // 1. Check deadline
        checkDeadlineForEmployee(organizationId, action);

        // 2. Check not approved
        checkNotApprovedForEmployee(poi, action);
    }

    /**
     * Get current logged-in admin username
     */
    private String getCurrentAdminUsername() {
        try {
            // Try to get from Spring Security
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("Could not get authenticated user, using fallback: {}", e.getMessage());
        }

        // Fallback - you can get from thread local, session, or configuration
        return "SYSTEM_ADMIN";
    }

    private void validateLastDateForPoi(String organizationId, String action) {
        String lastDateStr = poiSettingsService.getLastDateForPoi(organizationId);

        if (lastDateStr != null && !lastDateStr.trim().isEmpty()) {
            try {
                LocalDate lastDate = LocalDate.parse(lastDateStr);
                LocalDate today = LocalDate.now();

                if (today.isAfter(lastDate)) {
                    throw new RuntimeException(
                            String.format("Cannot %s POI. Submission deadline passed on: %s",
                                    action, lastDateStr));
                }
            } catch (Exception e) {
                log.warn("Invalid last date format: {}", lastDateStr);
                // Don't throw error for invalid date format
            }
        }
    }

    // ======================= HELPER METHODS =======================

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Invalid authentication context");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("companyUserId");
    }

    private Organization getOrganization(String organizationId) {
        return organizationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
    }

    private BasicDetails getEmployee(String organizationId, String employeeId) {
        return basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException(
                        "Employee not found: " + employeeId + " in org " + organizationId));
    }

    private EmployeeProofOfInvestment getPoi(String organizationId, String employeeId, Integer fiscalYear) {
        Organization org = getOrganization(organizationId);
        BasicDetails employee = getEmployee(organizationId, employeeId);

        return poiRepository.findByOrganizationAndEmployeeAndFiscalYear(org, employee, fiscalYear)
                .orElseThrow(() -> new RuntimeException(
                        "Proof of Investment not found for fiscal year " + fiscalYear));
    }

    private void validatePoiStatus(EmployeeProofOfInvestment poi, PayRunStatus expectedStatus, String action) {
        if (poi.getStatus() != expectedStatus) {
            throw new RuntimeException(
                    String.format("Cannot %s POI. Current status is %s, expected %s",
                            action, poi.getStatus(), expectedStatus));
        }
    }

    private void validateAmounts(BigDecimal declaredAmount, BigDecimal actualAmount, BigDecimal approvedAmount) {
        // Actual amount cannot exceed declared amount
        if (actualAmount != null && declaredAmount != null &&
                actualAmount.compareTo(declaredAmount) > 0) {
            throw new RuntimeException(
                    "Actual amount cannot exceed declared amount. Declared: " +
                            declaredAmount + ", Actual: " + actualAmount);
        }

        // Approved amount cannot exceed actual amount
        if (approvedAmount != null && actualAmount != null &&
                approvedAmount.compareTo(actualAmount) > 0) {
            throw new RuntimeException(
                    "Approved amount cannot exceed actual amount. Actual: " +
                            actualAmount + ", Approved: " + approvedAmount);
        }
    }

    private void addAdminComment(EmployeePOIItem item, String newComment, String action) {
        if (newComment == null || newComment.trim().isEmpty()) {
            return; // Don't add empty comments
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String formattedComment = String.format("[%s - %s] %s", timestamp, action, newComment.trim());

        if (item.getAdminComment() == null || item.getAdminComment().isEmpty()) {
            item.setAdminComment(formattedComment);
        } else {
            // Append to existing comment
            item.setAdminComment(item.getAdminComment() + "\n\n" + formattedComment);
        }
    }

    // Helper method to check if user is admin
    private boolean isAdminUser(String userId) {
        // Implement based on your auth system
        // Example: Check user roles from JWT or user service
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().contains("ADMIN") ||
                            grantedAuthority.getAuthority().contains("ROLE_ADMIN"));
        }
        return false;
    }

    // ======================= COMMENT METHODS =======================

    @Override
    @Transactional
    public POIItemCommentDTO addCommentToPoiItem(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId,
            AddCommentRequest request) {

        String method = "addCommentToPoiItem";
        log.info("[{}] Adding comment to POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        if (!isAdmin) {
            // Employee is commenting - apply validation
            EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);
            validateEmployeeEdit(organizationId, poi, "add comment");
        }
        // === END VALIDATION ===

        // Get POI and validate access
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);
        EmployeePOIItem poiItem = poi.getPoiItems().stream()
                .filter(item -> item.getId().equals(poiItemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "POI item not found with ID: " + poiItemId));

        // TEMPORARILY REMOVED: POI status validation
        // if (poi.getStatus() == PayRunStatus.APPROVED || poi.getStatus() ==
        // PayRunStatus.REJECTED) {
        // throw new RuntimeException("Cannot add comment to POI in " + poi.getStatus()
        // + " status");
        // }

        // Validate comment content
        if (request.getComment() == null || request.getComment().trim().isEmpty()) {
            throw new RuntimeException("Comment cannot be empty");
        }

        // Create comment
        EmployeePOIItemComment comment = new EmployeePOIItemComment();
        comment.setPoiItem(poiItem);
        comment.setComment(request.getComment().trim());

        // Determine commenter (employee or admin)
        // String currentUserId = getCurrentUserId();
        // boolean isAdmin = isAdminUser(currentUserId);

        if (isAdmin) {
            comment.setCommentedByAdmin(currentUserId);
        } else {
            // TEMPORARILY REMOVED: Employee validation
            // if (!poi.getEmployee().getId().toString().equals(employeeId)) {
            // throw new RuntimeException("Employee can only comment on their own POI
            // items");
            // }

            // Always set as employee for now
            if (poi.getEmployee() != null) {
                comment.setCommentedByEmployee(poi.getEmployee());
            } else {
                // Fallback: create a dummy employee or handle differently
                log.warn("[{}] No employee found for POI, setting comment without employee", method);
            }
        }

        // Handle threaded reply
        if (request.getResponseToCommentId() != null) {
            EmployeePOIItemComment parentComment = commentRepository.findById(request.getResponseToCommentId())
                    .orElseThrow(() -> new RuntimeException(
                            "Parent comment not found with ID: " + request.getResponseToCommentId()));

            // Verify parent comment belongs to same POI item
            if (!parentComment.getPoiItem().getId().equals(poiItemId)) {
                throw new RuntimeException("Parent comment does not belong to this POI item");
            }

            comment.setResponseTo(parentComment);
        }

        // Save comment
        EmployeePOIItemComment savedComment = commentRepository.save(comment);

        // Also add to poiItem's collection for immediate access
        if (poiItem.getComments() == null) {
            poiItem.setComments(new ArrayList<>());
        }
        poiItem.getComments().add(savedComment);

        log.info("[{}] Comment added successfully: {}", method, savedComment.getId());
        return commentMapper.toDto(savedComment);
    }

    @Transactional(readOnly = true)
    public List<POIItemCommentDTO> getCommentsForPoiItem(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId) {

        String method = "getCommentsForPoiItem";
        log.info("[{}] Getting comments for POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        // TEMPORARILY REMOVED: Strict validation
        // Organization org = getOrganization(organizationId);
        // BasicDetails employee = getEmployee(organizationId, employeeId);
        // boolean poiExists =
        // poiRepository.findByOrganizationAndEmployeeAndFiscalYear(org, employee,
        // fiscalYear).isPresent();
        // if (!poiExists) {
        // throw new RuntimeException("POI not found for specified parameters");
        // }

        // Get comments directly
        List<EmployeePOIItemComment> comments = commentRepository.findByPoiItem_IdOrderByCreatedTimeAsc(poiItemId);

        return comments.stream()
                .map(commentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public POIItemCommentDTO updateComment(
            String organizationId,
            String employeeId,
            Long commentId,
            UpdateCommentRequest request) {

        // Find the comment and derive fiscalYear from its POI, then delegate to the
        // existing method
        EmployeePOIItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found with ID: " + commentId));

        EmployeePOIItem poiItem = comment.getPoiItem();
        EmployeeProofOfInvestment poi = poiItem.getProofOfInvestment();
        if (poi == null || poi.getFiscalYear() == null) {
            throw new RuntimeException("Associated POI or fiscal year not found for comment: " + commentId);
        }

        Integer fiscalYear = poi.getFiscalYear();
        return updateComment(organizationId, employeeId, fiscalYear, commentId, request);
    }

    @Transactional
    public POIItemCommentDTO updateComment(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long commentId,
            UpdateCommentRequest request) {

        String method = "updateComment";
        log.info("[{}] Updating comment {} for org={}, employee={}, fiscalYear={}",
                method, commentId, organizationId, employeeId, fiscalYear);

        // Get comment
        EmployeePOIItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found with ID: " + commentId));

        // TEMPORARILY REMOVED: Employee validation
        // EmployeePOIItem poiItem = comment.getPoiItem();
        // EmployeeProofOfInvestment poi = poiItem.getProofOfInvestment();
        // if (!poi.getOrganization().getOrganizationId().equals(organizationId) ||
        // !poi.getEmployee().getId().toString().equals(employeeId)) {
        // throw new RuntimeException("Comment does not belong to specified
        // employee/organization");
        // }

        // Validate comment content
        if (request.getComment() == null || request.getComment().trim().isEmpty()) {
            throw new RuntimeException("Comment cannot be empty");
        }

        // Verify user can edit (only original commenter within time limit)
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        boolean canEdit = false;

        if (isAdmin && comment.getCommentedByAdmin() != null &&
                comment.getCommentedByAdmin().equals(currentUserId)) {
            canEdit = true; // Admin can edit their own comments
        } else if (!isAdmin && comment.getCommentedByEmployee() != null &&
                comment.getCreatedTime().isAfter(fiveMinutesAgo)) {
            // TEMPORARILY REMOVED: Employee ID check
            // comment.getCommentedByEmployee().getId().toString().equals(employeeId) &&
            canEdit = true; // Employee can edit within 5 minutes
        }

        if (!canEdit) {
            throw new RuntimeException("You cannot edit this comment");
        }

        // Update comment
        comment.setComment(request.getComment().trim());
        EmployeePOIItemComment updatedComment = commentRepository.save(comment);

        log.info("[{}] Comment updated successfully", method);
        return commentMapper.toDto(updatedComment);
    }

    @Transactional
    public void deleteComment(
            String organizationId,
            String employeeId,
            Long commentId) {

        String method = "deleteComment";
        log.info("[{}] Deleting comment {} for org={}, employee={}",
                method, commentId, organizationId, employeeId);

        // Get comment
        EmployeePOIItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found with ID: " + commentId));

        // TEMPORARILY REMOVED: Employee validation
        // EmployeePOIItem poiItem = comment.getPoiItem();
        // EmployeeProofOfInvestment poi = poiItem.getProofOfInvestment();
        // if (!poi.getOrganization().getOrganizationId().equals(organizationId) ||
        // !poi.getEmployee().getId().toString().equals(employeeId)) {
        // throw new RuntimeException("Comment does not belong to specified
        // employee/organization");
        // }

        // Verify user can delete (only original commenter within time limit or admin)
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        boolean canDelete = false;

        if (isAdmin) {
            canDelete = true; // Admin can delete any comment
        } else if (comment.getCommentedByEmployee() != null &&
                comment.getCreatedTime().isAfter(fiveMinutesAgo)) {
            // TEMPORARILY REMOVED: Employee ID check
            // comment.getCommentedByEmployee().getId().toString().equals(employeeId) &&
            canDelete = true; // Employee can delete within 5 minutes
        }

        if (!canDelete) {
            throw new RuntimeException("You cannot delete this comment");
        }

        // Remove from poiItem's collection if exists
        EmployeePOIItem poiItem = comment.getPoiItem();
        if (poiItem.getComments() != null) {
            poiItem.getComments().remove(comment);
        }

        // Delete comment
        commentRepository.delete(comment);

        log.info("[{}] Comment deleted successfully", method);
    }
    // ======================= GET / INITIALIZE =======================

    @Override
    @Transactional
    public EmployeeProofOfInvestmentDTO getOrInitializePoi(
            String organizationId,
            String employeeId,
            Integer fiscalYear) {

        String method = "getOrInitializePoi";
        log.info("[{}] Getting/initializing POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        // === ADD SETTINGS VALIDATION ===
        ProofOfInvestment settings = poiSettingsService.getSettings(organizationId);

        // Check if POI is locked
        if (settings.isPoiLocked()) {
            throw new RuntimeException("POI is currently locked. Cannot access or initialize.");
        }

        // Check last date for POI
        validateLastDateForPoi(organizationId, "access");
        // === END SETTINGS VALIDATION ===

        Organization org = getOrganization(organizationId);
        BasicDetails employee = getEmployee(organizationId, employeeId);

        Optional<EmployeeProofOfInvestment> existingPoi = poiRepository.findByOrganizationAndEmployeeAndFiscalYear(org,
                employee, fiscalYear);

        if (existingPoi.isPresent()) {
            log.info("[{}] POI found, returning existing", method);
            return poiMapper.toDto(existingPoi.get());
        }

        // ✅ Use the NEW repository method that fetches ALL collections
        EmployeeInvestmentDeclaration itDeclaration = itDeclarationRepository
                .findByOrganizationAndEmployeeAndFiscalYear(
                        org, employee, fiscalYear)
                .orElseThrow(() -> new RuntimeException(
                        "IT Declaration not found for fiscal year " + fiscalYear));

        // ========== Debug logging ==========
        log.info("[{}] 🔍 DEBUG: Let Out Properties count: {}",
                method,
                itDeclaration.getLetOutProperties() != null ? itDeclaration.getLetOutProperties().size() : "null");

        log.info("[{}] 🔍 DEBUG: Section 6A count: {}",
                method,
                itDeclaration.getSection6aDeclarations() != null ? itDeclaration.getSection6aDeclarations().size()
                        : "null");

        log.info("[{}] 🔍 DEBUG: House Rents count: {}",
                method, itDeclaration.getHouseRents() != null ? itDeclaration.getHouseRents().size() : "null");
        // ========== End debug ==========

        // ========== NEW LOGIC START ==========
        String taxRegime = itDeclaration.getTaxRegime(); // This is "OLD" or "NEW"

        // Create POI - ALWAYS create, even for New Regime
        EmployeeProofOfInvestment poi = createPoiMaster(org, employee, fiscalYear, itDeclaration);

        // Store tax regime in Zoho format for POI
        if ("OLD".equalsIgnoreCase(taxRegime)) {
            poi.setTaxRegimeAtSubmission("with_exemptions");
        } else if ("NEW".equalsIgnoreCase(taxRegime)) {
            poi.setTaxRegimeAtSubmission("without_exemptions");
        } else {
            poi.setTaxRegimeAtSubmission(taxRegime); // fallback
        }

        // Only create investment items for Old Regime
        if ("OLD".equalsIgnoreCase(taxRegime)) {
            // Old Regime - create items as before
            List<EmployeePOIItem> poiItems = new ArrayList<>();
            poiItems.addAll(buildSection6AItems(poi, itDeclaration));
            poiItems.addAll(buildHouseRentItems(poi, itDeclaration));
            poiItems.addAll(buildOtherIncomeItems(poi, itDeclaration));
            poiItems.addAll(buildLetOutPropertyItems(poi, itDeclaration));
            poiItems.addAll(buildPrevEmploymentItems(poi, itDeclaration));
            poiItems.addAll(buildPreTaxDeductionItems(poi, itDeclaration));

            // if (poiItems.isEmpty()) {
            // throw new RuntimeException("No declaration data found to initialize POI");
            // }
            // poi.setPoiItems(poiItems);
            if (poiItems.isEmpty()) {
                log.info("[{}] Empty IT declaration - creating POI with zero items", method);
                // ✅ ALLOW EMPTY - employee declared no investments (like Zoho)
            }
            poi.setPoiItems(poiItems); // This can be empty list ✅
        } else {
            // New Regime - empty items
            poi.setPoiItems(new ArrayList<>());
            // Optional: You might want to auto-approve New Regime POIs since they have no
            // investments
            // poi.setStatus(PayRunStatus.APPROVED);
            // poi.setSubmittedBy("SYSTEM");
            // poi.setSubmittedDate(LocalDateTime.now());
            // poi.setApprovedBy("SYSTEM");
            // poi.setApprovedDate(LocalDateTime.now());
        }
        // ========== NEW LOGIC END ==========

        EmployeeProofOfInvestment savedPoi = poiRepository.save(poi);

        log.info("[{}] POI initialized successfully with {} items",
                method, poi.getPoiItems().size());

        return poiMapper.toDto(savedPoi);
    }

    private EmployeeProofOfInvestment createPoiMaster(

            Organization org,

            BasicDetails employee,

            Integer fiscalYear,

            EmployeeInvestmentDeclaration declaration) {

        EmployeeProofOfInvestment poi = new EmployeeProofOfInvestment();

        poi.setOrganization(org);

        poi.setEmployee(employee);

        poi.setFiscalYear(fiscalYear);

        poi.setDeclaration(declaration);

        poi.setTaxRegimeAtSubmission(declaration.getTaxRegime());

        poi.setStatus(PayRunStatus.DRAFT);

        poi.setConsideredForIt(Boolean.FALSE);

        return poi;

    }

    private List<EmployeePOIItem> buildSection6AItems(

            EmployeeProofOfInvestment poi,

            EmployeeInvestmentDeclaration decl) {

        if (decl.getSection6aDeclarations() == null)
            return List.of();

        return decl.getSection6aDeclarations().stream().map(sec -> {

            EmployeePOIItem item = new EmployeePOIItem();

            item.setProofOfInvestment(poi);

            item.setInvestmentType(sec.getType());

            item.setSection6aItemId(sec.getSection6aItemId());

            item.setDeclaredAmount(

                    sec.getAmount() != null ? sec.getAmount() : BigDecimal.ZERO);

            item.setActualAmount(BigDecimal.ZERO);

            item.setApprovedAmount(BigDecimal.ZERO);

            item.setStatus(PayRunStatus.DRAFT);

            // Initialize empty comments list
            item.setComments(new ArrayList<>());

            return item;

        }).toList();

    }

    private List<EmployeePOIItem> buildHouseRentItems(

            EmployeeProofOfInvestment poi,

            EmployeeInvestmentDeclaration decl) {

        if (decl.getHouseRents() == null)
            return List.of();

        return decl.getHouseRents().stream().map(rent -> {

            EmployeePOIItem item = new EmployeePOIItem();

            item.setProofOfInvestment(poi);

            item.setInvestmentType("HRA");

            item.setSection6aItemId(rent.getId());

            item.setDeclaredAmount(

                    rent.getAmountPerMonth().multiply(BigDecimal.valueOf(12)));

            item.setActualAmount(BigDecimal.ZERO);

            item.setApprovedAmount(BigDecimal.ZERO);

            item.setStatus(PayRunStatus.DRAFT);

            item.setItemIdExternal(rent.getItemIdExternal());

            // Initialize empty comments list
            item.setComments(new ArrayList<>());

            return item;

        }).toList();

    }

    private List<EmployeePOIItem> buildOtherIncomeItems(

            EmployeeProofOfInvestment poi,

            EmployeeInvestmentDeclaration decl) {

        if (decl.getOtherIncomes() == null)
            return List.of();

        return decl.getOtherIncomes().stream().map(inc -> {

            EmployeePOIItem item = new EmployeePOIItem();

            item.setProofOfInvestment(poi);

            item.setInvestmentType("OTHER_INCOME_" + inc.getType());

            item.setSection6aItemId(inc.getId());

            item.setDeclaredAmount(

                    inc.getDeclaredAmount() != null ? inc.getDeclaredAmount() : BigDecimal.ZERO);

            item.setActualAmount(BigDecimal.ZERO);

            item.setApprovedAmount(BigDecimal.ZERO);

            item.setStatus(PayRunStatus.DRAFT);

            item.setItemIdExternal(inc.getItemIdExternal());

            // Initialize empty comments list
            item.setComments(new ArrayList<>());

            return item;

        }).toList();

    }

    private List<EmployeePOIItem> buildLetOutPropertyItems(
            EmployeeProofOfInvestment poi,
            EmployeeInvestmentDeclaration decl) {

        if (decl.getLetOutProperties() == null)
            return List.of();

        return decl.getLetOutProperties().stream().map(prop -> {
            EmployeePOIItem item = new EmployeePOIItem();
            item.setProofOfInvestment(poi);
            item.setInvestmentType("LET_OUT_PROPERTY");
            item.setSection6aItemId(prop.getId());

            // Calculate declared amount as sum of property details
            BigDecimal totalDeclared = BigDecimal.ZERO;
            if (prop.getPropertyDetails() != null) {
                for (EmployeeInvLetOutPropertyDetail detail : prop.getPropertyDetails()) {
                    totalDeclared = totalDeclared.add(detail.getAmount());

                    // Create POI property detail
                    EmployeePOIPropertyDetail poiDetail = new EmployeePOIPropertyDetail();
                    poiDetail.setPoiItem(item);
                    poiDetail.setType(detail.getType());
                    poiDetail.setAmount(detail.getAmount());
                    poiDetail.setNameOfLender(detail.getNameOfLender());
                    poiDetail.setPanOfLender(detail.getPanOfLender());

                    item.getPropertyDetails().add(poiDetail);
                }
            }

            item.setDeclaredAmount(totalDeclared);
            item.setActualAmount(BigDecimal.ZERO);
            item.setApprovedAmount(BigDecimal.ZERO);
            item.setStatus(PayRunStatus.DRAFT);
            item.setItemIdExternal(prop.getItemIdExternal());
            item.setComments(new ArrayList<>());
            return item;
        }).toList();
    }

    private List<EmployeePOIItem> buildPrevEmploymentItems(

            EmployeeProofOfInvestment poi,

            EmployeeInvestmentDeclaration decl) {

        if (decl.getPrevEmploymentDeclarations() == null)
            return List.of();

        return decl.getPrevEmploymentDeclarations().stream().map(prev -> {

            EmployeePOIItem item = new EmployeePOIItem();

            item.setProofOfInvestment(poi);

            item.setInvestmentType("PREV_EMPLOYMENT_" + prev.getType());

            item.setSection6aItemId(prev.getId());

            item.setDeclaredAmount(prev.getDeclaredAmount());

            item.setActualAmount(BigDecimal.ZERO);

            item.setApprovedAmount(BigDecimal.ZERO);

            item.setStatus(PayRunStatus.DRAFT);

            item.setItemIdExternal(prev.getItemIdExternal());

            // Initialize empty comments list
            item.setComments(new ArrayList<>());

            return item;

        }).toList();

    }

    private List<EmployeePOIItem> buildPreTaxDeductionItems(

            EmployeeProofOfInvestment poi,

            EmployeeInvestmentDeclaration decl) {

        if (decl.getPreTaxDeductions() == null)
            return List.of();

        return decl.getPreTaxDeductions().stream().map(pre -> {

            EmployeePOIItem item = new EmployeePOIItem();

            item.setProofOfInvestment(poi);

            item.setInvestmentType("PRE_TAX_" + pre.getType());

            item.setSection6aItemId(pre.getId());

            item.setDeclaredAmount(pre.getInvestmentAmount());

            item.setActualAmount(BigDecimal.ZERO);

            item.setApprovedAmount(BigDecimal.ZERO);

            item.setStatus(PayRunStatus.DRAFT);

            // Initialize empty comments list
            item.setComments(new ArrayList<>());

            return item;

        }).toList();

    }

    // ======================= POI ITEM OPERATIONS =======================

    @Override
    @Transactional
    public EmployeePOIItemDTO updatePoiItem(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId,
            POIItemUpdateRequest request) {

        // === CHECK IF USER IS ADMIN ===
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        // === ADD SETTINGS VALIDATION ===
        // Check if POI is locked
        // if (poiSettingsService.isPoiLocked(organizationId)) {
        // throw new RuntimeException("Cannot update POI item. POI is locked.");
        // }

        // Check last date for POI
        // validateLastDateForPoi(organizationId, "update");
        // === END SETTINGS VALIDATION ===

        String method = "updatePoiItem";
        log.info("[{}] Updating POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        // Get POI and validate status
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        if (!isAdmin) {
            // === EMPLOYEE VALIDATION ===
            // Check if POI is locked (only for employee)
            if (poiSettingsService.isPoiLocked(organizationId)) {
                throw new RuntimeException("Cannot update POI item. POI is locked.");
            }

            validateEmployeeEdit(organizationId, poi, "update item");
        }
        // Find the POI item
        EmployeePOIItem poiItem = poi.getPoiItems().stream()
                .filter(item -> item.getId().equals(poiItemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "POI item not found with ID: " + poiItemId));

        // Validate amounts
        validateAmounts(
                poiItem.getDeclaredAmount(),
                request.getActualAmount(),
                null // approved amount not being updated here
        );

        // Update item
        if (request.getActualAmount() != null) {
            poiItem.setActualAmount(request.getActualAmount());
        }

        if (request.getInvestmentType() != null) {
            poiItem.setInvestmentType(request.getInvestmentType());
        }

        if (request.getSection6aItemId() != null) {
            poiItem.setSection6aItemId(request.getSection6aItemId());
        }

        // Save
        EmployeePOIItem savedItem = poiItemRepository.save(poiItem);
        log.info("[{}] POI item updated successfully", method);

        return poiMapper.toItemDto(savedItem);

    }

    @Override
    @Transactional
    public void deletePoiItem(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId) {

        String method = "deletePoiItem";
        log.info("[{}] Deleting POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        // === CHECK IF USER IS ADMIN ===
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        // Get POI and validate status
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        if (!isAdmin) {
            // === EMPLOYEE VALIDATION ===
            validateEmployeeEdit(organizationId, poi, "delete item");
        }
        // Find and remove the item
        boolean removed = poi.getPoiItems().removeIf(item -> item.getId().equals(poiItemId));

        if (!removed) {
            throw new RuntimeException("POI item not found with ID: " + poiItemId);
        }

        // Also delete associated comments
        List<EmployeePOIItemComment> comments = commentRepository.findByPoiItem_IdOrderByCreatedTimeAsc(poiItemId);
        if (!comments.isEmpty()) {
            commentRepository.deleteAll(comments);
            log.info("[{}] Deleted {} associated comments", method, comments.size());
        }

        // Save POI (orphanRemoval will handle deletion)
        poiRepository.save(poi);
        log.info("[{}] POI item deleted successfully", method);
    }

    // ======================= DOCUMENT OPERATIONS =======================

    @Override
    @Transactional
    public List<EmployeePOIDocumentDTO> uploadDocuments(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId,
            List<MultipartFile> files) throws IOException {

        // === CHECK IF USER IS ADMIN ===
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        // === CHECK SETTINGS ===
        // ProofOfInvestment settings = poiSettingsService.getSettings(organizationId);
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        if (!isAdmin) {
            // === EMPLOYEE VALIDATION ===
            ProofOfInvestment settings = poiSettingsService.getSettings(organizationId);

            if (!settings.isAttachmentEnabledForPoi()) {
                throw new RuntimeException("Document upload is disabled by administrator.");
            }

            // Check if POI is locked
            if (settings.isPoiLocked()) {
                throw new RuntimeException("Cannot upload documents. POI is locked.");
            }

            // Check last date for POI
            validateLastDateForPoi(organizationId, "upload documents");
            // === END SETTINGS CHECK ===
            // validatePoiStatus(poi, PayRunStatus.DRAFT, "upload document");
            validateEmployeeEdit(organizationId, poi, "upload document");

        }

        EmployeePOIItem poiItem = poi.getPoiItems().stream()
                .filter(item -> item.getId().equals(poiItemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("POI item not found: " + poiItemId));

        List<EmployeePOIDocumentDTO> result = new ArrayList<>();

        for (MultipartFile file : files) {

            var uploadResponse = cloudinaryService.uploadEmployeeInvestmentFile(
                    file, organizationId, employeeId, fiscalYear);

            EmployeePOIDocument document = new EmployeePOIDocument();
            document.setPoiItem(poiItem);
            document.setDocumentName(file.getOriginalFilename());
            document.setDocumentUrl(uploadResponse.getSecure_url());
            document.setUploadedBy(getCurrentUserId());

            poiItem.getDocuments().add(document);

            EmployeePOIDocument saved = poiDocumentRepository.save(document);

            try {
                java.lang.reflect.Method m = poiMapper.getClass().getDeclaredMethod("toDocumentDto",
                        EmployeePOIDocument.class);
                m.setAccessible(true);
                EmployeePOIDocumentDTO dto = (EmployeePOIDocumentDTO) m.invoke(poiMapper, saved);
                result.add(dto);
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert document to DTO via mapper", e);
            }
        }

        return result;

    }

    @Override
    @Transactional
    public void deleteDocument(
            String organizationId,
            String employeeId,
            Long documentId) {

        String method = "deleteDocument";
        log.info("[{}] Deleting document {} for org={}, employee={}",
                method, documentId, organizationId, employeeId);

        // Find document
        EmployeePOIDocument document = poiDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException(
                        "Document not found with ID: " + documentId));

        // Get POI from document path
        EmployeePOIItem poiItem = document.getPoiItem();
        EmployeeProofOfInvestment poi = poiItem.getProofOfInvestment();

        // Validate organization and employee
        if (!poi.getOrganization().getOrganizationId().equals(organizationId) ||
                !poi.getEmployee().getEmployeeId().equals(employeeId)) {
            throw new RuntimeException("Document does not belong to specified employee/organization");
        }

        // === CHECK IF USER IS ADMIN ===
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        if (!isAdmin) {
            // === EMPLOYEE VALIDATION ===
            validateEmployeeEdit(organizationId, poi, "delete document");
        }

        // Validate POI status
        validatePoiStatus(poi, PayRunStatus.DRAFT, "delete document");

        // Delete from Cloudinary (optional - may want to keep for audit)
        try {
            cloudinaryService.deleteFile(document.getDocumentUrl());
        } catch (Exception e) {
            log.warn("[{}] Failed to delete file from Cloudinary: {}", method, e.getMessage());
            // Continue with database deletion even if Cloudinary fails
        }

        // Remove from POI item and delete
        poiItem.getDocuments().remove(document);
        poiDocumentRepository.delete(document);

        log.info("[{}] Document deleted successfully", method);
    }

    // ======================= WORKFLOW OPERATIONS =======================

    @Override
    @Transactional
    public EmployeeProofOfInvestmentDTO submitPoi(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            POISubmitRequest request) {

        String method = "submitPoi";
        log.info("[{}] Submitting POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        // === ADD COMPLETE SETTINGS VALIDATION ===
        ProofOfInvestment settings = poiSettingsService.getSettings(organizationId);

        // Check if POI is locked
        if (settings.isPoiLocked()) {
            throw new RuntimeException("Cannot submit POI. Submission is locked.");
        }

        // Check last date for POI
        validateLastDateForPoi(organizationId, "submit");

        // Attempt to extract tax regime from request via reflection (handles requests
        // without a getTaxRegime method)
        Object taxRegimeValue = null;
        if (request != null) {
            try {
                java.lang.reflect.Method m = request.getClass().getMethod("getTaxRegime");
                taxRegimeValue = m.invoke(request);
            } catch (NoSuchMethodException e1) {
                try {
                    java.lang.reflect.Method m2 = request.getClass().getMethod("getTaxRegimeAtSubmission");
                    taxRegimeValue = m2.invoke(request);
                } catch (NoSuchMethodException e2) {
                    // No known getter found; leave taxRegimeValue as null
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to read tax regime from request", ex);
                }
            } catch (Exception ex) {
                throw new RuntimeException("Failed to read tax regime from request", ex);
            }
        }

        // Check if tax regime can be changed
        if (!settings.isCanChangeTaxRegimePoi() && taxRegimeValue != null) {
            throw new RuntimeException("Cannot change tax regime. This feature is disabled.");
        }
        // === END SETTINGS VALIDATION ===

        // Get POI
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);
        validatePoiStatus(poi, PayRunStatus.DRAFT, "submit");

        // Get items that actually have amounts (invested in)
        List<EmployeePOIItem> itemsWithAmounts = poi.getPoiItems().stream()
                .filter(item -> item.getActualAmount() != null &&
                        item.getActualAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        // Must have at least one item with actual amount
        // if (itemsWithAmounts.isEmpty()) {
        // throw new RuntimeException(
        // "Cannot submit POI. No investment items have actual amounts.");
        // }
        // ✅ ALLOW ZERO AMOUNTS - employee declared no investments
        if (itemsWithAmounts.isEmpty()) {
            log.info("[{}] POI submission with zero investments - employee declared no tax-saving investments", method);
            // Allow submission - this means higher TDS for employee (valid business case)
            // Don't throw exception!
        }

        // === CHECK ATTACHMENT REQUIREMENTS BASED ON SETTINGS ===
        boolean attachmentMandatory = settings.isAttachmentMandatoryPoiForPortal();
        boolean attachmentEnabled = settings.isAttachmentEnabledForPoi();

        if (attachmentMandatory) {
            boolean itemsWithAmountsHaveDocs = itemsWithAmounts.stream()
                    .allMatch(item -> !item.getDocuments().isEmpty());

            if (!itemsWithAmountsHaveDocs) {
                // Count items missing documents
                long itemsMissingDocs = itemsWithAmounts.stream()
                        .filter(item -> item.getDocuments().isEmpty())
                        .count();

                throw new RuntimeException(
                        String.format(
                                "Cannot submit POI. %d investment item(s) with amounts are missing supporting documents. "
                                        +
                                        "Please upload proof for all investments.",
                                itemsMissingDocs));
            }
        }

        if (!attachmentEnabled && poi.getPoiItems().stream()
                .anyMatch(item -> !item.getDocuments().isEmpty())) {
            throw new RuntimeException(
                    "Cannot submit POI. Document upload is disabled by administrator.");
        }
        // === END ATTACHMENT VALIDATION ===

        // Check if comments are mandatory for approval
        if (settings.isCommentsMandatoryForPoiApproval()) {
            // You can add logic here to check if admin comments are required
            // This would be validated during approval, not submission
        }

        // Update POI for submission
        poi.setStatus(SUBMITTED_STATUS);
        poi.setSubmittedBy(getCurrentUserId());
        poi.setSubmittedDate(LocalDateTime.now());

        // ================= SYNC IT DECLARATION =================
        EmployeeInvestmentDeclaration declaration = poi.getDeclaration();

        if (declaration != null && !"SUBMITTED".equals(declaration.getStatus())) {
            declaration.setStatus("SUBMITTED");
            declaration.setStatusFormatted("Submitted");
            declaration.setMessageTypes("SUCCESS");
            declaration.setCanAllowEdit(false); // 🔒 lock declaration
        }

        // Set tax regime if provided and allowed
        if (taxRegimeValue != null && settings.isCanChangeTaxRegimePoi()) {
            poi.setTaxRegimeAtSubmission(taxRegimeValue.toString());
        }

        // Update all items to SUBMITTED
        poi.getPoiItems().forEach(item -> item.setStatus(SUBMITTED_STATUS));

        // Send email notification if configured
        sendSubmissionNotifications(organizationId, employeeId, fiscalYear, settings);

        // Save POI
        EmployeeProofOfInvestment savedPoi = poiRepository.save(poi);

        // ==================== SEND SUBMISSION CONFIRMATION EMAIL ====================
        try {
            log.info("Sending POI submission confirmation email to employee: {}", employeeId);

            // Get employee and organization
            Organization org = getOrganization(organizationId);
            BasicDetails employee = getEmployee(organizationId, employeeId);

            // Send submission email
            boolean emailSent = brevoEmailService.sendPOISubmissionEmail(
                    employee, org, fiscalYear);

            if (emailSent) {
                log.info("POI submission email sent successfully to {}", employeeId);
            } else {
                log.warn("Failed to send POI submission email to {}", employeeId);
            }
        } catch (Exception e) {
            log.error("Error sending POI submission email to {}: {}",
                    employeeId, e.getMessage());
            // Don't fail the submission if email fails
        }
        // ==================== END EMAIL SENDING ====================

        log.info("[{}] POI submitted successfully", method);
        return poiMapper.toDto(savedPoi);
    }

    @Override
    @Transactional
    public EmployeeProofOfInvestmentDTO withdrawPoi(
            String organizationId,
            String employeeId,
            Integer fiscalYear) {

        String method = "withdrawPoi";
        log.info("[{}] Withdrawing POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        // === EMPLOYEE ONLY - PREVENT ADMIN ===
        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdminUser(currentUserId);

        if (isAdmin) {
            throw new RuntimeException("Admin cannot withdraw POI. Use reject instead.");
        }

        // Get POI
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);
        // validatePoiStatus(poi, SUBMITTED_STATUS, "withdraw");
        validateEmployeeEdit(organizationId, poi, "withdraw");

        // Update POI back to DRAFT
        poi.setStatus(PayRunStatus.DRAFT);
        poi.setSubmittedBy(null);
        poi.setSubmittedDate(null);

        // Update all items back to DRAFT
        poi.getPoiItems().forEach(item -> item.setStatus(PayRunStatus.DRAFT));

        // Save and return
        EmployeeProofOfInvestment savedPoi = poiRepository.save(poi);
        log.info("[{}] POI withdrawn successfully", method);

        return poiMapper.toDto(savedPoi);
    }

    // ======================= ADMIN OPERATIONS =======================

    // In your EmployeeProofOfInvestmentServiceImpl.java

    @Override
    public Page<EmployeeProofOfInvestmentResponseDTO> getAllPOISubmissions(
            String organizationId,
            Integer fiscalYear,
            String status,
            String taxRegime,
            int page,
            int perPage) {

        Pageable pageable = PageRequest.of(page - 1, perPage);

        // Convert frontend status to your enum values
        String statusParam = null;
        if (status != null && !status.equals("all")) {
            statusParam = mapFrontendStatusToEnum(status);
        }

        // Convert tax regime if needed (frontend might send "OLD"/"NEW")
        String taxRegimeParam = taxRegime;
        if ("OLD".equalsIgnoreCase(taxRegime)) {
            taxRegimeParam = "with_exemptions";
        } else if ("NEW".equalsIgnoreCase(taxRegime)) {
            taxRegimeParam = "without_exemptions";
        }

        Page<EmployeeProofOfInvestment> poiPage = poiRepository.findAllForAdminListing(
                organizationId, fiscalYear, statusParam, taxRegimeParam, pageable);

        return poiPage.map(this::convertToResponseDTOWithEmployeeDetails);
    }

    private String mapFrontendStatusToEnum(String frontendStatus) {
        if (frontendStatus == null)
            return null;

        // Direct mapping since your enum now has APPROVAL_PENDING
        switch (frontendStatus.toLowerCase()) {
            case "pending":
            case "approval pending":
                return "APPROVAL_PENDING"; // Your enum has this now
            case "draft":
                return "DRAFT";
            case "submitted":
                return "SUBMITTED";
            case "approved":
                return "APPROVED";
            case "rejected":
                return "REJECTED";
            case "completed":
                return "COMPLETED";
            case "ready":
                return "READY";
            default:
                // Try to use as-is
                return frontendStatus.toUpperCase();
        }
    }

    private EmployeeProofOfInvestmentResponseDTO convertToResponseDTOWithEmployeeDetails(
            EmployeeProofOfInvestment entity) {

        EmployeeProofOfInvestmentResponseDTO dto = new EmployeeProofOfInvestmentResponseDTO();

        // Map basic fields
        dto.setId(entity.getId());
        dto.setDeclarationId(entity.getDeclaration() != null ? entity.getDeclaration().getId() : null);

        // Convert IDs to String (since your DTO uses String)
        dto.setOrganizationId(entity.getOrganization() != null ? entity.getOrganization().getOrganizationId() : null); // This
                                                                                                                       // is
                                                                                                                       // already
                                                                                                                       // String

        dto.setEmployeeId(entity.getEmployee() != null ? entity.getEmployee().getEmployeeId() : null); // This is
                                                                                                       // already String

        dto.setFiscalYear(entity.getFiscalYear());
        dto.setTaxRegimeAtSubmission(entity.getTaxRegimeAtSubmission());
        dto.setStatus(entity.getStatus());
        dto.setSubmittedBy(entity.getSubmittedBy());
        dto.setSubmittedDate(entity.getSubmittedDate());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setApprovedDate(entity.getApprovedDate());
        dto.setConsideredForIt(entity.getConsideredForIt());
        dto.setCreatedTime(entity.getCreatedTime());
        dto.setUpdatedTime(entity.getUpdatedTime());
        dto.setLastEditedBy(determineLastEditedBy(entity));
        dto.setLastEditedDate(entity.getUpdatedTime());

        // Add employee details
        BasicDetails employee = entity.getEmployee();
        if (employee != null) {
            dto.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
            dto.setEmployeeCode(employee.getEmployeeNumber());
            dto.setEmployeeNumber(employee.getEmployeeNumber());
        }

        // Add organization name
        if (entity.getOrganization() != null) {
            dto.setOrganizationName(entity.getOrganization().getOrganizationName());
        }

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeProofOfInvestmentDTO getPoiForReview(
            String organizationId,
            String employeeId,
            Integer fiscalYear) {

        String method = "getPoiForReview";
        log.info("[{}] Getting POI for review for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        // Get POI - admin can view any status
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        // Convert to DTO
        EmployeeProofOfInvestmentDTO dto = poiMapper.toDto(poi);

        // MANUALLY ADD THE EXTRA FIELDS if mapper doesn't handle them
        if (poi.getEmployee() != null) {
            dto.setEmployeeName(poi.getEmployee().getFirstName() + " " + poi.getEmployee().getLastName());
            dto.setEmployeeNumber(poi.getEmployee().getEmployeeNumber());
        }

        // Determine lastEditedBy
        String lastEditedBy = determineLastEditedBy(poi);
        dto.setLastEditedBy(lastEditedBy);

        // lastEditedDate is the updatedTime
        dto.setLastEditedDate(poi.getUpdatedTime());

        // submittedBy should already be set by mapper
        // If not, set it:
        if (dto.getSubmittedBy() == null && poi.getSubmittedBy() != null) {
            dto.setSubmittedBy(poi.getSubmittedBy());
        }

        return dto;
    }

    // Helper method to determine who last edited
    private String determineLastEditedBy(EmployeeProofOfInvestment poi) {
        // Priority: submittedBy > approvedBy > created by logic
        if (poi.getSubmittedBy() != null && !poi.getSubmittedBy().isEmpty()) {
            return poi.getSubmittedBy();
        }

        if (poi.getApprovedBy() != null && !poi.getApprovedBy().isEmpty()) {
            return poi.getApprovedBy();
        }

        // If you track createdBy in entity, use that
        // return poi.getCreatedBy();

        // Fallback: use employee name
        if (poi.getEmployee() != null) {
            return poi.getEmployee().getFirstName() + " " + poi.getEmployee().getLastName();
        }

        return "Unknown";
    }

    @Override
    @Transactional
    public EmployeePOIItemDTO approvePoiItem(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId,
            String adminComment,
            BigDecimal approvedAmount) {

        String method = "approvePoiItem";
        log.info("[{}] Approving POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        // === CHECK SETTINGS ===
        ProofOfInvestment settings = poiSettingsService.getSettings(organizationId);

        if (settings.isCommentsMandatoryForPoiApproval() &&
                (adminComment == null || adminComment.trim().isEmpty())) {
            throw new RuntimeException("Admin comment is mandatory for POI approval.");
        }

        // Check if IT can override in proof mode
        if (!settings.isCanItOverrideInProofMode() && approvedAmount != null) {
            EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);
            EmployeePOIItem poiItem = poi.getPoiItems().stream()
                    .filter(item -> item.getId().equals(poiItemId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException(
                            "POI item not found with ID: " + poiItemId));

            // Allow admin to change amount even if IT override is disabled
            // Only log a warning instead of throwing exception
            if (approvedAmount.compareTo(poiItem.getActualAmount()) != 0) {
                log.warn("[{}] IT override is disabled but admin is changing amount from {} to {}",
                        method, poiItem.getActualAmount(), approvedAmount);
                // Don't throw exception - admin has authority
            }
        }
        // === END SETTINGS CHECK ===

        // Get POI - must be SUBMITTED for admin review
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        // Admin can approve if POI is SUBMITTED or APPROVAL_PENDING
        // if (poi.getStatus() != PayRunStatus.SUBMITTED &&
        // poi.getStatus() != PayRunStatus.APPROVAL_PENDING) {
        // throw new RuntimeException(
        // "POI must be in SUBMITTED or APPROVAL_PENDING status for admin review.
        // Current status: " +
        // poi.getStatus().getFormattedName());
        // }

        // Find the POI item
        EmployeePOIItem poiItem = poi.getPoiItems().stream()
                .filter(item -> item.getId().equals(poiItemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "POI item not found with ID: " + poiItemId));

        // Validate amounts
        validateAmounts(
                poiItem.getDeclaredAmount(),
                poiItem.getActualAmount(),
                approvedAmount);

        // ========== MAKE AMOUNT EDITABLE DURING APPROVAL ==========
        BigDecimal originalActualAmount = poiItem.getActualAmount();
        boolean amountWasChanged = false;

        if (approvedAmount != null) {
            // Check if admin is changing the amount
            if (!approvedAmount.equals(originalActualAmount)) {
                // ADMIN IS EDITING THE AMOUNT
                amountWasChanged = true;

                // Update BOTH actual amount and approved amount
                poiItem.setActualAmount(approvedAmount); // ✅ Edit the actual amount
                poiItem.setApprovedAmount(approvedAmount);
                poiItem.setAdminAdjusted(true); // Flag that admin adjusted this
                poiItem.setAmountAdjustedBy(getCurrentAdminUsername());
                poiItem.setAmountAdjustedDate(LocalDateTime.now());

                log.info("[{}] Admin changed amount from {} to {} for item {}",
                        method, originalActualAmount, approvedAmount, poiItemId);
            } else {
                // Same amount, just approve
                poiItem.setApprovedAmount(approvedAmount);
            }
        } else {
            // No amount specified, approve with actual amount
            poiItem.setApprovedAmount(originalActualAmount);
        }

        poiItem.setStatus(PayRunStatus.APPROVED);
        addAdminComment(poiItem, adminComment, "APPROVED");
        // ========== END OF EDITS ==========

        // Add auto-comment for approval if adminComment provided
        if (adminComment != null && !adminComment.trim().isEmpty()) {
            AddCommentRequest commentRequest = new AddCommentRequest();

            StringBuilder commentBuilder = new StringBuilder();
            commentBuilder.append("✅ APPROVED: ").append(adminComment.trim());

            if (amountWasChanged) {
                // Show amount was changed
                commentBuilder.append(String.format(
                        " | Amount adjusted from ₹%s to ₹%s",
                        originalActualAmount, approvedAmount));
            } else if (approvedAmount != null) {
                // Show approved amount (no change)
                commentBuilder.append(" | Approved Amount: ₹").append(approvedAmount);
            }

            commentRequest.setComment(commentBuilder.toString());

            // Optional: Set as response to last employee comment
            List<EmployeePOIItemComment> existingComments = commentRepository
                    .findByPoiItem_IdOrderByCreatedTimeAsc(poiItemId);

            if (!existingComments.isEmpty()) {
                EmployeePOIItemComment lastEmployeeComment = existingComments.stream()
                        .filter(c -> c.getCommentedByEmployee() != null)
                        .reduce((first, second) -> second) // Get last
                        .orElse(null);

                if (lastEmployeeComment != null) {
                    commentRequest.setResponseToCommentId(lastEmployeeComment.getId());
                }
            }

            try {
                addCommentToPoiItem(organizationId, employeeId, fiscalYear, poiItemId, commentRequest);
            } catch (Exception e) {
                log.warn("[{}] Failed to add auto-comment for approval: {}", method, e.getMessage());
            }
        }

        // Save
        EmployeePOIItem savedItem = poiItemRepository.save(poiItem);
        log.info("[{}] POI item approved successfully", method);

        return poiMapper.toItemDto(savedItem);
    }

    @Override
    @Transactional
    public EmployeePOIItemDTO rejectPoiItem(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            Long poiItemId,
            String adminComment) {

        String method = "rejectPoiItem";
        log.info("[{}] Rejecting POI item {} for org={}, employee={}, fiscalYear={}",
                method, poiItemId, organizationId, employeeId, fiscalYear);

        // Get POI
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        // Don't allow rejection of DRAFT POIs
        // if (poi.getStatus() == PayRunStatus.DRAFT) {
        // throw new RuntimeException(
        // "Cannot reject DRAFT POI. Employee must submit first. Current status:
        // DRAFT");
        // }

        // // Allow both SUBMITTED and APPROVAL_PENDING
        // if (poi.getStatus() != PayRunStatus.SUBMITTED &&
        // poi.getStatus() != PayRunStatus.APPROVAL_PENDING) {
        // throw new RuntimeException(
        // "POI must be in SUBMITTED or APPROVAL_PENDING status for admin review.
        // Current status: " +
        // poi.getStatus().getFormattedName());
        // }

        // Find the POI item
        EmployeePOIItem poiItem = poi.getPoiItems().stream()
                .filter(item -> item.getId().equals(poiItemId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "POI item not found with ID: " + poiItemId));

        // Update item
        poiItem.setApprovedAmount(BigDecimal.ZERO);
        poiItem.setStatus(PayRunStatus.REJECTED);
        addAdminComment(poiItem, adminComment, "REJECTED");

        // Add auto-comment for rejection if adminComment provided
        if (adminComment != null && !adminComment.trim().isEmpty()) {
            AddCommentRequest commentRequest = new AddCommentRequest();
            commentRequest.setComment("❌ REJECTED: " + adminComment.trim());

            // Optional: Set as response to last employee comment
            List<EmployeePOIItemComment> existingComments = commentRepository
                    .findByPoiItem_IdOrderByCreatedTimeAsc(poiItemId);

            if (!existingComments.isEmpty()) {
                EmployeePOIItemComment lastEmployeeComment = existingComments.stream()
                        .filter(c -> c.getCommentedByEmployee() != null)
                        .reduce((first, second) -> second) // Get last
                        .orElse(null);

                if (lastEmployeeComment != null) {
                    commentRequest.setResponseToCommentId(lastEmployeeComment.getId());
                }
            }

            try {
                addCommentToPoiItem(organizationId, employeeId, fiscalYear, poiItemId, commentRequest);
            } catch (Exception e) {
                log.warn("[{}] Failed to add auto-comment for rejection: {}", method, e.getMessage());
            }
        }

        // Save
        EmployeePOIItem savedItem = poiItemRepository.save(poiItem);
        log.info("[{}] POI item rejected successfully", method);

        return poiMapper.toItemDto(savedItem);
    }

    @Override
    @Transactional
    public EmployeeProofOfInvestmentDTO finalApprovePoi(
            String organizationId,
            String employeeId,
            Integer fiscalYear) {

        String method = "finalApprovePoi";
        log.info("[{}] Final approving POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        // Get POI
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        // if (poi.getStatus() != SUBMITTED_STATUS) {
        // throw new RuntimeException(
        // "POI must be in SUBMITTED status for final approval. Current status: " +
        // poi.getStatus());
        // }

        // Check if all items are approved/rejected
        // Only items with actual amount > 0 need review
        boolean allItemsWithAmountsReviewed = poi.getPoiItems().stream()
                .filter(item -> item.getActualAmount() != null &&
                        item.getActualAmount().compareTo(BigDecimal.ZERO) > 0)
                .allMatch(item -> item.getStatus() == PayRunStatus.APPROVED ||
                        item.getStatus() == PayRunStatus.REJECTED);

        if (!allItemsWithAmountsReviewed) {
            throw new RuntimeException(
                    "Cannot final approve POI. Some items with amounts are still pending review.");
        }

        // Auto-approve items with amount = 0
        poi.getPoiItems().stream()
                .filter(item -> item.getActualAmount() != null &&
                        item.getActualAmount().compareTo(BigDecimal.ZERO) == 0)
                .forEach(item -> {
                    item.setStatus(PayRunStatus.APPROVED);
                    item.setApprovedAmount(BigDecimal.ZERO);
                    if (item.getAdminComment() == null) {
                        item.setAdminComment("Auto-approved: No investment made");
                    }
                });

        // Update POI
        poi.setStatus(PayRunStatus.APPROVED);
        poi.setApprovedBy(getCurrentUserId());
        poi.setApprovedDate(LocalDateTime.now());
        // poi.setConsideredForIt(consideredForIt != null ? consideredForIt :
        // Boolean.TRUE);

        // ================= SYNC IT DECLARATION =================
        EmployeeInvestmentDeclaration declaration = poi.getDeclaration();

        if (declaration != null) {
            declaration.setStatus("APPROVED");
            declaration.setStatusFormatted("Approved");
            declaration.setMessageTypes("SUCCESS");
            declaration.setCanAllowEdit(false); // 🔒 lock declaration
        }

        // Save and return
        EmployeeProofOfInvestment savedPoi = poiRepository.save(poi);
        log.info("[{}] POI final approved successfully", method);

        return poiMapper.toDto(savedPoi);
    }

    @Override
    @Transactional
    public EmployeeProofOfInvestmentDTO finalRejectPoi(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            String adminComment) {

        String method = "finalRejectPoi";
        log.info("[{}] Final rejecting POI for org={}, employee={}, fiscalYear={}",
                method, organizationId, employeeId, fiscalYear);

        // Get POI - admin can reject ANY status
        EmployeeProofOfInvestment poi = getPoi(organizationId, employeeId, fiscalYear);

        // === CHANGE: Set status to DRAFT (not REJECTED) so employee can resubmit ===
        poi.setStatus(PayRunStatus.DRAFT); // ❌ CHANGED FROM PayRunStatus.REJECTED
        poi.setSubmittedBy(null); // Clear submission info
        poi.setSubmittedDate(null);
        poi.setApprovedBy(getCurrentUserId());
        poi.setApprovedDate(LocalDateTime.now());

        // Add admin comment to all items
        poi.getPoiItems().forEach(item -> {
            if (item.getAdminComment() == null || item.getAdminComment().isEmpty()) {
                item.setAdminComment("❌ REJECTED: " + adminComment);
            } else {
                item.setAdminComment(item.getAdminComment() + "\n\n❌ REJECTED: " + adminComment);
            }

            // Reset item status to DRAFT
            item.setStatus(PayRunStatus.DRAFT);
            item.setApprovedAmount(null);
        });

        // ================= SYNC IT DECLARATION =================
        EmployeeInvestmentDeclaration declaration = poi.getDeclaration();

        if (declaration != null) {
            declaration.setStatus("DRAFT"); // ❌ CHANGED FROM "REJECTED"
            declaration.setStatusFormatted("Draft");
            declaration.setMessageTypes("INFO");
            declaration.setCanAllowEdit(true); // 🔓 unlock declaration
        }

        // Save and return
        EmployeeProofOfInvestment savedPoi = poiRepository.save(poi);
        log.info("[{}] POI rejected and returned to DRAFT for resubmission", method);

        return poiMapper.toDto(savedPoi);
    }

    private void sendSubmissionNotifications(String organizationId, String employeeId,
            Integer fiscalYear, ProofOfInvestment settings) {
        try {
            // Send to employee if configured
            if (settings.isSendMailOnPoiRelease()) {
                log.info("Sending POI submission notification to employee: {}", employeeId);
                // Call your email service here
                // emailService.sendPOISubmissionEmailToEmployee(employeeId, fiscalYear);
            }

            // Send to admin/HR if configured
            if (settings.isSendMailOnEmployeeLevelPoiLockAndRelease()) {
                log.info("Sending POI submission notification to admin for employee: {}", employeeId);
                // Call your email service here
                // emailService.sendPOISubmissionEmailToAdmin(organizationId, employeeId,
                // fiscalYear);
            }
        } catch (Exception e) {
            log.error("Failed to send submission notifications: {}", e.getMessage());
            // Don't fail the submission if email fails
        }
    }

    // ======================= IT CONSIDERATION =======================
    @Override
    @Transactional
    public ConsiderForITResponse considerPOIForIT(
            String organizationId,
            String employeeId,
            Integer fiscalYear) {

        final String method = "considerPOIForIT";

        log.info("[{}] 🚀 START | orgId={} empId={} fy={}",
                method, organizationId, employeeId, fiscalYear);

        /*
         * =====================================================
         * 1️⃣ FETCH ORGANIZATION
         * =====================================================
         */
        Organization org = organizationRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));

        /*
         * =====================================================
         * 2️⃣ FETCH EMPLOYEE
         * =====================================================
         */
        BasicDetails employee = basicDetailsRepository
                .findByOrganization_OrganizationIdAndEmployeeId(
                        organizationId, employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + employeeId));

        /*
         * =====================================================
         * 3️⃣ FETCH POI
         * =====================================================
         */
        EmployeeProofOfInvestment poi = poiRepository
                .findByOrganizationAndEmployeeAndFiscalYear(
                        org, employee, fiscalYear)
                .orElseThrow(() -> new RuntimeException("Proof of Investment not found"));

        /*
         * =====================================================
         * 4️⃣ VALIDATIONS
         * =====================================================
         */
        if (poi.getStatus() != PayRunStatus.APPROVED) {
            throw new RuntimeException(
                    "POI must be APPROVED before considering for IT calculation");
        }

        if (Boolean.TRUE.equals(poi.getConsideredForIt())) {
            throw new RuntimeException(
                    "POI already considered for IT calculation");
        }

        /*
         * =====================================================
         * 5️⃣ FETCH IT DECLARATION
         * =====================================================
         */
        EmployeeInvestmentDeclaration declaration = itDeclarationRepository
                .findByOrganizationAndEmployeeAndFiscalYear(
                        org, employee, fiscalYear)
                .orElseThrow(() -> new RuntimeException("IT Declaration not found"));

        String taxRegime = declaration.getTaxRegime();

        if (taxRegime == null) {
            throw new RuntimeException("Tax regime not selected in IT declaration");
        }

        log.info("[{}] 📌 Tax Regime at consideration = {}",
                method, taxRegime);

        /*
         * =====================================================
         * 6️⃣ RECALCULATE FINAL TAX (ONE TIME)
         * =====================================================
         */
            Object taxCalculationResult;

            if ("OLD".equalsIgnoreCase(taxRegime)) {

                OldTaxCalculationResult result =
                        oldTaxCalculationService.calculateOldRegimeTaxUsingPOI(
                                organizationId,
                                employeeId,
                                fiscalYear);

                taxCalculationResult = result;

            } else if ("NEW".equalsIgnoreCase(taxRegime)) {

                NewTaxCalculationResult result =
                        newTaxCalculationService.calculateNewTax(
                                organizationId,
                                employeeId,
                                fiscalYear);

                taxCalculationResult = result;

            } else {
                throw new RuntimeException("Unsupported tax regime: " + taxRegime);
            }

        /*
        * =====================================================
        * 7️⃣ MARK POI AS CONSIDERED (NO TAX STORAGE IN POI)
        * =====================================================
        */
        poi.setConsideredForIt(true);
        poi.setConsideredDate(LocalDateTime.now());
        poiRepository.save(poi);

        log.info("[{}] 📌 POI marked as considered | poiId={}",
                method, poi.getId());

        /*
        * =====================================================
        * 7️⃣.5 STORE TDS INTO EMPLOYEE_TDS (SINGLE SOURCE OF TRUTH)
        * =====================================================
        */

        // 7.5.1 Deactivate existing active TDS (if any)
        employeeTdsRepository
                .findActiveByOrganizationAndEmployeeAndFiscalYear(
                    organizationId,
                    employeeId,
                    fiscalYear
            )
                .ifPresent(existing -> {
                    existing.setIsActive(false);
                    employeeTdsRepository.save(existing);
                });

        // 7.5.2 Fetch ORG-LEVEL POI SETTINGS (for effective month)
        ProofOfInvestment poiSettings =
                poiSettingsService.getSettings(organizationId);

        // 7.5.3 Create new employee_tds record
        EmployeeTds employeeTds = new EmployeeTds();

        employeeTds.setEmployeeId(employeeId);
        employeeTds.setOrganizationId(organizationId);
        employeeTds.setFiscalYear(fiscalYear);

        employeeTds.setTdsSourceType(TdsSourceType.POI_BASED);
        employeeTds.setPoiId(poi.getId());
        employeeTds.setTaxRegime(taxRegime);

        // 🔑 Store FINAL values from TAX DTOs (NO recalculation)
        if (taxCalculationResult instanceof OldTaxCalculationResult oldResult) {

            employeeTds.setAnnualGrossSalary(oldResult.getGrossIncome());
            employeeTds.setAnnualTaxableIncome(oldResult.getTaxableIncome());
            employeeTds.setFinalAnnualTax(oldResult.getTaxPayable());

        } else if (taxCalculationResult instanceof NewTaxCalculationResult newResult) {

            employeeTds.setAnnualGrossSalary(newResult.getGrossIncome());
            employeeTds.setAnnualTaxableIncome(newResult.getTaxableIncome());
            employeeTds.setFinalAnnualTax(newResult.getTaxPayable());
        }

        // ✅ Store STRING month directly (org-level config)
        employeeTds.setEffectiveFromMonth(
                poiSettings.getMonthToConsiderPoi()
        );

        employeeTds.setIsActive(true);

        // Save employee_tds
        EmployeeTds savedEmployeeTds =
                employeeTdsRepository.save(employeeTds);

        log.info(
                "[{}] 🧾 employee_tds saved | id={} | regime={} | finalTax={} | effectiveMonth={}",
                method,
                savedEmployeeTds.getId(),
                savedEmployeeTds.getTaxRegime(),
                savedEmployeeTds.getFinalAnnualTax(),
                savedEmployeeTds.getEffectiveFromMonth()
        );

        /*
        * =====================================================
        * 8️⃣ BUILD RESPONSE (FROM EMPLOYEE_TDS)
        * =====================================================
        */
        ConsiderForITResponse response = new ConsiderForITResponse();

        response.setPoiId(savedEmployeeTds.getPoiId());
        response.setEmployeeId(savedEmployeeTds.getEmployeeId());
        response.setFiscalYear(savedEmployeeTds.getFiscalYear());

        response.setStatus(poi.getStatus().name());
        response.setConsideredForIt(true);
        response.setConsideredDate(poi.getConsideredDate());

        // 🔥 SINGLE SOURCE OF TRUTH
        response.setFinalAnnualTax(savedEmployeeTds.getFinalAnnualTax());
        response.setTaxRegime(savedEmployeeTds.getTaxRegime());

        // Optional: UI transparency
        response.setTaxCalculation(taxCalculationResult);

        log.info(
                "[{}] ✅ END | POI considered | employee_tds_id={}",
                method,
                savedEmployeeTds.getId()
        );

        return response;
    }

    // Helper to check if employee can resubmit (after rejection)
    private boolean canEmployeeResubmit(EmployeeProofOfInvestment poi) {
        // Employee can resubmit if:
        // 1. POI is in DRAFT status
        // 2. POI was previously submitted (has submittedBy)
        // 3. POI was approved/rejected (has approvedBy)
        return poi.getStatus() == PayRunStatus.DRAFT &&
                poi.getSubmittedBy() != null &&
                poi.getApprovedBy() != null;
    }

}
