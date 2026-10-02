package com.infinevo.payroll.proof;

import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindowRepository;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single implementation supplying both the {@code POI_PENDING} reminder audience and the
 * proof-of-investment chase list (W-34.3 spec sections 3 and 4).
 *
 * <p>Both readers query through this class so that reminders and the officer dashboard never
 * disagree on who is pending.
 */
@Repository
@Transactional(readOnly = true)
public class ProofPendingQueryImpl implements ProofPendingQuery {

    private static final Set<String> VALID_STATUSES =
            Set.of("NOT_STARTED", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED");

    @PersistenceContext
    private EntityManager entityManager;

    private final IncomeTaxDeclarationWindowRepository windowRepository;
    private final Clock clock;

    /** For tests: a fixed clock. Spring uses the other constructor; there is no Clock bean. */
    ProofPendingQueryImpl(
            EntityManager entityManager, IncomeTaxDeclarationWindowRepository windowRepository, Clock clock) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.windowRepository = Objects.requireNonNull(windowRepository, "windowRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Autowired
    public ProofPendingQueryImpl(EntityManager entityManager, IncomeTaxDeclarationWindowRepository windowRepository) {
        this(entityManager, windowRepository, TaxDeclarationRules.defaultClock());
    }

    @Override
    public List<UUID> findPendingEmployeeIds(UUID tenantId, String financialYear) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        String jpql =
                """
            SELECT e.id
            FROM EmployeeInvestmentDeclaration d
            JOIN Employee e ON e.id = d.employeeId AND e.tenantId = d.tenantId
            LEFT JOIN EmployeeProofOfInvestment p ON p.declarationId = d.id AND p.tenantId = d.tenantId
            WHERE d.tenantId = :tenantId
              AND d.financialYear = :financialYear
              AND d.status = :submittedStatus
              AND e.deleted = false
              AND e.status = :activeStatus
              AND (p.id IS NULL OR p.status IN (:pendingProofStatuses))
            ORDER BY e.id ASC
            """;

        return entityManager
                .createQuery(jpql, UUID.class)
                .setParameter("tenantId", tenantId)
                .setParameter("financialYear", financialYear)
                .setParameter("submittedStatus", DeclarationStatus.SUBMITTED)
                .setParameter("activeStatus", EmploymentStatus.ACTIVE)
                .setParameter("pendingProofStatuses", List.of(ProofStatus.DRAFT, ProofStatus.REJECTED))
                .getResultList();
    }

    @Override
    public Page<ProofChaseRow> findChaseRows(
            UUID tenantId, String financialYear, String statusFilter, String searchPrefix, Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        String normalizedStatus = validateAndNormalizeStatus(statusFilter);
        String escapedSearch = escapePrefixSearch(searchPrefix);

        // 1. Count query
        StringBuilder countJpql = new StringBuilder(
                """
            SELECT COUNT(d)
            FROM EmployeeInvestmentDeclaration d
            JOIN Employee e ON e.id = d.employeeId AND e.tenantId = d.tenantId
            LEFT JOIN EmployeeProofOfInvestment p ON p.declarationId = d.id AND p.tenantId = d.tenantId
            WHERE d.tenantId = :tenantId
              AND d.financialYear = :financialYear
              AND d.status = :submittedStatus
              AND e.deleted = false
            """);

        applyFilterClauses(countJpql, normalizedStatus, escapedSearch);

        Query countQuery = entityManager.createQuery(countJpql.toString());
        bindFilterParameters(countQuery, tenantId, financialYear, normalizedStatus, escapedSearch);

        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // 2. Data query
        StringBuilder dataJpql = new StringBuilder(
                """
            SELECT
                e.id,
                e.employeeNumber,
                e.firstName,
                e.lastName,
                d.taxRegime,
                p.id,
                p.status,
                p.submittedAt,
                (SELECT COALESCE(SUM(i.claimedAmount), 0) FROM EmployeeProofItem i WHERE i.tenantId = d.tenantId AND i.proofId = p.id AND i.claimedAmount > 0),
                (SELECT SUM(i.approvedAmount) FROM EmployeeProofItem i WHERE i.tenantId = d.tenantId AND i.proofId = p.id)
            FROM EmployeeInvestmentDeclaration d
            JOIN Employee e ON e.id = d.employeeId AND e.tenantId = d.tenantId
            LEFT JOIN EmployeeProofOfInvestment p ON p.declarationId = d.id AND p.tenantId = d.tenantId
            WHERE d.tenantId = :tenantId
              AND d.financialYear = :financialYear
              AND d.status = :submittedStatus
              AND e.deleted = false
            """);

        applyFilterClauses(dataJpql, normalizedStatus, escapedSearch);
        dataJpql.append(" ORDER BY e.lastName ASC, e.firstName ASC, e.id ASC");

        Query dataQuery = entityManager.createQuery(dataJpql.toString());
        bindFilterParameters(dataQuery, tenantId, financialYear, normalizedStatus, escapedSearch);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rawRows = dataQuery.getResultList();
        List<ProofChaseRow> rows = new ArrayList<>(rawRows.size());

        for (Object[] r : rawRows) {
            UUID employeeId = (UUID) r[0];
            String empNumber = (String) r[1];
            String firstName = (String) r[2];
            String lastName = (String) r[3];
            String fullName = firstName + (lastName != null && !lastName.isBlank() ? " " + lastName.trim() : "");
            String taxRegime = (String) r[4];
            UUID proofId = (UUID) r[5];
            ProofStatus proofStatus = (ProofStatus) r[6];
            Instant submittedAt = (Instant) r[7];

            BigDecimal claimedTotal = toBigDecimal(r[8]);
            BigDecimal approvedTotal = (proofStatus == ProofStatus.APPROVED) ? toBigDecimal(r[9]) : null;

            String reportedStatus = proofStatus != null ? proofStatus.name() : "NOT_STARTED";

            rows.add(new ProofChaseRow(
                    employeeId,
                    empNumber,
                    fullName,
                    taxRegime,
                    reportedStatus,
                    proofId,
                    submittedAt,
                    claimedTotal,
                    approvedTotal));
        }

        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public ProofChaseSummary getSummary(UUID tenantId, String financialYear) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        String jpql =
                """
            SELECT
                COALESCE(SUM(CASE WHEN p.id IS NULL THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN p.status = com.infinevo.payroll.proof.ProofStatus.DRAFT THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN p.status = com.infinevo.payroll.proof.ProofStatus.SUBMITTED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN p.status = com.infinevo.payroll.proof.ProofStatus.APPROVED THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN p.status = com.infinevo.payroll.proof.ProofStatus.REJECTED THEN 1L ELSE 0L END), 0L)
            FROM EmployeeInvestmentDeclaration d
            JOIN Employee e ON e.id = d.employeeId AND e.tenantId = d.tenantId
            LEFT JOIN EmployeeProofOfInvestment p ON p.declarationId = d.id AND p.tenantId = d.tenantId
            WHERE d.tenantId = :tenantId
              AND d.financialYear = :financialYear
              AND d.status = :submittedStatus
              AND e.deleted = false
            """;

        Object[] counts = (Object[]) entityManager
                .createQuery(jpql)
                .setParameter("tenantId", tenantId)
                .setParameter("financialYear", financialYear)
                .setParameter("submittedStatus", DeclarationStatus.SUBMITTED)
                .getSingleResult();

        long notStarted = counts[0] != null ? ((Number) counts[0]).longValue() : 0L;
        long draft = counts[1] != null ? ((Number) counts[1]).longValue() : 0L;
        long submitted = counts[2] != null ? ((Number) counts[2]).longValue() : 0L;
        long approved = counts[3] != null ? ((Number) counts[3]).longValue() : 0L;
        long rejected = counts[4] != null ? ((Number) counts[4]).longValue() : 0L;

        Optional<IncomeTaxDeclarationWindow> window =
                windowRepository.findByTenantIdAndFinancialYear(tenantId, financialYear);
        LocalDate dueDate =
                window.map(IncomeTaxDeclarationWindow::getPoiDueDate).orElse(null);
        LocalDate today = LocalDate.now(clock.withZone(TaxDeclarationRules.ZONE));
        boolean proofOpen = window.map(w -> w.isProofOpenOn(today)).orElse(false);

        return new ProofChaseSummary(notStarted, draft, submitted, approved, rejected, dueDate, proofOpen);
    }

    private static String validateAndNormalizeStatus(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        String upper = statusFilter.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(upper)) {
            throw new ProofValidationException("Unknown status filter: " + statusFilter);
        }
        return upper;
    }

