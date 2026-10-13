package com.infinevo.core.user;

import com.infinevo.shared.identity.UserAccount;
import com.infinevo.shared.identity.UserAccountRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Display names for the actors an audit column stores (D-79): {@code approved_by}, {@code paid_by},
 * {@code locked_by} and the like hold the caller's Keycloak subject, which a screen must not print.
 *
 * <p>The whole set is resolved against {@code core.user_account} in the bound tenant in one statement,
 * never one lookup per row, as {@code EmployeeDocumentQuery} names an uploader (W-73.5). A value that is
 * not a subject ({@code system}) or a subject with no synced account is simply absent from the map; the
 * caller sends {@code null} for its name.
 *
 * <p>Not guarded by an action, deliberately: it returns a name and nothing else, and only for the
 * subjects a record the caller may already read carries.
 */
@Service
public class ActorNameService {

    private final UserAccountRepository accounts;

    public ActorNameService(UserAccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    }

    /** Subject to display name, for the subjects that parse as a UUID and have an account in the bound tenant. */
    @Transactional(readOnly = true)
    public Map<String, String> displayNames(Collection<String> actors) {
        if (actors == null || actors.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = actors.stream()
                .filter(Objects::nonNull)
                .map(ActorNameService::parse)
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
        String name = Stream.of(account.getFirstName(), account.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return name.isEmpty() ? account.getEmail() : name;
    }

    private static Optional<UUID> parse(String subject) {
        try {
            return Optional.of(UUID.fromString(subject.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
