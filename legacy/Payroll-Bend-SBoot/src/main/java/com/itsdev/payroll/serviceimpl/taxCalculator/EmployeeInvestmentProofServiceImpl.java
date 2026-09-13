package com.itsdev.payroll.serviceimpl.taxCalculator;


import com.itsdev.payroll.dto.CloudinaryUploadResponseDTO;
import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentDocumentDTO;
import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentProofRequest;
import com.itsdev.payroll.dto.taxCalculator.EmployeeInvestmentProofResponse;
import com.itsdev.payroll.dto.taxCalculator.POIDocumentDTO;
import com.itsdev.payroll.dto.taxCalculator.POISubmissionRequest;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.taxCalculator.EmployeeInvestmentProof;
import com.itsdev.payroll.entity.taxCalculator.EmployeeInvestmentProofFile;
import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.taxCalculator.EmployeeInvestmentProofRepository;
import com.itsdev.payroll.repository.taxCalculator.ProofOfInvestmentDocumentRepository;
import com.itsdev.payroll.service.CloudinaryService;
import com.itsdev.payroll.service.taxCalculator.EmployeeInvestmentProofService;
import com.itsdev.payroll.service.taxCalculator.POIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeInvestmentProofServiceImpl implements EmployeeInvestmentProofService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeInvestmentProofServiceImpl.class);

    private final EmployeeInvestmentProofRepository proofRepo;
    private final BasicDetailsRepository employeeRepo;
    private final CloudinaryService cloudinaryService;

    public EmployeeInvestmentProofServiceImpl(
            EmployeeInvestmentProofRepository proofRepo,
            BasicDetailsRepository employeeRepo,
            CloudinaryService cloudinaryService) {

        this.proofRepo = proofRepo;
        this.employeeRepo = employeeRepo;
        this.cloudinaryService = cloudinaryService;
    }

    @Transactional
    @Override
    public void submitInvestmentProof(EmployeeInvestmentProofRequest request, String organizationId) throws IOException {

        String method = "submitInvestmentProof";

        log.info("[{}] ➡️ Starting investment proof submission | orgId={}, employeeId={}, financialYear={}",
                method, organizationId, request.getEmployeeId(), request.getFinancialYear());

        // 1️⃣ Fetch employee
        BasicDetails employee = employeeRepo
                .findByOrganization_OrganizationIdAndEmployeeId(
                        organizationId,
                        request.getEmployeeId()
                )
                .orElseThrow(() -> {
                    log.error("[{}] ❌ Employee not found | orgId={}, employeeId={}", 
                            method, organizationId, request.getEmployeeId());
                    return new RuntimeException("Employee not found");
                });

        log.info("[{}] ✔ Employee found | employeeName={} {}, employeeNumber={}",
                method, employee.getFirstName(), employee.getLastName(), employee.getEmployeeNumber());

        // 2️⃣ Create master proof record
        EmployeeInvestmentProof proof = new EmployeeInvestmentProof();
        proof.setEmployee(employee);
        proof.setOrganizationId(organizationId);
        proof.setFinancialYear(request.getFinancialYear());
        proof.setStatus(request.getStatus());

        log.info("[{}] 📄 Creating EmployeeInvestmentProof | employeeId={}", 
                method, request.getEmployeeId());

        // 3️⃣ Process each document
        for (EmployeeInvestmentDocumentDTO doc : request.getDocuments()) {

            String originalName = doc.getFile().getOriginalFilename();

            log.info("[{}] ⬆ Uploading file | fileName='{}', docType={}, itemName={}, employeeId={}, orgId={}",
                    method, originalName, doc.getDocumentType(), doc.getDeclaredItemName(),
                    request.getEmployeeId(), organizationId);

            // Upload to Cloudinary
            CloudinaryUploadResponseDTO uploadRes = cloudinaryService.uploadEmployeeInvestmentFile(
                    doc.getFile(),
                    organizationId,
                    employee.getEmployeeNumber(),
                    request.getFinancialYear()
            );

            // Create child object
            EmployeeInvestmentProofFile file = new EmployeeInvestmentProofFile();
            file.setProof(proof);
            file.setDeclaredItemName(doc.getDeclaredItemName());
            file.setDocumentType(doc.getDocumentType());
            file.setFileName(originalName);
            file.setContentType(doc.getFile().getContentType());
            file.setFileSize(doc.getFile().getSize());
            file.setFileUrl(uploadRes.getSecure_url());
            file.setPublicId(uploadRes.getPublic_id());

            proof.getFiles().add(file);

            log.info("[{}] 📌 File added to proof | fileName='{}', employeeId={}", 
                    method, originalName, request.getEmployeeId());
        }

        // 4️⃣ Save parent (children saved automatically)
        proofRepo.save(proof);

        log.info("[{}] ✅ Investment proof saved successfully | proofId={}, employeeNumber={}",
                method, proof.getId(), employee.getEmployeeNumber());
    }
    
    @Override
    public EmployeeInvestmentProofResponse getProofByEmployeeId(
            String employeeId,
            String organizationId,
            Integer financialYear
    ) {
        String method = "getProofByEmployeeId";

        log.info("[{}] 🔍 Started | orgId={}, employeeId={}, financialYear={}",
                method, organizationId, employeeId, financialYear);

        Optional<EmployeeInvestmentProof> optionalProof =
                proofRepo.findByOrganizationIdAndEmployee_EmployeeIdAndFinancialYear(
                        organizationId, employeeId, financialYear
                );

        if (optionalProof.isEmpty()) {
            log.warn("[{}] ⚠ Proof NOT found | orgId={}, employeeId={}, financialYear={}",
                    method, organizationId, employeeId, financialYear);

            // return null instead of throwing
            return null;
        }

        EmployeeInvestmentProof proof = optionalProof.get();

        log.info("[{}] ✔ Proof found | proofId={}, employeeId={}, year={}",
                method, proof.getId(), employeeId, financialYear);

        log.info("[{}] 🔄 Mapping entity to response DTO | proofId={}", method, proof.getId());

        EmployeeInvestmentProofResponse res = new EmployeeInvestmentProofResponse();
        res.setProofId(proof.getId());
        res.setEmployeeId(employeeId);
        res.setFinancialYear(proof.getFinancialYear());
        res.setStatus(proof.getStatus());
        res.setCreatedDate(proof.getCreatedDate());
        res.setModifiedDate(proof.getModifiedDate());

        log.info("[{}] 🗂 Mapping {} attached documents", method, proof.getFiles().size());

        List<EmployeeInvestmentProofResponse.InvestmentDocumentResponse> documents =
                proof.getFiles().stream().map(f -> {
                    log.info("[{}] 📄 Processing file | fileName={}, type={}",
                            method, f.getFileName(), f.getDocumentType());

                    EmployeeInvestmentProofResponse.InvestmentDocumentResponse d =
                            new EmployeeInvestmentProofResponse.InvestmentDocumentResponse();
                    d.setDeclaredItemName(f.getDeclaredItemName());
                    d.setDocumentType(f.getDocumentType());
                    d.setFileName(f.getFileName());
                    d.setFileUrl(f.getFileUrl());
                    d.setContentType(f.getContentType());
                    d.setFileSize(f.getFileSize());
                    return d;
                }).toList();

        res.setDocuments(documents);

        log.info("[{}] ✅ Completed | proofId={}, totalDocuments={}",
                method, proof.getId(), documents.size());

        return res;
    }



}

