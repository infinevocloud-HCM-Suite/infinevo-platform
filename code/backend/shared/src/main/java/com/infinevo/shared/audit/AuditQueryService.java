package com.infinevo.shared.audit;

import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the audit trail for the bound tenant (W-22.1, spec section 4).
 *
 * <p>An audit row nobody can read cannot be tested, which is why capture and query are one
 * ticket - spec, size cap.
 */
@Service
public class AuditQueryService {

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** One audit row as the API returns it. The tenant is implicit and never echoed back. */
    public record AuditLogView(
            UUID id,
            Instant occurredAt,
            UUID actorUserId,
            String actorLabel,
            String operation,
            String entitySchema,
            String entityTable,
            String entityId,
            List<String> changedColumns,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            String traceId) {

        static AuditLogView of(AuditLog row) {
            String[] columns = row.getChangedColumns();
            return new AuditLogView(
                    row.getId(),
                    row.getOccurredAt(),
                    row.getActorUserId(),
                    row.getActorLabel(),
                    row.getOperation(),
                    row.getEntitySchema(),
                    row.getEntityTable(),
                    row.getEntityId(),
                    columns == null ? null : Arrays.asList(columns),
                    row.getOldValues(),
                    row.getNewValues(),
                    row.getTraceId());
        }
    }

    /**
     * Every filter is optional. The tenant is not: it comes from {@link TenantContext}, never from
     * a parameter, so a caller cannot ask for another tenant's trail by typing one in.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogView> search(
            String entityTable, String entityId, String actor, Instant from, Instant to, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Specification<AuditLog> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("tenantId"), tenantId));
            equalIfPresent(builder, root, "entityTable", entityTable).ifPresent(predicates::add);
            equalIfPresent(builder, root, "entityId", entityId).ifPresent(predicates::add);
            equalIfPresent(builder, root, "actorLabel", actor).ifPresent(predicates::add);
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, newestFirst(pageable)).map(AuditLogView::of);
    }

    /** Newest first unless the caller asked for an order of its own. */
    private static Pageable newestFirst(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
    }

    private static Optional<Predicate> equalIfPresent(
            CriteriaBuilder builder, Root<AuditLog> root, String attribute, String value) {
        return Optional.ofNullable(blankToNull(value)).map(present -> builder.equal(root.get(attribute), present));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
