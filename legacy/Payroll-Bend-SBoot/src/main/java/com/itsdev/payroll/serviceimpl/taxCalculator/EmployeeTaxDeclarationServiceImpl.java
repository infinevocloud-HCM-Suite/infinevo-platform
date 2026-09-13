// package com.itsdev.payroll.serviceimpl.taxCalculator;

// import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
// import com.itsdev.payroll.dto.taxCalculator.DeclarationSubmissionRequest;
// import com.itsdev.payroll.dto.taxCalculator.EmployeeTaxDeclarationDTO;
// import com.itsdev.payroll.dto.taxCalculator.TaxDocumentDTO;
// import com.itsdev.payroll.entity.employee.BasicDetails;
// import com.itsdev.payroll.entity.taxCalculator.EmployeeTaxDeclaration;
// import com.itsdev.payroll.entity.taxCalculator.TaxDocument;
// import com.itsdev.payroll.mapper.taxCalculator.EmployeeTaxDeclarationMapper;
// import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
// import
// com.itsdev.payroll.repository.taxCalculator.EmployeeTaxDeclarationRepository;
// import com.itsdev.payroll.repository.taxCalculator.TaxDocumentRepository;
// import com.itsdev.payroll.service.CloudinaryService;
// import
// com.itsdev.payroll.service.taxCalculator.EmployeeTaxDeclarationService;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.security.core.Authentication;
// import org.springframework.security.core.context.SecurityContextHolder;
// import org.springframework.security.oauth2.jwt.Jwt;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;
// import org.springframework.web.multipart.MultipartFile;

// import java.io.IOException;
// import java.time.LocalDateTime;
// import java.util.List;

// @Service
// public class EmployeeTaxDeclarationServiceImpl implements
// EmployeeTaxDeclarationService {

// private static final Logger log =
// LoggerFactory.getLogger(EmployeeTaxDeclarationServiceImpl.class);

// private final EmployeeTaxDeclarationRepository declarationRepository;
// private final TaxDocumentRepository documentRepository;
// private final BasicDetailsRepository employeeRepository;
// private final CloudinaryService cloudinaryService;
// private final EmployeeTaxDeclarationMapper declarationMapper;

// public EmployeeTaxDeclarationServiceImpl(EmployeeTaxDeclarationRepository
// declarationRepository,
// TaxDocumentRepository documentRepository,
// BasicDetailsRepository employeeRepository,
// CloudinaryService cloudinaryService,
// EmployeeTaxDeclarationMapper declarationMapper) {
// this.declarationRepository = declarationRepository;
// this.documentRepository = documentRepository;
// this.employeeRepository = employeeRepository;
// this.cloudinaryService = cloudinaryService;
// this.declarationMapper = declarationMapper;
// }

// public EmployeeTaxDeclarationDTO getMyDeclaration(String organizationId, Long
// employeeId, Integer financialYear) {
// log.info("Fetching tax declaration for employee: {}, organization: {}, year:
// {}",
// employeeId, organizationId, financialYear);

// BasicDetails employee = employeeRepository.findById(employeeId)
// .orElseThrow(() -> new RuntimeException("Employee not found with id: " +
// employeeId));

// // Verify employee belongs to organization
// if (!employee.getOrganization().getOrganizationId().equals(organizationId)) {
// throw new RuntimeException("Employee does not belong to this organization");
// }

// EmployeeTaxDeclaration declaration = declarationRepository
// .findByEmployeeAndFinancialYear(employee, financialYear)
// .orElseGet(() -> createNewDeclaration(employee, organizationId,
// financialYear));

// return declarationMapper.toDTO(declaration);
// }

// @Override
// @Transactional
// public EmployeeTaxDeclarationDTO submitDeclaration(String organizationId,
// Long employeeId,
// DeclarationSubmissionRequest request) {
// log.info("Submitting tax declaration for employee: {}, organization: {}",
// employeeId, organizationId);

// validateDeclaration(request);

// BasicDetails employee = employeeRepository.findById(employeeId)
// .orElseThrow(() -> new RuntimeException("Employee not found"));

// EmployeeTaxDeclaration declaration = declarationRepository
// .findByEmployeeAndFinancialYear(employee, request.getFinancialYear())
// .orElse(createNewDeclaration(employee, organizationId,
// request.getFinancialYear()));

// // Update declaration with submitted data
// updateDeclarationFromRequest(declaration, request);
// declaration.setStatus(EmployeeTaxDeclaration.DeclarationStatus.SUBMITTED);
// declaration.setSubmittedDate(LocalDateTime.now());
// declaration.setModifiedDate(LocalDateTime.now());

// EmployeeTaxDeclaration saved = declarationRepository.save(declaration);
// log.info("Tax declaration submitted successfully. Declaration ID: {}",
// saved.getId());

// return declarationMapper.toDTO(saved);
// }

// @Override
// @Transactional
// public EmployeeTaxDeclarationDTO saveAsDraft(String organizationId, Long
// employeeId,
// DeclarationSubmissionRequest request) {
// log.info("Saving tax declaration as draft for employee: {}, organization:
// {}", employeeId, organizationId);