    private static String escapePrefixSearch(String searchPrefix) {
        if (searchPrefix == null || searchPrefix.isBlank()) {
            return null;
        }
        String escaped = searchPrefix
                .trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                .toLowerCase(Locale.ROOT);
        return escaped + "%";
    }

    private static void applyFilterClauses(StringBuilder sb, String normalizedStatus, String escapedSearch) {
        if (normalizedStatus != null) {
            if ("NOT_STARTED".equals(normalizedStatus)) {
                sb.append(" AND p.id IS NULL");
            } else {
                sb.append(" AND p.status = :proofStatus");
            }
        }
        if (escapedSearch != null) {
            sb.append(" AND (LOWER(e.firstName) LIKE :search ESCAPE '!' OR LOWER(e.lastName) LIKE :search ESCAPE '!'"
                    + " OR LOWER(e.employeeNumber) LIKE :search ESCAPE '!')");
        }
    }

    private static void bindFilterParameters(
            Query query, UUID tenantId, String financialYear, String normalizedStatus, String escapedSearch) {
        query.setParameter("tenantId", tenantId);
        query.setParameter("financialYear", financialYear);
        query.setParameter("submittedStatus", DeclarationStatus.SUBMITTED);

        if (normalizedStatus != null && !"NOT_STARTED".equals(normalizedStatus)) {
            query.setParameter("proofStatus", ProofStatus.valueOf(normalizedStatus));
        }
        if (escapedSearch != null) {
            query.setParameter("search", escapedSearch);
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
