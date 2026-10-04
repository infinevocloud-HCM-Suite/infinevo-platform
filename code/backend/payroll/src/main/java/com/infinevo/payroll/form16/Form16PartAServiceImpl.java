package com.infinevo.payroll.form16;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.employee.detail.PanLookup;
import com.infinevo.payroll.form16.PartAEntryParser.Outcome;
import com.infinevo.payroll.form16.PartAEntryParser.ParsedEntry;
import com.infinevo.payroll.form16.exception.PartANotFoundException;
import com.infinevo.payroll.form16.exception.PartATooLargeException;
import com.infinevo.payroll.form16.exception.PartAZipException;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Every rule of the Part A upload and its reads (W-36.5 §3) — {@code docs/CONVENTIONS.md} section 3.
 *
 * <p><strong>The ZIP is never trusted.</strong> Entries are streamed one by one to a temp directory of
 * this call's own, each under a name this class chooses; the entry name is only ever parsed
 * ({@link PartAEntryParser}). Reading stops at {@link #MAX_ENTRIES} entries or {@link #MAX_UNPACKED_BYTES}
 * unpacked (lowerable only by tests), counted from the bytes actually inflated rather than the sizes the archive declares — the
 * zip-bomb guard (§9). The directory is deleted in {@code finally}, whatever happened.
 *
 * <p><strong>One transaction per certificate.</strong> {@code upload} itself is not transactional: one
 * file the store refuses is reported as skipped and does not undo the certificates filed before it.
 */
@Service
public class Form16PartAServiceImpl implements Form16PartAService {

    private static final Logger log = LoggerFactory.getLogger(Form16PartAServiceImpl.class);

    /** 50 MB — the upload limit (§2), the same as the document store's system limit. */
    static final long MAX_ZIP_BYTES = 50L * 1024 * 1024;

    /** At most this many entries, of any kind (§3). */
    static final int MAX_ENTRIES = 2_000;

    /** At most this many bytes unpacked across every entry (§3). */
    static final long MAX_UNPACKED_BYTES = 200L * 1024 * 1024;

    private static final int CHUNK = 8192;
    private static final String TEMP_PREFIX = "form16-part-a-";

    private final Form16PartARepository repository;
    private final DocumentService documentService;
    private final DocumentLinkService documentLinkService;
    private final EmployeeIdentificationService identificationService;
    private final EmployeeService employeeService;
    private final TransactionTemplate entryTransaction;
    private final Path workRoot;
    private final Clock clock;
    private final long maxUnpackedBytes;

    @Autowired
    public Form16PartAServiceImpl(
            Form16PartARepository repository,
            DocumentService documentService,
            DocumentLinkService documentLinkService,
            EmployeeIdentificationService identificationService,
            EmployeeService employeeService,
            PlatformTransactionManager transactionManager,
            @Value("${payroll.form16.part-a.work-dir:}") String workDir) {
        this(
                repository,
                documentService,
                documentLinkService,
                identificationService,
                employeeService,
                transactionManager,
                workDir,
                Clock.systemUTC(),
                MAX_UNPACKED_BYTES);
    }

    /** {@code maxUnpackedBytes} is {@link #MAX_UNPACKED_BYTES} in production; tests lower it (§9). */
    Form16PartAServiceImpl(
            Form16PartARepository repository,
            DocumentService documentService,
            DocumentLinkService documentLinkService,
            EmployeeIdentificationService identificationService,
            EmployeeService employeeService,
            PlatformTransactionManager transactionManager,
            String workDir,
            Clock clock,
            long maxUnpackedBytes) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.documentLinkService = Objects.requireNonNull(documentLinkService, "documentLinkService must not be null");
        this.identificationService =
                Objects.requireNonNull(identificationService, "identificationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.entryTransaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.workRoot = workDir == null || workDir.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"))
                : Path.of(workDir.strip());
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (maxUnpackedBytes <= 0) {
            throw new IllegalArgumentException("maxUnpackedBytes must be positive");
        }
        this.maxUnpackedBytes = maxUnpackedBytes;
    }

    @Override
    public PartAUploadResult upload(String financialYear, MultipartFile file) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        if (file == null || file.isEmpty()) {
            throw PartAZipException.invalid();
        }
        if (file.getSize() > MAX_ZIP_BYTES) {
            throw new PartATooLargeException(MAX_ZIP_BYTES);
        }
        UUID tenantId = TenantContext.require();

        Path work = createWorkDir();
        try {
            List<Extracted> entries = extract(file, work);
            return file(tenantId, fy, entries);
        } finally {
            deleteRecursively(work);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartARow> list(String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        return repository.findActiveByTenantIdAndFinancialYear(tenantId, fy.label()).stream()
                .map(PartARow::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PartANotFoundException.class)
    public PartAOwn own(String financialYear) {
        FinancialYear fy = FinancialYear.parse(financialYear);
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PartANotFoundException("No employee record is linked to this login"));
        Form16PartA row = repository
                .findActive(tenantId, employee.id(), fy.label())
                .orElseThrow(() -> new PartANotFoundException("No Form 16 Part A on file for " + fy.label()));
        DocumentLinkService.SignedLink link;
        try {
            link = documentLinkService.signedLink(row.getDocumentId());
        } catch (DocumentService.NotFoundException e) {
            throw new PartANotFoundException("No Form 16 Part A on file for " + fy.label());
        }
        return new PartAOwn(row.getDocumentId(), link.url(), link.expiresAt());
    }

    /** A parsed entry and, for a candidate, the temp file its bytes were written to. */
    private record Extracted(ParsedEntry entry, Path file) {}

    /** Streams the ZIP's entries, writing only candidates to disk. */
    private List<Extracted> extract(MultipartFile file, Path work) {
        List<ParsedEntry> parsed = new ArrayList<>();
        Map<Integer, Path> files = new HashMap<>();
        try (InputStream raw = new BufferedInputStream(file.getInputStream())) {
            if (!startsLikeZip(raw)) {
                throw PartAZipException.invalid();
            }
            try (ZipInputStream zip = new ZipInputStream(raw)) {
                int count = 0;
                long unpacked = 0;
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    count++;
                    if (count > MAX_ENTRIES) {
                        throw PartAZipException.tooManyEntries(MAX_ENTRIES);
                    }
                    long remaining = maxUnpackedBytes - unpacked;
                    if (entry.isDirectory()) {
                        unpacked += copyBounded(zip, OutputStream.nullOutputStream(), remaining);
                        continue;
                    }
                    ParsedEntry entryParsed = PartAEntryParser.parse(entry.getName());
                    if (entryParsed.outcome() == Outcome.CANDIDATE) {
                        Path target = work.resolve(parsed.size() + ".pdf");
                        try (OutputStream out = Files.newOutputStream(target)) {
                            unpacked += copyBounded(zip, out, remaining);
                        }
                        files.put(parsed.size(), target);
                    } else {
                        unpacked += copyBounded(zip, OutputStream.nullOutputStream(), remaining);
                    }
                    parsed.add(entryParsed);
                }
            }
        } catch (ZipException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("encrypt")) {
                throw PartAZipException.encrypted();
            }
            throw PartAZipException.invalid();
        } catch (IOException e) {
            throw PartAZipException.invalid();
        }

        List<ParsedEntry> resolved = PartAEntryParser.markDuplicates(parsed);
        List<Extracted> result = new ArrayList<>(resolved.size());
        for (int i = 0; i < resolved.size(); i++) {
            result.add(new Extracted(resolved.get(i), files.get(i)));
        }
        return result;
    }

    /** Files each candidate whose PAN one live employee holds; everything else is reported. */
    private PartAUploadResult file(UUID tenantId, FinancialYear fy, List<Extracted> entries) {
        Set<String> pans = new HashSet<>();
        for (Extracted extracted : entries) {
            if (extracted.entry().outcome() == Outcome.CANDIDATE) {
                pans.add(extracted.entry().pan());
            }
        }
        // One query for the whole upload (DEBT-019). A PAN on two live employees comes back ambiguous.
        PanLookup lookup = pans.isEmpty() ? PanLookup.EMPTY : identificationService.lookupByPan(pans);
        Map<String, UUID> employees = lookup.unique();

        int matched = 0;
        List<String> unmatched = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String storedName = "Form16-PartA-" + fy.label() + ".pdf";
        String actor = currentActor();

        for (Extracted extracted : entries) {
            ParsedEntry entry = extracted.entry();
            if (entry.outcome().isSkipped()) {
                skipped.add(entry.name());
                continue;
            }
            if (entry.outcome() == Outcome.CANDIDATE && lookup.ambiguous().contains(entry.pan())) {
                // §9: a certificate is never filed against a guess between two employees.
                log.warn("Skipped a Part A entry in tenant {}: PAN held by more than one employee", tenantId);
                skipped.add(entry.name());
                continue;
            }
            UUID employeeId = entry.outcome() == Outcome.CANDIDATE ? employees.get(entry.pan()) : null;
            if (employeeId == null) {
                unmatched.add(entry.name());
                continue;
            }
            try {
                entryTransaction.executeWithoutResult(status ->
                        fileOne(tenantId, fy.label(), employeeId, storedName, entry.name(), extracted.file(), actor));
                matched++;
            } catch (DocumentService.ValidationException
                    | DocumentService.UnsupportedTypeException
                    | DocumentService.TooLargeException
                    | DataIntegrityViolationException e) {
                log.warn(
                        "Skipped a Part A entry for employee {} in tenant {}: {}",
                        employeeId,
                        tenantId,
                        e.getClass().getSimpleName());
                skipped.add(entry.name());
            }
        }
        log.info(
                "Form 16 Part A upload for {} in tenant {}: {} matched, {} unmatched, {} skipped",
                fy.label(),
                tenantId,
                matched,
                unmatched.size(),
                skipped.size());
        return new PartAUploadResult(matched, unmatched, skipped);
    }

    /**
     * Stores the PDF, then supersedes the active row and soft-deletes its document, then inserts the new
     * row. The active row is locked first, so two uploads for one employee supersede one after the other.
     */
    private void fileOne(
            UUID tenantId, String fy, UUID employeeId, String storedName, String sourceName, Path file, String actor) {
        Optional<Form16PartA> active = repository.findActiveForUpdate(tenantId, employeeId, fy);
        UUID documentId = documentService.storeFile(DocumentKind.FORM16_PART_A, employeeId, storedName, file);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (active.isPresent()) {
            Form16PartA old = active.get();
            old.supersede(now, actor);
            // Flushed before the insert, so the unique partial index sees one active row.
            repository.saveAndFlush(old);
            try {
                documentService.delete(old.getDocumentId());
            } catch (DocumentService.NotFoundException e) {
                // Already gone: nothing left to hide.
            }
        }
        repository.saveAndFlush(new Form16PartA(tenantId, employeeId, fy, documentId, sourceName, actor, now));
    }

    /** {@code PK\3\4} (an entry) or {@code PK\5\6} (an empty archive). The stream is reset. */
    private static boolean startsLikeZip(InputStream in) throws IOException {
        in.mark(4);
        byte[] head = in.readNBytes(4);
        in.reset();
        return head.length == 4
                && head[0] == 'P'
                && head[1] == 'K'
                && ((head[2] == 3 && head[3] == 4) || (head[2] == 5 && head[3] == 6));
    }

    /** Copies the current entry, refusing as soon as it passes {@code limit} bytes. */
    private long copyBounded(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] chunk = new byte[CHUNK];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > limit) {
                throw PartAZipException.tooLargeUnpacked(maxUnpackedBytes);
            }
            out.write(chunk, 0, read);
        }
        return total;
    }

    private Path createWorkDir() {
        try {
            Files.createDirectories(workRoot);
            return Files.createTempDirectory(workRoot, TEMP_PREFIX);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a working directory for the Part A upload", e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete Part A temp file {}", path.getFileName());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean the Part A working directory {}", dir.getFileName());
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return "system";
        }
        return auth.getName();
    }
}