// BasicDetails employee = employeeRepository.findById(employeeId)
// .orElseThrow(() -> new RuntimeException("Employee not found"));

// EmployeeTaxDeclaration declaration = declarationRepository
// .findByEmployeeAndFinancialYear(employee, request.getFinancialYear())
// .orElse(createNewDeclaration(employee, organizationId,
// request.getFinancialYear()));

// // Update declaration with draft data
// updateDeclarationFromRequest(declaration, request);
// declaration.setStatus(EmployeeTaxDeclaration.DeclarationStatus.DRAFT);
// declaration.setModifiedDate(LocalDateTime.now());

// EmployeeTaxDeclaration saved = declarationRepository.save(declaration);
// log.info("Tax declaration saved as draft. Declaration ID: {}",
// saved.getId());

// return declarationMapper.toDTO(saved);
// }

// @Override
// @Transactional
// public EmployeeTaxDeclarationDTO updateDeclaration(String organizationId,
// Long declarationId,
// DeclarationSubmissionRequest request) {
// log.info("Updating tax declaration: {}, organization: {}", declarationId,
// organizationId);

// EmployeeTaxDeclaration declaration =
// declarationRepository.findById(declarationId)
// .orElseThrow(() -> new RuntimeException("Declaration not found"));

// if (!declaration.getOrganizationId().equals(organizationId)) {
// throw new RuntimeException("Declaration does not belong to this
// organization");
// }

// // Only allow updates if in DRAFT or SUBMITTED status
// if (declaration.getStatus() ==
// EmployeeTaxDeclaration.DeclarationStatus.APPROVED) {
// throw new RuntimeException("Cannot update an approved declaration");
// }

// validateDeclaration(request);
// updateDeclarationFromRequest(declaration, request);
// declaration.setModifiedDate(LocalDateTime.now());

// EmployeeTaxDeclaration saved = declarationRepository.save(declaration);
// return declarationMapper.toDTO(saved);
// }

// @Override
// @Transactional
// public String uploadDocument(MultipartFile file, String organizationId, Long
// employeeId,
// String section, String description) throws IOException {
// log.info("Uploading document for employee: {}, organization: {}, section:
// {}",
// employeeId, organizationId, section);

// BasicDetails employee = employeeRepository.findById(employeeId)
// .orElseThrow(() -> new RuntimeException("Employee not found"));

// // Verify employee belongs to organization
// if (!employee.getOrganization().getOrganizationId().equals(organizationId)) {
// throw new RuntimeException("Employee does not belong to this organization");
// }

// // Upload to Cloudinary
// String companyUserId = getCompanyUserId();
// CloudinaryUploadResponseDTO uploadResponse =
// cloudinaryService.uploadFile(file, companyUserId);

// // Create and save TaxDocument
// TaxDocument document = new TaxDocument(employee, organizationId,
// getCurrentUserId());
// document.setPublicId(uploadResponse.getPublic_id());
// document.setFileUrl(uploadResponse.getSecure_url());
// document.setFileName(file.getOriginalFilename());
// document.setFileType(getFileExtension(file.getOriginalFilename()));
// document.setContentType(file.getContentType());
// document.setFileSize(file.getSize());

// TaxDocument.DocumentSection docSection = null;
// if (section != null) {
// try {
// docSection = TaxDocument.DocumentSection.valueOf(section);
// } catch (IllegalArgumentException e) {
// try {
// docSection = TaxDocument.DocumentSection.valueOf(section.toUpperCase());
// } catch (IllegalArgumentException ex) {
// throw new RuntimeException("Invalid document section: " + section);
// }
// }
// }
// document.setSection(docSection);
// document.setDescription(description);

// TaxDocument savedDocument = documentRepository.save(document);
// log.info("Document uploaded successfully. Document ID: {}",
// savedDocument.getId());

// return savedDocument.getFileUrl();
// }

// @Override
// @Transactional
// public boolean deleteDocument(String organizationId, Long documentId) throws
// IOException {
// log.info("Deleting document: {}, organization: {}", documentId,
// organizationId);

// TaxDocument document = documentRepository.findById(documentId)
// .orElseThrow(() -> new RuntimeException("Document not found"));

// if (!document.getOrganizationId().equals(organizationId)) {
// throw new RuntimeException("Document does not belong to this organization");
// }

// // Delete from Cloudinary
// boolean cloudinaryDeleted =
// cloudinaryService.deleteFile(document.getPublicId());

// if (cloudinaryDeleted) {
// documentRepository.delete(document);
// log.info("Document deleted successfully. Document ID: {}", documentId);
// return true;
// } else {
// log.error("Failed to delete document from Cloudinary. Document ID: {}",
// documentId);
// return false;
// }
// }

// @Override
// public List<EmployeeTaxDeclarationDTO> getPendingDeclarations(String
// organizationId) {
// log.info("Fetching pending declarations for organization: {}",
// organizationId);

// List<EmployeeTaxDeclaration> declarations = declarationRepository
// .findByOrganizationIdAndStatus(organizationId,
// EmployeeTaxDeclaration.DeclarationStatus.SUBMITTED);

