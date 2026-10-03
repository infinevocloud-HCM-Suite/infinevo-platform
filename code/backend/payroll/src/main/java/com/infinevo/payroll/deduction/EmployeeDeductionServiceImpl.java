package com.infinevo.payroll.deduction;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.deduction.EmployeeDeductionRules.ParsedLine;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * W-35.2. {@link #enter} checks every line before it writes any — the rules, then the employee and the
 * document of each — so a bad line costs no write at all; then it writes each line's ledger row and its
 * deduction row in the one transaction, so a failure after that rolls the ledger rows back with the rest
 * (§9). The deduction's id is minted first, because the ledger row names it in its {@code source_ref}.
 *
 * <p>Ported from legacy {@code SalaryDeductionController.java:38-52} (batch entry). Not ported: the
 * pay run reading and flipping this table ({@code EmployeePayRunServiceImpl.java:335-348},
 * {@code :1208-1221}) — the run reads only the ledger now; the edit before {@code INPAYRUN}; the public
 * Cloudinary proof ({@code SalaryDeductionController.java:54-121}, DEBT-011).
 */
@Service
public class EmployeeDeductionServiceImpl implements EmployeeDeductionService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeDeductionServiceImpl.class);

    /** How this module names itself on the ledger, as W-35.1's claims do. */
    static final String SOURCE_MODULE = "payroll";

    static final String SOURCE_REF_PREFIX = "employee_deduction:";

    private static final String ACTOR_SYSTEM = "system";

    private final EmployeeDeductionRepository deductions;
    private final PayInputService payInputService;
    private final EmployeeService employeeService;
    private final DocumentService documentService;
    private final Clock clock;

    @Autowired
    public EmployeeDeductionServiceImpl(
            EmployeeDeductionRepository deductions,
            PayInputService payInputService,
            EmployeeService employeeService,
            DocumentService documentService) {
        this(deductions, payInputService, employeeService, documentService, EmployeeDeductionRules.defaultClock());
    }

    EmployeeDeductionServiceImpl(
            EmployeeDeductionRepository deductions,
            PayInputService payInputService,
            EmployeeService employeeService,
            DocumentService documentService,
            Clock clock) {
        this.deductions = Objects.requireNonNull(deductions, "deductions must not be null");
        this.payInputService = Objects.requireNonNull(payInputService, "payInputService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.documentService = Objects.requireNonNull(documentService, "documentService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public EmployeeDeductionBatchResponse enter(List<EmployeeDeductionLineRequest> lines) {
        UUID tenantId = TenantContext.require();
        List<ParsedLine> parsed = EmployeeDeductionRules.parseBatch(lines, YearMonth.now(clock));

        // Every line checked against the employee and the document before anything is written.
        Map<UUID, EmployeeResponse> employees = new HashMap<>();
        for (int i = 0; i < parsed.size(); i++) {
            ParsedLine line = parsed.get(i);
            EmployeeResponse employee = employees.get(line.employeeId());
            if (employee == null) {
                employee = activeEmployee(i, line.employeeId());
                employees.put(employee.id(), employee);
            }
            if (line.documentId() != null) {
                requireEmployeeDocument(i, line.documentId(), employee.id());
            }
        }

        String actor = currentActor();
        Map<UUID, String> names = employeeService.displayNames(employees.keySet());
        List<EmployeeDeductionResponse> rows = new ArrayList<>(parsed.size());
        for (ParsedLine line : parsed) {
            UUID id = UUID.randomUUID();
            PayInputResponse posted = payInputService.record(new PayInputCommand(
                    line.employeeId(),
                    line.period(),
                    PayInputKind.AD_HOC_DEDUCTION,
                    null,
                    Money.of(line.amount()),
                    SOURCE_MODULE,
                    SOURCE_REF_PREFIX + id));
            EmployeeDeduction saved = deductions.save(new EmployeeDeduction(
                    id,
                    tenantId,
                    line.employeeId(),
                    line.period(),
                    line.deductionType(),
                    line.amount(),
                    line.reason(),
                    line.remarks(),
                    line.documentId(),
                    posted.id(),
                    posted.postedPeriod(),
                    actor));
            rows.add(EmployeeDeductionResponse.from(saved, names.get(saved.getEmployeeId())));
        }
        deductions.flush();
        log.info("Entered {} salary deductions in tenant {}", rows.size(), tenantId);
        return EmployeeDeductionBatchResponse.of(rows);
    }

    @Override
    @Transactional
    public EmployeeDeductionResponse reverse(UUID id, String reason) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        String why = EmployeeDeductionRules.reversalReason(reason);
        // Locked, so a second reversal waits for this one and then sees REVERSED.
        EmployeeDeduction deduction =
                deductions.findForUpdate(id, tenantId).orElseThrow(() -> new EmployeeDeductionNotFoundException(id));
        if (deduction.getStatus() == DeductionState.REVERSED) {
            throw new DeductionAlreadyReversedException(id);
        }
        PayInputResponse reversal = payInputService.reverse(deduction.getPayInputId(), why);
        UUID reversedBy =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        deduction.reverse(reversal.id(), reversedBy, Instant.now().truncatedTo(ChronoUnit.MICROS), currentActor());
        EmployeeDeduction saved = deductions.saveAndFlush(deduction);
        EmployeeDeductionResponse response = withName(saved);
        log.info(
                "Reversed salary deduction {} in tenant {}: reversal pay input {} in {}",
                id,
                tenantId,
                reversal.id(),
                reversal.postedPeriod());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeDeductionResponse get(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        return deductions
                .findByTenantIdAndId(tenantId, id)
                .map(this::withName)
                .orElseThrow(() -> new EmployeeDeductionNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeDeductionResponse> list(
            UUID employeeId, YearMonth period, DeductionState status, DeductionType deductionType, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Specification<EmployeeDeduction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (period != null) {
                predicates.add(cb.equal(root.get("period"), period.toString()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (deductionType != null) {
                predicates.add(cb.equal(root.get("deductionType"), deductionType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<EmployeeDeduction> page = deductions.findAll(spec, pageable);
        Map<UUID, String> names = namesOf(page.getContent());
        return page.map(d -> EmployeeDeductionResponse.from(d, names.get(d.getEmployeeId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeDeductionResponse> listOwn() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user account"));
        List<EmployeeDeduction> own =
                deductions.findByTenantIdAndEmployeeIdOrderByPeriodDescCreatedAtDesc(tenantId, employee.id());
        Map<UUID, String> names = namesOf(own);
        return own.stream()
                .map(d -> EmployeeDeductionResponse.from(d, names.get(d.getEmployeeId())))
                .toList();
    }

    /** W-47.4 §4: one {@code displayNames} call for a page or list of rows. */
    private Map<UUID, String> namesOf(List<EmployeeDeduction> rows) {
        Set<UUID> ids = rows.stream().map(EmployeeDeduction::getEmployeeId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : employeeService.displayNames(ids);
    }

    private EmployeeDeductionResponse withName(EmployeeDeduction d) {
        return EmployeeDeductionResponse.from(
                d, employeeService.displayNames(Set.of(d.getEmployeeId())).get(d.getEmployeeId()));
    }

    /** §4: an active employee of the bound tenant; an unknown id and an inactive one are both {@code 400}. */
    private EmployeeResponse activeEmployee(int line, UUID employeeId) {
        EmployeeResponse employee;
        try {
            employee = employeeService.get(employeeId);
        } catch (EmployeeService.NotFoundException e) {
            throw new EmployeeDeductionValidationException(line, "no employee " + employeeId + " in this tenant");
        }
        if (employee.status() != EmploymentStatus.ACTIVE) {
            throw new EmployeeDeductionValidationException(
                    line, "employee " + employeeId + " is " + employee.status() + ", not ACTIVE");
        }
        return employee;
    }

    /** §4 and §13 decision 5: a document of kind {@code EMPLOYEE_DOCUMENT} that belongs to the employee. */
    private void requireEmployeeDocument(int line, UUID documentId, UUID employeeId) {
        DocumentResponse document;
        try {
            document = documentService.get(documentId);
        } catch (DocumentService.NotFoundException e) {
            throw new EmployeeDeductionValidationException(line, "no document " + documentId + " in this tenant");
        }
        if (document.kind() != DocumentKind.EMPLOYEE_DOCUMENT) {
            throw new EmployeeDeductionValidationException(
                    line, "document " + documentId + " must be of kind EMPLOYEE_DOCUMENT, is " + document.kind());
        }
        if (!employeeId.equals(document.employeeId())) {
            throw new EmployeeDeductionValidationException(
                    line, "document " + documentId + " does not belong to employee " + employeeId);
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return ACTOR_SYSTEM;
        }
        return auth.getName();
    }
}
