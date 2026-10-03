package com.infinevo.payroll.form16;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.form16.exception.Form16PartANotFoundException;
import com.infinevo.payroll.form16.exception.InvalidZipException;
import com.infinevo.payroll.form16.exception.ZipEncryptedException;
import com.infinevo.payroll.form16.exception.ZipLimitExceededException;
import com.infinevo.payroll.form16.exception.ZipTooLargeException;
import com.infinevo.shared.tenant.TenantContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of Form 16 Part A service (W-36.5 §3, §4).
 */
@Service
public class Form16PartAServiceImpl implements Form16PartAService {

    private static final Logger log = LoggerFactory.getLogger(Form16PartAServiceImpl.class);

    private static final Pattern FY_PATTERN = Pattern.compile("^[0-9]{4}-[0-9]{4}$");
    private static final long MAX_ZIP_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final int MAX_ENTRIES = 2000;
    private static final long MAX_UNPACKED_BYTES = 200L * 1024 * 1024; // 200 MB

    private final DocumentService documentService;
    private final DocumentLinkService documentLinkService;
    private final EmployeeIdentificationService employeeIdentificationService;
    private final EmployeeService employeeService;
    private final Form16PartARepository form16PartARepository;
    private final PlatformTransactionManager transactionManager;

    public Form16PartAServiceImpl(
            DocumentService documentService,
            DocumentLinkService documentLinkService,
            EmployeeIdentificationService employeeIdentificationService,
            EmployeeService employeeService,
            Form16PartARepository form16PartARepository,
            PlatformTransactionManager transactionManager) {
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.documentLinkService = Objects.requireNonNull(documentLinkService, "documentLinkService must not be null");
        this.employeeIdentificationService =
                Objects.requireNonNull(employeeIdentificationService, "employeeIdentificationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.form16PartARepository =
                Objects.requireNonNull(form16PartARepository, "form16PartARepository must not be null");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager must not be null");
    }

    private record EntryOnDisk(String entryName, Path path, String pan) {}

    @Override
    public PartAUploadResult upload(String financialYear, MultipartFile file) {
        validateFinancialYear(financialYear);
        if (file == null || file.isEmpty()) {
            throw new InvalidZipException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_ZIP_BYTES) {
            throw new ZipTooLargeException("ZIP file exceeds 50 MB limit");
        }

        UUID tenantId = TenantContext.require();
        String actor = resolveActor();

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("form16-part-a-");
            int entryCount = 0;
            long totalUnpackedBytes = 0;
            List<String> skipped = new ArrayList<>();
            List<String> unmatched = new ArrayList<>();
            List<EntryOnDisk> candidateEntries = new ArrayList<>();

            try (InputStream in = file.getInputStream();
                    ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry entry;
                while (true) {
                    try {
                        entry = zis.getNextEntry();
                    } catch (ZipException ze) {
                        if (ze.getMessage() != null
                                && ze.getMessage().toLowerCase().contains("encrypt")) {
                            throw new ZipEncryptedException();
                        }
                        throw new InvalidZipException("Failed to read ZIP entry: " + ze.getMessage(), ze);
                    }
                    if (entry == null) {
                        break;
                    }
                    if (entry.isDirectory()) {
                        continue;
                    }

                    entryCount++;
                    if (entryCount > MAX_ENTRIES) {
                        throw new ZipLimitExceededException("ZIP archive exceeds 2,000 entries limit");
                    }

                    String name = entry.getName();
                    if (PartAEntryParser.isPathTraversal(name)
                            || PartAEntryParser.isSystemOrMetadata(name)
                            || !PartAEntryParser.isPdf(name)) {
                        skipped.add(name);
                        continue;
                    }

                    Optional<String> panOpt = PartAEntryParser.extractPan(name);
                    if (panOpt.isEmpty()) {
                        unmatched.add(name);
                        continue;
                    }

                    Path tempFile = tempDir.resolve("entry-" + entryCount + ".pdf");
                    try (OutputStream os = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            totalUnpackedBytes += read;
                            if (totalUnpackedBytes > MAX_UNPACKED_BYTES) {
                                throw new ZipLimitExceededException("ZIP unpacked size exceeds 200 MB limit");
                            }
                            os.write(buffer, 0, read);
                        }
                    }
                    candidateEntries.add(new EntryOnDisk(name, tempFile, panOpt.get()));
                }
            } catch (ZipEncryptedException | ZipLimitExceededException e) {
                throw e;
            } catch (ZipException ze) {
                if (ze.getMessage() != null && ze.getMessage().toLowerCase().contains("encrypt")) {
                    throw new ZipEncryptedException();
                }
                throw new InvalidZipException("Invalid ZIP file: " + ze.getMessage(), ze);
            } catch (IOException e) {
                throw new InvalidZipException("I/O error reading ZIP: " + e.getMessage(), e);
            }

            if (entryCount == 0 && skipped.isEmpty() && unmatched.isEmpty()) {
                throw new InvalidZipException("Uploaded file is not a valid ZIP archive or is empty");
            }

            // Check for duplicate PAN entries within the ZIP archive
            Map<String, List<EntryOnDisk>> byPan = new LinkedHashMap<>();
            for (EntryOnDisk e : candidateEntries) {
                byPan.computeIfAbsent(e.pan(), k -> new ArrayList<>()).add(e);
            }

            List<EntryOnDisk> uniqueEntries = new ArrayList<>();
            for (Map.Entry<String, List<EntryOnDisk>> entry : byPan.entrySet()) {
                List<EntryOnDisk> list = entry.getValue();
                if (list.size() > 1) {
                    for (EntryOnDisk dup : list) {
                        skipped.add(dup.entryName());
                    }
                } else {
                    uniqueEntries.add(list.get(0));
                }
            }

            // Batch PAN resolution: single query to core
            Set<String> uniquePans =
                    uniqueEntries.stream().map(EntryOnDisk::pan).collect(Collectors.toSet());
            Map<String, UUID> employeeMap = employeeIdentificationService.employeeIdsByPan(uniquePans);
            Set<String> duplicatePansInDb = employeeIdentificationService.duplicatePans(uniquePans);

            int matchedCount = 0;
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            for (EntryOnDisk entry : uniqueEntries) {
                String pan = entry.pan();
                if (employeeMap.containsKey(pan)) {
                    UUID employeeId = employeeMap.get(pan);
                    String storeFileName = "Form16-PartA-" + financialYear + ".pdf";

                    // Store file in document service (applies system 50 MB limit)
                    UUID documentId = documentService.storeFile(
                            DocumentKind.FORM16_PART_A, employeeId, storeFileName, entry.path());

                    // One transaction per entry: supersede active link row and insert new row
                    UUID docToSoftDelete = txTemplate.execute(status -> {
                        Optional<Form16PartA> activeOpt =
                                form16PartARepository.findByTenantIdAndEmployeeIdAndFinancialYearAndIsActiveTrue(
                                        tenantId, employeeId, financialYear);
                        UUID oldDocId = null;
                        if (activeOpt.isPresent()) {
                            Form16PartA active = activeOpt.get();
                            active.supersede(actor);
                            form16PartARepository.saveAndFlush(active);
                            oldDocId = active.getDocumentId();
                        }

                        Form16PartA newRecord = new Form16PartA(
                                tenantId, employeeId, financialYear, documentId, entry.entryName(), actor);
                        form16PartARepository.save(newRecord);
                        return oldDocId;
                    });

                    // Soft-delete old document outside repository transaction
                    if (docToSoftDelete != null) {
                        try {
                            documentService.delete(docToSoftDelete);
                        } catch (Exception e) {
                            log.warn("Failed to soft-delete superseded Form 16 Part A document {}", docToSoftDelete, e);
                        }
                    }
                    matchedCount++;
                } else if (duplicatePansInDb.contains(pan)) {
                    // PAN held by two or more live employees: skipped, not guessed
                    skipped.add(entry.entryName());
                } else {
                    // PAN not matched in this tenant: unmatched
                    unmatched.add(entry.entryName());
                }
            }

            return new PartAUploadResult(matchedCount, unmatched, skipped);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary directory for ZIP processing", e);
        } finally {
            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartAOfficerItem> list(String financialYear) {
        validateFinancialYear(financialYear);
        UUID tenantId = TenantContext.require();
        List<Form16PartA> records =
                form16PartARepository.findByTenantIdAndFinancialYearAndIsActiveTrue(tenantId, financialYear);
        return records.stream()
                .map(r -> new PartAOfficerItem(r.getEmployeeId(), r.getDocumentId(), r.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PartAEmployeeResponse own(String financialYear) {
        validateFinancialYear(financialYear);
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = employeeService
                .currentEmployee()
                .orElseThrow(
                        () -> new Form16PartANotFoundException("No employee profile found for authenticated user"));

        Form16PartA record = form16PartARepository
                .findByTenantIdAndEmployeeIdAndFinancialYearAndIsActiveTrue(tenantId, current.id(), financialYear)
                .orElseThrow(() -> new Form16PartANotFoundException("No Form 16 Part A found for employee "
                        + current.id() + " in financial year " + financialYear));

        DocumentLinkService.SignedLink signed = documentLinkService.signedLink(record.getDocumentId());
        return new PartAEmployeeResponse(record.getDocumentId(), signed.url(), signed.expiresAt());
    }

    private void validateFinancialYear(String financialYear) {
        if (financialYear == null || !FY_PATTERN.matcher(financialYear).matches()) {
            throw new IllegalArgumentException(
                    "Invalid financial year format: " + financialYear + " (expected YYYY-YYYY)");
        }
    }

    private String resolveActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "system";
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete temporary directory {}", root, e);
        }
    }
}