// return declarations.stream()
// .map(declarationMapper::toDTO)
// .toList();
// }

// @Override
// @Transactional
// public EmployeeTaxDeclarationDTO reviewDeclaration(String organizationId,
// Long declarationId,
// EmployeeTaxDeclaration.DeclarationStatus status,
// String comments, String reviewedBy) {
// log.info("Reviewing declaration: {}, organization: {}, status: {}",
// declarationId, organizationId, status);

// EmployeeTaxDeclaration declaration =
// declarationRepository.findById(declarationId)
// .orElseThrow(() -> new RuntimeException("Declaration not found"));

// if (!declaration.getOrganizationId().equals(organizationId)) {
// throw new RuntimeException("Declaration does not belong to this
// organization");
// }

// if (declaration.getStatus() !=
// EmployeeTaxDeclaration.DeclarationStatus.SUBMITTED) {
// throw new RuntimeException("Only submitted declarations can be reviewed");
// }

// declaration.setStatus(status);
// declaration.setReviewedDate(LocalDateTime.now());
// declaration.setReviewedBy(reviewedBy);

// if (status == EmployeeTaxDeclaration.DeclarationStatus.REJECTED) {
// declaration.setRejectionReason(comments);
// } else {
// declaration.setRejectionReason(null);
// }

// declaration.setModifiedDate(LocalDateTime.now());

// EmployeeTaxDeclaration saved = declarationRepository.save(declaration);
// log.info("Declaration reviewed successfully. Declaration ID: {}, Status: {}",
// saved.getId(), status);

// return declarationMapper.toDTO(saved);
// }

// @Override
// public List<EmployeeTaxDeclarationDTO> getEmployeeDeclarations(String
// organizationId, Long employeeId) {
// log.info("Fetching all declarations for employee: {}, organization: {}",
// employeeId, organizationId);

// BasicDetails employee = employeeRepository.findById(employeeId)
// .orElseThrow(() -> new RuntimeException("Employee not found"));

// List<EmployeeTaxDeclaration> declarations =
// declarationRepository.findByEmployee(employee);

// return declarations.stream()
// .map(declarationMapper::toDTO)
// .toList();
// }

// @Override
// public boolean validateDeclaration(DeclarationSubmissionRequest request) {
// // Validate section limits
// if (request.getSection80C() != null && request.getSection80C() > 150000) {
// throw new RuntimeException("Section 80C cannot exceed ₹1,50,000");
// }
// if (request.getSection80D() != null && request.getSection80D() > 25000) {
// throw new RuntimeException("Section 80D cannot exceed ₹25,000");
// }
// if (request.getSection24() != null && request.getSection24() > 200000) {
// throw new RuntimeException("Section 24 cannot exceed ₹2,00,000");
// }
// if (request.getHraRent() != null && request.getHraRent() < 0) {
// throw new RuntimeException("HRA rent cannot be negative");
// }

// return true;
// }

// // ===== PRIVATE HELPER METHODS =====

// private EmployeeTaxDeclaration createNewDeclaration(BasicDetails employee,
// String organizationId,
// Integer financialYear) {
// EmployeeTaxDeclaration declaration = new EmployeeTaxDeclaration(employee,
// organizationId, financialYear);
// declaration.setStatus(EmployeeTaxDeclaration.DeclarationStatus.DRAFT);
// declaration.setTaxRegime(EmployeeTaxDeclaration.TaxRegime.NEW); // ← MUST SET
// THIS
// declaration.setSection80C(0.0);
// declaration.setSection80D(0.0);
// declaration.setHraRent(0.0);
// declaration.setOtherExemptions(0.0);
// return declarationRepository.save(declaration);
// }

// private void updateDeclarationFromRequest(EmployeeTaxDeclaration declaration,
// DeclarationSubmissionRequest request) {
// declaration.setTaxRegime(request.getTaxRegime());
// declaration.setSection80C(request.getSection80C());
// declaration.setSection80D(request.getSection80D());
// declaration.setSection80E(request.getSection80E());
// declaration.setSection24(request.getSection24());
// declaration.setHraRent(request.getHraRent());
// declaration.setOtherExemptions(request.getOtherExemptions());
// }

// private String getFileExtension(String fileName) {
// if (fileName == null || !fileName.contains(".")) {
// return "unknown";
// }
// return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
// }

// private String getCompanyUserId() {
// Authentication authentication =
// SecurityContextHolder.getContext().getAuthentication();
// if (authentication == null || !(authentication.getPrincipal() instanceof
// Jwt)) {
// throw new RuntimeException("Invalid authentication context");
// }
// Jwt jwt = (Jwt) authentication.getPrincipal();
// return jwt.getClaimAsString("companyUserId");
// }

// private String getCurrentUserId() {
// Authentication authentication =
// SecurityContextHolder.getContext().getAuthentication();
// if (authentication == null || !(authentication.getPrincipal() instanceof
// Jwt)) {
// throw new RuntimeException("Invalid authentication context");
// }
// Jwt jwt = (Jwt) authentication.getPrincipal();
// return jwt.getClaimAsString("sub"); // or whatever field contains user ID
// }
// }