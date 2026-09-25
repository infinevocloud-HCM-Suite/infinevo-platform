package com.infinevo.core.document;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule about a stored file (W-21, spec sections 4 and 6) — {@code docs/CONVENTIONS.md} section 3.
 *
 * <p><strong>The order is blob, then row</strong> (spec section 4). A failed upload leaves no row
 * pointing at nothing. A row that fails after its blob was written leaves an unreferenced blob behind,
 * readable only by the platform identity — the cheaper of the two failures, and one a retention sweep
 * can find.
 *
 * <p><strong>The employee is looked up in the bound tenant.</strong> The foreign key on
 * {@code employee_id} is checked by PostgreSQL as the table owner, with row security off, so the
 * database would accept a document in tenant A pointing at an employee in tenant B — the gap
 * {@code EmployeeServiceImpl.assign} closes for the org masters, closed here the same way.
 *
 * <p><strong>Two limits, by kind (decision D2).</strong> A file a client uploads is capped at 10 MB
 * (contracts section 5 row 16) and read into memory, bounded — one byte over and nothing is written. A
 * file the platform generates, an export or a payslip, is capped higher and arrives through
 * {@link #storeFile}, which streams it from disk. The frozen system never stored a generated file at
 * all — every export went straight down the HTTP response
 * ({@code legacy/Payroll-Bend-SBoot/.../controller/payruns/PayRunReportController.java:29,77}) — and
 * its upload limits never ran: no multipart size is configured anywhere, so Spring's 1 MB default
 * refused every file before its own 5 MB and 10 MB checks
 * ({@code SalaryDeductionController.java:83}, {@code EmployeeProofOfInvestmentController.java:296}).
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    /** 10 MB — anything a client uploads. Contracts section 5 row 16. */
    static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;

    /** 50 MB — anything the platform generates (decision D2). An export at D-19 scale is far smaller. */
    static final long DEFAULT_SYSTEM_MAX_BYTES = 50L * 1024 * 1024;

    static final int MAX_FILE_NAME = 255;
    private static final int MAX_ACTOR = 100;
    private static final int READ_CHUNK = 8192;

    /** How much of a file the type check reads — enough for every signature and the csv sniff. */
    private static final int HEAD_BYTES = 8192;

    /** The path segment used in place of an employee id for a tenant-level document. */
    static final String TENANT_SEGMENT = "tenant";

    private final DocumentRepository documents;
    private final EmployeeRepository employees;
    private final BlobStorage blobStorage;
    private final String container;
    private final long uploadMaxBytes;
    private final long systemMaxBytes;
    private final Set<DocumentType> allowedTypes;

    /**
     * {@code allowed-types} arrives as the comma-separated string written in {@code application.yml} and
     * is split here, rather than bound to a {@code List}: turning a string into a list is a conversion
     * that depends on which conversion service the context carries, and without one it produces a
     * single entry holding the whole string — which then fails as an unknown type.
     */
    @Autowired
    public DocumentServiceImpl(
            DocumentRepository documents,
            EmployeeRepository employees,
            BlobStorage blobStorage,
            @Value("${document.blob.container:documents}") String container,
            @Value("${document.upload.max-bytes:" + DEFAULT_MAX_BYTES + "}") long uploadMaxBytes,
            @Value("${document.system.max-bytes:" + DEFAULT_SYSTEM_MAX_BYTES + "}") long systemMaxBytes,
            @Value("${document.upload.allowed-types:pdf,jpg,png,xlsx,csv}") String allowedTypes) {
        this(documents, employees, blobStorage, container, uploadMaxBytes, systemMaxBytes, splitTypes(allowedTypes));
    }

    /** For tests: the system limit at its default. */
    DocumentServiceImpl(
            DocumentRepository documents,
            EmployeeRepository employees,
            BlobStorage blobStorage,
            String container,
            long uploadMaxBytes,
            String allowedTypes) {
        this(
                documents,
                employees,
                blobStorage,
                container,
                uploadMaxBytes,
                DEFAULT_SYSTEM_MAX_BYTES,
                splitTypes(allowedTypes));
    }

    /** For tests: the system limit at its default. */
    DocumentServiceImpl(
            DocumentRepository documents,
            EmployeeRepository employees,
            BlobStorage blobStorage,
            String container,
            long uploadMaxBytes,
            List<String> allowedTypes) {
        this(documents, employees, blobStorage, container, uploadMaxBytes, DEFAULT_SYSTEM_MAX_BYTES, allowedTypes);
    }

    DocumentServiceImpl(
            DocumentRepository documents,
            EmployeeRepository employees,
            BlobStorage blobStorage,
            String container,
            long uploadMaxBytes,
            long systemMaxBytes,
            List<String> allowedTypes) {
        this.documents = Objects.requireNonNull(documents, "documents must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.blobStorage = Objects.requireNonNull(blobStorage, "blobStorage must not be null");
        if (container == null || container.isBlank()) {
            throw new IllegalStateException("document.blob.container must name a container");
        }
        if (uploadMaxBytes <= 0 || systemMaxBytes <= 0) {
            throw new IllegalStateException("document.upload.max-bytes and document.system.max-bytes must be"
                    + " positive, got " + uploadMaxBytes + " and " + systemMaxBytes);
        }
        this.container = container.trim();
        this.uploadMaxBytes = uploadMaxBytes;
        this.systemMaxBytes = systemMaxBytes;
        this.allowedTypes = parseTypes(allowedTypes);
    }

    @Override
    @Transactional
    public UUID store(DocumentKind kind, UUID employeeId, String fileName, InputStream content) {
        Accepted accepted = accept(kind, employeeId, fileName, content != null);

        byte[] bytes = readBounded(content, limitFor(kind));
        checkContent(accepted, bytes.length, bytes);

        UUID id = UUID.randomUUID();
        String path = blobPath(accepted.tenantId(), employeeId, kind, id);
        blobStorage.upload(container, path, bytes, accepted.type().contentType());
        return persist(id, accepted, employeeId, kind, bytes.length, path, sha256Hex(bytes));
    }

    @Override
    @Transactional
    public UUID storeFile(DocumentKind kind, UUID employeeId, String fileName, Path file) {
        Accepted accepted = accept(kind, employeeId, fileName, file != null);

        long size;
        byte[] head;
        try {
            size = Files.size(file);
            if (size > limitFor(kind)) {
                throw new TooLargeException(limitFor(kind));
            }
            head = readHead(file);
        } catch (IOException e) {
            throw new ValidationException(Map.of("file", "The file could not be read"));
        }
        checkContent(accepted, size, head);

        UUID id = UUID.randomUUID();
        String path = blobPath(accepted.tenantId(), employeeId, kind, id);
        String checksum = sha256Hex(file);
        blobStorage.upload(container, path, file, accepted.type().contentType());
        return persist(id, accepted, employeeId, kind, size, path, checksum);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id) {
        return DocumentResponse.from(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContent open(UUID id) {
        Document document = require(id);
        InputStream content = blobStorage.open(document.getBlobContainer(), document.getBlobPath());
        return new DocumentContent(DocumentResponse.from(document), content);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Document document = require(id);
        document.markDeleted(currentActor());
        documents.save(document);
        log.info("Soft-deleted document {} in tenant {}", id, document.getTenantId());
    }

    /** What {@link #accept} settled: the bound tenant, the cleaned name, and the type it names. */
    private record Accepted(UUID tenantId, String name, DocumentType type) {}

    /**
     * Every check that needs no bytes: a tenant is bound, the request is whole, the name is one of the
     * accepted types, and the employee — if named — is live in the bound tenant. In that order, so a
     * request that is refused never has its content read.
     */
    private Accepted accept(DocumentKind kind, UUID employeeId, String fileName, boolean hasContent) {
        UUID tenantId = TenantContext.require();

        Map<String, String> errors = new LinkedHashMap<>();
        if (kind == null) {
            errors.put("kind", "kind is required");
        }
        String name = cleanFileName(fileName, errors);
        if (!hasContent) {
            errors.put("file", "file is required");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        DocumentType type = DocumentType.ofFileName(name)
                .filter(allowedTypes::contains)
                .orElseThrow(() -> new UnsupportedTypeException(
                        "Only pdf, jpg, png, xlsx and csv files are accepted; " + name + " is none of them"));

        if (employeeId != null
                && employees
                        .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                        .isEmpty()) {
            // The same sentence whether the employee does not exist or belongs to another tenant, so
            // the answer cannot be used to enumerate another tenant's ids.
            throw new ValidationException(Map.of("employeeId", "No employee " + employeeId + " in this tenant"));
        }
        return new Accepted(tenantId, name, type);
    }

    /** Refuses an empty file, and one whose first bytes are not what its name says. */
    private static void checkContent(Accepted accepted, long size, byte[] head) {
        if (size == 0) {
            throw new ValidationException(Map.of("file", "file is empty"));
        }
        if (!accepted.type().matches(head)) {
            throw new UnsupportedTypeException("The content of " + accepted.name() + " is not a "
                    + accepted.type().name().toLowerCase(Locale.ROOT) + " file");
        }
    }

    private UUID persist(
            UUID id, Accepted accepted, UUID employeeId, DocumentKind kind, long size, String path, String checksum) {
        documents.saveAndFlush(new Document(
                id,
                accepted.tenantId(),
                employeeId,
                kind,
                accepted.name(),
                accepted.type().contentType(),
                size,
                container,
                path,
                checksum,
                currentActor()));
        log.info("Stored document {} ({}, {} bytes) in tenant {}", id, kind, size, accepted.tenantId());
        return id;
    }

    /** 10 MB for what a client can upload, the higher system limit for what the platform writes. */
    long limitFor(DocumentKind kind) {
        return kind.isUploadable() ? uploadMaxBytes : systemMaxBytes;
    }

    /** The live document with this id in the bound tenant, or {@link NotFoundException}. */
    private Document require(UUID id) {
        UUID tenantId = TenantContext.require();
        return documents.findByIdAndTenantIdAndDeletedFalse(id, tenantId).orElseThrow(() -> new NotFoundException(id));
    }

    /**
     * {@code {tenantId}/{employeeId}/{kind}/{documentId}}, or {@code {tenantId}/tenant/{kind}/{documentId}}
     * with no employee — tenant first, and an export lands under the tenant, never under a null (spec
     * section 6).
     */
    static String blobPath(UUID tenantId, UUID employeeId, DocumentKind kind, UUID documentId) {
        String owner = employeeId == null ? TENANT_SEGMENT : employeeId.toString();
        return tenantId + "/" + owner + "/" + kind.name() + "/" + documentId;
    }

    /**
     * The last path segment of the name, trimmed. Browsers and multipart clients sometimes send a full
     * path ({@code C:\fakepath\payslip.pdf}); only the file name is the user's, and a path separator in
     * a stored name is how a later download header goes wrong.
     */
    static String cleanFileName(String fileName, Map<String, String> errors) {
        if (fileName == null) {
            errors.put("fileName", "fileName is required");
            return null;
        }
        String name = fileName;
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        name = name.trim();
        if (name.isEmpty()) {
            errors.put("fileName", "fileName is required");
            return null;
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            errors.put("fileName", "fileName must not contain control characters");
            return null;
        }
        if (name.length() > MAX_FILE_NAME) {
            errors.put("fileName", "fileName must be at most " + MAX_FILE_NAME + " characters");
            return null;
        }
        return name;
    }

    /** Reads the whole stream, refusing as soon as it passes {@code limit} bytes. */
    static byte[] readBounded(InputStream content, long limit) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_CHUNK];
        long total = 0;
        try {
            int read;
            while ((read = content.read(chunk)) != -1) {
                total += read;
                if (total > limit) {
                    throw new TooLargeException(limit);
                }
                out.write(chunk, 0, read);
            }
        } catch (IOException e) {
            throw new ValidationException(Map.of("file", "The file could not be read"));
        }
        return out.toByteArray();
    }

    private static byte[] readHead(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEAD_BYTES);
        }
    }

    static String sha256Hex(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    /** The checksum of a file, read in chunks — the file is never held whole. */
    static String sha256Hex(Path file) {
        MessageDigest digest = sha256();
        byte[] chunk = new byte[READ_CHUNK];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                digest.update(chunk, 0, read);
            }
        } catch (IOException e) {
            throw new ValidationException(Map.of("file", "The file could not be read"));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    private static List<String> splitTypes(String names) {
        if (names == null || names.isBlank()) {
            return List.of();
        }
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    private static Set<DocumentType> parseTypes(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new IllegalStateException("document.upload.allowed-types must name at least one type");
        }
        Set<DocumentType> types = EnumSet.noneOf(DocumentType.class);
        for (String name : names) {
            types.add(DocumentType.ofName(name));
        }
        return Set.copyOf(types);
    }

    /**
     * The authenticated subject, for {@code created_by} / {@code updated_by}; {@code system} when there
     * is none. Same rule as {@code EmployeeServiceImpl.currentActor}.
     */
    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return Document.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }
}
