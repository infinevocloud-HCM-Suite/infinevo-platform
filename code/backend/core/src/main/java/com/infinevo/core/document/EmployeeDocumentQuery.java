package com.infinevo.core.document;

import com.infinevo.core.employee.EmployeeDocumentView;
import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows of the employee page's Documents tab (W-73.5, spec sections 4 and 8): the employee's live
 * {@link DocumentKind#EMPLOYEE_DOCUMENT} documents, newest first, each naming its uploader.
 *
 * <p><strong>The uploader is a name, not a subject.</strong> {@code created_by} holds the uploader's
 * Keycloak subject ({@link DocumentServiceImpl#currentActor}); spec section 8 says the row shows "your
 * name". The subjects of the whole list are resolved to {@code core.user_account} in one statement —
 * never one lookup per row — and a subject with no synced account, or a value that is not a subject
 * at all ({@code system}), is shown as stored.
 */
@Service
public class EmployeeDocumentQuery {

    private final DocumentService documents;
    private final UserAccountRepository accounts;

    public EmployeeDocumentQuery(DocumentService documents, UserAccountRepository accounts) {
        this.documents = Objects.requireNonNull(documents, "documents must not be null");
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    }

    /** The employee's documents in the bound tenant, with uploader names. */
    @Transactional(readOnly = true)
    public List<EmployeeDocumentView> list(UUID employeeId) {
        List<DocumentResponse> rows = documents.findByEmployeeAndKind(employeeId, DocumentKind.EMPLOYEE_DOCUMENT);
        Map<String, String> names = displayNames(rows.stream()
                .map(DocumentResponse::createdBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return rows.stream()
                .map(row -> EmployeeDocumentView.from(row, names.getOrDefault(row.createdBy(), row.createdBy())))
                .toList();
    }

    /** Subject to display name, for the subjects that parse as a UUID and have an account here. */
    Map<String, String> displayNames(Set<String> subjects) {
        Set<UUID> ids = subjects.stream()
                .map(EmployeeDocumentQuery::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (UserAccount account : accounts.findByTenantIdAndKeycloakUserIdIn(TenantContext.require(), ids)) {
            names.put(account.getKeycloakUserId().toString(), displayName(account));
        }
        return names;
    }

    /** First and last name; the email when neither is synced. */
    static String displayName(UserAccount account) {
        String name = java.util.stream.Stream.of(account.getFirstName(), account.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return name.isEmpty() ? account.getEmail() : name;
    }

    private static Optional<UUID> parse(String subject) {
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
