package com.infinevo.shared.audit;

import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final UserAccountRepository userAccounts;

    public AuditQueryService(AuditLogRepository repository) {
        this(repository, (UserAccountRepository) null);
    }

    public AuditQueryService(AuditLogRepository repository, UserAccountRepository userAccounts) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.userAccounts = userAccounts;
    }

    @Autowired
    public AuditQueryService(
            AuditLogRepository repository, ObjectProvider<UserAccountRepository> userAccountsProvider) {
        this(repository, userAccountsProvider != null ? userAccountsProvider.getIfAvailable() : null);
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
            if (actor != null && !actor.isBlank()) {
                String trimmedActor = actor.trim();
                List<Predicate> actorPredicates = new ArrayList<>();
                actorPredicates.add(builder.equal(root.get("actorLabel"), trimmedActor));
                try {
                    UUID actorUuid = UUID.fromString(trimmedActor);
                    actorPredicates.add(builder.equal(root.get("actorUserId"), actorUuid));
                } catch (IllegalArgumentException ignored) {
                }
                if (userAccounts != null) {
                    userAccounts
                            .findByTenantIdAndEmailIgnoreCase(tenantId, trimmedActor)
                            .ifPresent(acc -> {
                                if (acc.getKeycloakUserId() != null) {
                                    actorPredicates.add(
                                            builder.equal(root.get("actorUserId"), acc.getKeycloakUserId()));
                                    actorPredicates.add(builder.equal(
                                            root.get("actorLabel"),
                                            acc.getKeycloakUserId().toString()));
                                }
                            });
                }
                predicates.add(builder.or(actorPredicates.toArray(new Predicate[0])));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> page = repository.findAll(specification, newestFirst(pageable));
        return enrich(tenantId, page);
    }

    private Page<AuditLogView> enrich(UUID tenantId, Page<AuditLog> page) {
        if (page.isEmpty() || userAccounts == null) {
            return page.map(AuditLogView::of);
        }
        Set<UUID> userIdsToLookup = new HashSet<>();
        for (AuditLog row : page.getContent()) {
            if (row.getActorUserId() != null) {
                userIdsToLookup.add(row.getActorUserId());
            } else if (isUuid(row.getActorLabel())) {
                try {
                    userIdsToLookup.add(UUID.fromString(row.getActorLabel()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        Map<UUID, String> emailByUserId = new HashMap<>();
        if (!userIdsToLookup.isEmpty()) {
            List<UserAccount> accounts = userAccounts.findByTenantIdAndKeycloakUserIdIn(tenantId, userIdsToLookup);
            for (UserAccount acc : accounts) {
                if (acc.getKeycloakUserId() != null
                        && acc.getEmail() != null
                        && !acc.getEmail().isBlank()) {
                    emailByUserId.put(acc.getKeycloakUserId(), acc.getEmail());
                }
            }
        }
        return page.map(row -> {
            AuditLogView view = AuditLogView.of(row);
            UUID keycloakId = row.getActorUserId();
            if (keycloakId == null && isUuid(row.getActorLabel())) {
                try {
                    keycloakId = UUID.fromString(row.getActorLabel());
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (keycloakId != null && emailByUserId.containsKey(keycloakId)) {
                String email = emailByUserId.get(keycloakId);
                return new AuditLogView(
                        view.id(),
                        view.occurredAt(),
                        view.actorUserId(),
                        email,
                        view.operation(),
                        view.entitySchema(),
                        view.entityTable(),
                        view.entityId(),
                        view.changedColumns(),
                        view.oldValues(),
                        view.newValues(),
                        view.traceId());
            }
            return view;
        });
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
