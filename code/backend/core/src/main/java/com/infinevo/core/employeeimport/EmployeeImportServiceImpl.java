package com.infinevo.core.employeeimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.invitation.EmployeeInvitationRequest;
import com.infinevo.core.invitation.EmployeeInvitationResponse;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.org.DepartmentRepository;
import com.infinevo.core.org.DesignationRepository;
import com.infinevo.core.org.OrgMaster;
import com.infinevo.core.org.OrgMasterRepository;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every rule of the bulk import and the invite-all lives here (W-73.7) — {@code docs/CONVENTIONS.md} §3.
 *
 * <p>Not {@code @Transactional}: each row opens its own transaction ({@link #rowTransaction}), so the
 * employee and its invitation land together or not at all, and a failed row leaves the others in place.
 *
 * <p>A row is checked twice: once when the file is uploaded (dry run, or before queueing), and again by
 * the worker just before it is written — the tenant may have changed between the two. The second check
 * still leaves {@code EmployeeService.create} and {@code InvitationService.createEmployeeInvitation} as the
 * final word: whatever they refuse is the row's error.
 */
@Service
public class EmployeeImportServiceImpl implements EmployeeImportService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImportServiceImpl.class);

    /** Granted on every acceptance anyway (W-24.2 §6); naming it in the file adds nothing. */
    private static final String EMPLOYEE_ROLE = "employee";

    /** Never granted from inside a tenant (W-73.3 §4). */
    private static final String PLATFORM_ADMIN_ROLE = "platform-admin";

    /** Invite-all works through the list this many at a time, reporting progress after each (spec §4). */
    static final int INVITE_BATCH = 100;

    private static final int MAX_EMPLOYEE_NUMBER = 64;
    private static final int MAX_NAME = 100;
    private static final int MAX_WORK_EMAIL = 255;
    private static final int MAX_MOBILE = 32;
    private static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EmployeeService employeeService;
    private final InvitationService invitationService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final WorkLocationRepository workLocationRepository;
    private final RoleRepository roleRepository;
    private final JobService jobService;
    private final DocumentService documentService;
    private final ObjectProvider<QueueProducer> queueProducers;
    private final TransactionTemplate rowTransaction;

    /**
     * What a file is checked against is read in one read-only transaction: the tenant binding that row-level
     * security reads is transaction-local, so a read outside one sees nothing.
     */
    private final TransactionTemplate readTransaction;

    public EmployeeImportServiceImpl(
            EmployeeService employeeService,
            InvitationService invitationService,
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            WorkLocationRepository workLocationRepository,
            RoleRepository roleRepository,
            JobService jobService,
            DocumentService documentService,
            ObjectProvider<QueueProducer> queueProducers,
            PlatformTransactionManager transactionManager) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.invitationService = Objects.requireNonNull(invitationService, "invitationService must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.departmentRepository =
                Objects.requireNonNull(departmentRepository, "departmentRepository must not be null");
        this.designationRepository =
                Objects.requireNonNull(designationRepository, "designationRepository must not be null");
        this.workLocationRepository =
                Objects.requireNonNull(workLocationRepository, "workLocationRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.queueProducers = Objects.requireNonNull(queueProducers, "queueProducers must not be null");
        this.rowTransaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    // ---------------------------------------------------------------- dry run and enqueue

    @Override
    public List<ImportRowResult> dryRun(List<EmployeeImportRow> rows) {
        Checker checker = checker(rows);
        List<ImportRowResult> results = new ArrayList<>();
        for (EmployeeImportRow row : rows) {
            Checked checked = checker.check(row);
            results.add(
                    checked.error() != null
                            ? ImportRowResult.error(row.rowNumber(), row.employeeNumber(), checked.error())
                            : ImportRowResult.ok(row.rowNumber(), row.employeeNumber(), wouldDo(checked), null, null));
        }
        return results;
    }

    private Checker checker(List<EmployeeImportRow> rows) {
        UUID tenantId = TenantContext.require();
        return Objects.requireNonNull(readTransaction.execute(status -> new Checker(tenantId, rows)));
    }

    private static String wouldDo(Checked checked) {
        if (!checked.giveAccess()) {
            return "Will be created";
        }
        return checked.roleIds().isEmpty()
                ? "Will be created and invited"
                : "Will be created and invited with " + String.join(", ", checked.roleCodes());
    }

    @Override
    public String enqueueImport(String csv, List<EmployeeImportRow> rows, boolean validOnly, UUID actorUserId) {
        requireActor(actorUserId);
        List<ImportRowResult> checked = dryRun(rows);
        long errors = checked.stream().filter(ImportRowResult::isError).count();
        if (errors == checked.size()) {
            throw new EmployeeImportFileException("No row can be imported; run the dry run to see why");
        }
        if (errors > 0 && !validOnly) {
            throw new EmployeeImportFileException(
                    errors + " of " + checked.size() + " rows have errors; fix them, or import the valid rows only");
        }
        return enqueue(new EmployeeImportJob.Input(EmployeeImportJob.IMPORT, actorUserId, validOnly, csv));
    }

    @Override
    public String enqueueInviteAll(UUID actorUserId) {
        requireActor(actorUserId);
        return enqueue(new EmployeeImportJob.Input(EmployeeImportJob.INVITE_ALL, actorUserId, null, null));
    }

    @Override
    public int countWithoutAccess() {
        return invitationService.employeesWithoutAccess().size();
    }

    /** Writes the job row, then sends its id. Without a producer nothing would ever run it: refuse first. */
    private String enqueue(EmployeeImportJob.Input input) {
        UUID tenantId = TenantContext.require();
        QueueProducer producer = queueProducers.getIfAvailable();
        if (producer == null) {
            throw new ImportUnavailableException("Background jobs are not available here: no queue is configured");
        }
        String jobId = UUID.randomUUID().toString();
        jobService.createJob(jobId, tenantId, QUEUE_NAME, toJson(input));
        try {
            producer.send(QUEUE_NAME, QueueMessage.of(jobId, tenantId, QUEUE_NAME, input.kind()));
        } catch (RuntimeException e) {
            log.warn("Could not queue employee {} job {}; marking it FAILED", input.kind(), jobId, e);
            jobService.markFailed(jobId, "The job could not be queued");
            throw new ImportUnavailableException("The job could not be queued; try again");
        }
        log.info("Queued employee {} job {} in tenant {}", input.kind(), jobId, tenantId);
        return jobId;
    }

    // ---------------------------------------------------------------- history and result

    @Override
    public List<EmployeeImportJobResponse> recentJobs() {
        UUID tenantId = TenantContext.require();
        return jobService.recentJobs(tenantId, QUEUE_NAME).stream()
                .map(EmployeeImportServiceImpl::toJobResponse)
                .toList();
    }

    /**
     * A queued, running or failed job still holds its input — the file — which is never echoed back; only
     * its kind is read from it. A completed one holds the summary.
     */
    static EmployeeImportJobResponse toJobResponse(JobStatusResponseDTO job) {
        JsonNode payload = readTree(job.resultPayload());
        String kind = text(payload, "kind");
        boolean completed = job.status() == JobState.COMPLETED && payload != null && payload.has("totalCount");
        return new EmployeeImportJobResponse(
                job.jobId(),
                kind,
                job.status(),
                job.progressPercentage(),
                completed ? payload.get("totalCount").asInt() : null,
                completed ? payload.get("createdCount").asInt() : null,
                completed ? payload.get("invitedCount").asInt() : null,
                completed ? payload.get("failedCount").asInt() : null,
                completed ? uuid(text(payload, "resultDocumentId")) : null,
                job.errorMessage(),
                job.createdAt(),
                job.updatedAt());
    }

    @Override
    public DocumentService.DocumentContent resultFile(String jobId) {
        UUID tenantId = TenantContext.require();
        JobStatusResponseDTO job = jobService
                .getJobStatus(jobId, tenantId)
                .filter(j -> QUEUE_NAME.equals(j.queueName()))
                .orElseThrow(() -> new JobNotFoundException("No import job " + jobId + " in this tenant"));
        UUID documentId = toJobResponse(job).resultDocumentId();
        if (documentId == null) {
            throw new JobNotFoundException("Import job " + jobId + " has no result file");
        }
        return documentService.open(documentId);
    }

    // ---------------------------------------------------------------- the worker's side

    @Override
    public String runJob(String jobId) {
        UUID tenantId = TenantContext.require();
        JobStatusResponseDTO job = jobService
                .getJobStatus(jobId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("No job " + jobId + " in this tenant"));
        EmployeeImportJob.Input input = fromJson(job.resultPayload(), EmployeeImportJob.Input.class);
        if (input == null || input.kind() == null || input.actorUserId() == null) {
            throw new IllegalArgumentException("Job " + jobId + " holds no import input");
        }

        int[] lastPercent = {-1};
        ProgressListener progress = percent -> {
            if (percent != lastPercent[0]) {
                lastPercent[0] = percent;
                jobService.updateProgress(jobId, percent);
            }
        };

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(actorContext(input.actorUserId()));
        List<ImportRowResult> results;
        UUID resultDocumentId;
        try {
            results = switch (input.kind()) {
                case EmployeeImportJob.IMPORT ->
                    importRows(EmployeeImportParser.parse(input.csv()), input.actorUserId(), progress);
                case EmployeeImportJob.INVITE_ALL -> inviteAllWithoutAccess(input.actorUserId(), progress);
                default -> throw new IllegalArgumentException("Unknown import job kind " + input.kind());
            };
            // Still as the requester, so the result document's created_by names them too.
            resultDocumentId = storeResult(jobId, input.kind(), results);
        } finally {
            SecurityContextHolder.setContext(previous);
        }

        int failed = (int) results.stream().filter(ImportRowResult::isError).count();
        int created = (int) results.stream()
                .filter(r -> !r.isError() && EmployeeImportJob.IMPORT.equals(input.kind()))
                .count();
        int invited =
                (int) results.stream().filter(r -> r.invitationId() != null).count();
        return toJson(new EmployeeImportJob.Summary(
                input.kind(), results.size(), created, invited, failed, resultDocumentId));
    }

    /**
     * The worker has no request, so nothing names who is acting. The job's requester is put in the security
     * context for the run: {@code created_by} on each employee and the audit rows then name the person who
     * uploaded the file, as they would for a single Add Employee — not {@code system}.
     */
    private static SecurityContext actorContext(UUID actorUserId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actorUserId.toString(), null, List.of()));
        return context;
    }

    @Override
    public List<ImportRowResult> importRows(List<EmployeeImportRow> rows, UUID actorUserId, ProgressListener progress) {
        requireActor(actorUserId);
        Checker checker = checker(rows);
        List<ImportRowResult> results = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            EmployeeImportRow row = rows.get(i);
            Checked checked = checker.check(row);
            if (checked.error() != null) {
                results.add(ImportRowResult.error(row.rowNumber(), row.employeeNumber(), checked.error()));
            } else {
                results.add(importOne(row, checked, actorUserId));
            }
            progress.percent(percent(i + 1, rows.size()));
        }
        return results;
    }

    /** One row, one transaction: the employee and its invitation together, or neither. */
    private ImportRowResult importOne(EmployeeImportRow row, Checked checked, UUID actorUserId) {
        try {
            return rowTransaction.execute(status -> {
                EmployeeResponse employee = employeeService.create(checked.request());
                UUID invitationId = null;
                if (checked.giveAccess()) {
                    EmployeeInvitationResponse invitation = invitationService.createEmployeeInvitation(
                            new EmployeeInvitationRequest(employee.id(), checked.roleIds()), actorUserId);
                    invitationId = invitation.id();
                }
                return ImportRowResult.ok(
                        row.rowNumber(),
                        row.employeeNumber(),
                        invitationId != null ? "Created and invited" : "Created",
                        employee.id(),
                        invitationId);
            });
        } catch (RuntimeException e) {
            return ImportRowResult.error(row.rowNumber(), row.employeeNumber(), reason(e));
        }
    }

    @Override
    public List<ImportRowResult> inviteAllWithoutAccess(UUID actorUserId, ProgressListener progress) {
        requireActor(actorUserId);
        UUID tenantId = TenantContext.require();
        List<UUID> employeeIds = invitationService.employeesWithoutAccess();
        Map<UUID, String> numbers = new HashMap<>();
        for (int from = 0; from < employeeIds.size(); from += INVITE_BATCH) {
            List<UUID> batch = employeeIds.subList(from, Math.min(from + INVITE_BATCH, employeeIds.size()));
            employeeRepository
                    .findByTenantIdAndIdIn(tenantId, batch)
                    .forEach(e -> numbers.put(e.getId(), e.getEmployeeNumber()));
        }

        List<ImportRowResult> results = new ArrayList<>();
        for (int i = 0; i < employeeIds.size(); i++) {
            UUID employeeId = employeeIds.get(i);
            String number = numbers.get(employeeId);
            try {
                // createEmployeeInvitation is its own transaction; it refuses one with a live pending
                // invitation, so a second run over the same employees invites nobody.
                EmployeeInvitationResponse invitation = invitationService.createEmployeeInvitation(
                        new EmployeeInvitationRequest(employeeId, Set.of()), actorUserId);
                results.add(ImportRowResult.ok(i + 1, number, "Invited", employeeId, invitation.id()));
            } catch (RuntimeException e) {
                results.add(
                        new ImportRowResult(i + 1, number, ImportRowResult.Status.ERROR, reason(e), employeeId, null));
            }
            if ((i + 1) % INVITE_BATCH == 0 || i + 1 == employeeIds.size()) {
                progress.percent(percent(i + 1, employeeIds.size()));
            }
        }
        return results;
    }

    /**
     * The result CSV, one line per row, in the document store as an {@code EXPORT}. A store that fails —
     * no blob storage in this runtime — leaves the job without a file rather than failing it: the rows
     * have landed, and the counts still say how many.
     */
    UUID storeResult(String jobId, String kind, List<ImportRowResult> results) {
        String fileName = (EmployeeImportJob.INVITE_ALL.equals(kind) ? "employee-invite-all-" : "employee-import-")
                + jobId + ".csv";
        try {
            return documentService.store(
                    DocumentKind.EXPORT,
                    null,
                    fileName,
                    new ByteArrayInputStream(resultCsv(results).getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            log.error("Employee {} job {}: result file not stored: {}", kind, jobId, e.getMessage());
            return null;
        }
    }

    /** {@code row,employee_number,status,message,employee_id,invitation_id}, one line per row. */
    static String resultCsv(List<ImportRowResult> results) {
        StringBuilder csv = new StringBuilder("row,employee_number,status,message,employee_id,invitation_id\n");
        for (ImportRowResult r : results) {
            csv.append(r.row())
                    .append(',')
                    .append(cell(r.employeeNumber()))
                    .append(',')
                    .append(r.status())
                    .append(',')
                    .append(cell(r.message()))
                    .append(',')
                    .append(r.employeeId() == null ? "" : r.employeeId())
                    .append(',')
                    .append(r.invitationId() == null ? "" : r.invitationId())
                    .append('\n');
        }
        return csv.toString();
    }

    /**
     * Quoted when it holds a comma, quote or line break; prefixed with {@code '} when it starts with a
     * formula character, so a spreadsheet opening the file never runs a cell (CSV injection).
     */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    // ---------------------------------------------------------------- row checks

    /** A row's verdict: an error, or the request and invitation it makes. */
    private record Checked(
            String error, EmployeeRequest request, boolean giveAccess, Set<UUID> roleIds, List<String> roleCodes) {

        static Checked failed(List<String> errors) {
            return new Checked(String.join("; ", errors), null, false, Set.of(), List.of());
        }
    }

    /**
     * What the tenant holds, read once per file, and what the file itself repeats. Employee numbers count
     * deleted rows (the unique index does); work emails count live employees only.
     */
    private final class Checker {

        private final Set<String> takenNumbers;
        private final Set<String> takenEmails;
        private final Map<String, UUID> departments;
        private final Map<String, UUID> designations;
        private final Map<String, UUID> locations;
        private final Map<String, Role> roles = new HashMap<>();
        private final Map<String, Integer> firstRowOfNumber = new HashMap<>();
        private final Map<String, Integer> firstRowOfEmail = new HashMap<>();

        Checker(UUID tenantId, List<EmployeeImportRow> rows) {
            takenNumbers = new HashSet<>(employeeRepository.findEmployeeNumbers(tenantId));
            takenEmails = new HashSet<>(employeeRepository.findLiveWorkEmails(tenantId));
            departments = activeMasters(departmentRepository, tenantId);
            designations = activeMasters(designationRepository, tenantId);
            locations = activeMasters(workLocationRepository, tenantId);
            for (Role role : roleRepository.findByTenantIdOrderByCodeAsc(tenantId)) {
                roles.put(role.getCode().toLowerCase(Locale.ROOT), role);
            }
            for (EmployeeImportRow row : rows) {
                if (row.employeeNumber() != null) {
                    firstRowOfNumber.putIfAbsent(row.employeeNumber(), row.rowNumber());
                }
                if (row.workEmail() != null) {
                    firstRowOfEmail.putIfAbsent(row.workEmail().toLowerCase(Locale.ROOT), row.rowNumber());
                }
            }
        }

        Checked check(EmployeeImportRow row) {
            List<String> errors = new ArrayList<>();

            String number = row.employeeNumber();
            if (number == null) {
                errors.add("employee_number is required");
            } else if (number.length() > MAX_EMPLOYEE_NUMBER) {
                errors.add("employee_number is longer than " + MAX_EMPLOYEE_NUMBER + " characters");
            } else if (firstRowOfNumber.get(number) != row.rowNumber()) {
                errors.add("Employee number " + number + " repeats row " + firstRowOfNumber.get(number));
            } else if (takenNumbers.contains(number)) {
                errors.add("Employee number " + number + " is already in use in this tenant");
            }

            if (row.firstName() == null) {
                errors.add("first_name is required");
            } else if (row.firstName().length() > MAX_NAME) {
                errors.add("first_name is longer than " + MAX_NAME + " characters");
            }
            if (row.lastName() != null && row.lastName().length() > MAX_NAME) {
                errors.add("last_name is longer than " + MAX_NAME + " characters");
            }

            String email = row.workEmail();
            if (email != null) {
                String key = email.toLowerCase(Locale.ROOT);
                if (email.length() > MAX_WORK_EMAIL || !email.matches(EMAIL_PATTERN)) {
                    errors.add("work_email " + email + " is not an email address");
                } else if (firstRowOfEmail.get(key) != row.rowNumber()) {
                    errors.add("Work email " + email + " repeats row " + firstRowOfEmail.get(key));
                } else if (takenEmails.contains(key)) {
                    errors.add("Work email " + email + " already belongs to an employee in this tenant");
                }
            }

            if (row.mobile() != null && row.mobile().length() > MAX_MOBILE) {
                errors.add("mobile is longer than " + MAX_MOBILE + " characters");
            }

            LocalDate joined = null;
            if (row.dateOfJoining() == null) {
                errors.add("date_of_joining is required");
            } else {
                joined = EmployeeImportParser.parseDate(row.dateOfJoining()).orElse(null);
                if (joined == null) {
                    errors.add(
                            "date_of_joining " + row.dateOfJoining() + " is not a date; use YYYY-MM-DD or DD/MM/YYYY");
                }
            }

            UUID departmentId = master(departments, row.department(), "department", errors);
            UUID designationId = master(designations, row.designation(), "designation", errors);
            UUID locationId = master(locations, row.location(), "location", errors);

            Optional<Boolean> access = EmployeeImportParser.parseYesNo(row.giveAccess());
            boolean giveAccess = access.orElse(false);
            if (access.isEmpty()) {
                errors.add("give_access must be Y or N, not " + row.giveAccess());
            }
            if (giveAccess && email == null) {
                errors.add("give_access is Y but there is no work_email to send the invitation to");
            }

            Set<UUID> roleIds = new LinkedHashSet<>();
            List<String> roleCodes = new ArrayList<>();
            List<String> requested = grantedRoleCodes(row.roles());
            if (!requested.isEmpty() && !giveAccess) {
                errors.add("roles are granted with portal access; set give_access to Y or clear roles");
            }
            for (String code : requested) {
                Role role = roles.get(code);
                if (role == null) {
                    errors.add("Role " + code + " does not exist in this tenant");
                } else if (PLATFORM_ADMIN_ROLE.equals(role.getCode())) {
                    errors.add("Role platform-admin cannot be granted from inside a tenant");
                } else {
                    roleIds.add(role.getId());
                    roleCodes.add(role.getCode());
                }
            }

            if (!errors.isEmpty()) {
                return Checked.failed(errors);
            }
            takenNumbers.add(number);
            if (email != null) {
                takenEmails.add(email.toLowerCase(Locale.ROOT));
            }
            EmployeeRequest request = new EmployeeRequest(
                    number,
                    row.firstName(),
                    null,
                    row.lastName(),
                    null,
                    joined,
                    null,
                    null,
                    email,
                    row.mobile(),
                    null,
                    departmentId,
                    designationId,
                    locationId);
            return new Checked(null, request, giveAccess, roleIds, roleCodes);
        }
    }

    /**
     * The role codes a {@code roles} cell asks for, beyond {@code employee} — which every acceptance grants
     * anyway. A file whose rows name any of these needs {@code core.role.assign} (W-73.3's rule).
     */
    public static List<String> grantedRoleCodes(String rolesCell) {
        return EmployeeImportParser.splitRoles(rolesCell).stream()
                .filter(code -> !EMPLOYEE_ROLE.equals(code))
                .toList();
    }

    /** True when any row asks for a role beyond {@code employee}. */
    public static boolean grantsRoles(List<EmployeeImportRow> rows) {
        return rows.stream().anyMatch(row -> !grantedRoleCodes(row.roles()).isEmpty());
    }

    /** Active masters by lower-cased code and by lower-cased name; a code wins over another's name. */
    private static <T extends OrgMaster> Map<String, UUID> activeMasters(
            OrgMasterRepository<T> repository, UUID tenantId) {
        List<T> active = repository.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId);
        Map<String, UUID> byKey = new HashMap<>();
        for (T master : active) {
            if (master.getName() != null) {
                byKey.put(master.getName().trim().toLowerCase(Locale.ROOT), master.getId());
            }
        }
        for (T master : active) {
            byKey.put(master.getCode().trim().toLowerCase(Locale.ROOT), master.getId());
        }
        return byKey;
    }

    private static UUID master(Map<String, UUID> masters, String value, String column, List<String> errors) {
        if (value == null) {
            return null;
        }
        UUID id = masters.get(value.toLowerCase(Locale.ROOT));
        if (id == null) {
            errors.add("No active " + column + " with code or name " + value);
        }
        return id;
    }

    // ---------------------------------------------------------------- helpers

    private static String reason(RuntimeException e) {
        if (e instanceof EmployeeService.ValidationException v) {
            return v.fieldErrors().values().stream().collect(Collectors.joining("; "));
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static int percent(int done, int total) {
        return total <= 0 ? 100 : Math.min(100, done * 100 / total);
    }

    /** {@code invited_by_user_id} must name a real person (W-24.2); refuse here, before anything is queued. */
    private static void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new IllegalStateException("No authenticated user could be resolved to record as the inviter");
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the job payload", e);
        }
    }

    private static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("The job payload could not be read", e);
        }
    }

    private static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
