package com.infinevo.shared.authz;

import static com.infinevo.shared.authz.AuthzTestSupport.authenticate;
import static com.infinevo.shared.authz.AuthzTestSupport.identityResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * W-11.2 spec section 7 — the decision {@link PermissionService} makes, and every way it refuses.
 */
class PermissionServiceTest {

    private static final String READ = "core.employee.read";
    private static final String DELETE = "core.employee.delete";

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private FakeCacheService cache;
    private StubActionSource source;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        cache = new FakeCacheService();
        source = new StubActionSource();
        source.grant(tenant, user, READ);
        PermissionCache permissionCache = new PermissionCache(cache);
        service = new PermissionService(() -> permissionCache, () -> source, identityResolver());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A held action passes")
    void heldActionPasses() {
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(service.holds(READ)).isTrue();
        assertThatCode(() -> service.require(READ)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An unheld action is refused, and require() throws the exception answered 403")
    void unheldActionIsRefused() {
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(service.holds(DELETE)).isFalse();
        assertThatThrownBy(() -> service.require(DELETE))
                .isInstanceOf(PermissionDeniedException.class)
                .isInstanceOf(AccessDeniedException.class)
                .satisfies(e ->
                        assertThat(((PermissionDeniedException) e).actionCode()).isEqualTo(DELETE));
    }

    @Test
    @DisplayName("No tenant bound: refused, and the source is never asked")
    void unboundTenantFailsClosed() {
        authenticate(user);

        assertThat(service.holds(READ)).isFalse();
        assertThat(source.calls()).isZero();
    }

    @Test
    @DisplayName("No authenticated user: refused")
    void noUserFailsClosed() {
        TenantContext.set(tenant);

        assertThat(service.holds(READ)).isFalse();
        assertThat(source.calls()).isZero();
    }

    @Test
    @DisplayName("An anonymous or non-UUID principal: refused")
    void anonymousOrUnparseablePrincipalFailsClosed() {
        TenantContext.set(tenant);

        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", user.toString(), AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(service.holds(READ)).isFalse();

        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("not-a-uuid", "n/a", null));
        assertThat(service.holds(READ)).isFalse();
    }

    @Test
    @DisplayName("A non-JWT principal is read by name, as TenantAuthenticationExtractor does")
    void nonJwtPrincipalIsReadByName() {
        TenantContext.set(tenant);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.toString(), "n/a", null));

        assertThat(service.holds(READ)).isTrue();
    }

    @Test
    @DisplayName("A blank action code is refused")
    void blankActionCodeIsRefused() {
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(service.holds(" ")).isFalse();
        assertThat(service.holds(null)).isFalse();
    }

    @Test
    @DisplayName("No ActionSource bean: refused")
    void noActionSourceFailsClosed() {
        PermissionCache permissionCache = new PermissionCache(cache);
        PermissionService withoutSource = new PermissionService(() -> permissionCache, () -> null, identityResolver());
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(withoutSource.holds(READ)).isFalse();
    }

    @Test
    @DisplayName("No PermissionCache: refused")
    void noCacheFailsClosed() {
        PermissionService withoutCache = new PermissionService(() -> null, () -> source, identityResolver());
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(withoutCache.holds(READ)).isFalse();
    }

    @Test
    @DisplayName("No user_account row: refused, and nothing is cached - the row may exist on the next request")
    void unresolvedUserIsRefusedAndNotCached() {
        AtomicBoolean synced = new AtomicBoolean(false);
        UserAccountIdResolver resolver = (t, k) -> synced.get() ? Optional.of(k) : Optional.empty();
        PermissionCache permissionCache = new PermissionCache(cache);
        PermissionService lazyProfiles = new PermissionService(() -> permissionCache, () -> source, resolver);
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(lazyProfiles.holds(READ)).isFalse();
        assertThat(source.calls()).isZero();

        synced.set(true);
        assertThat(lazyProfiles.holds(READ)).isTrue();
    }

    @Test
    @DisplayName("The source throwing: refused")
    void sourceFailureFailsClosed() {
        source.failWith(new IllegalStateException("database down"));
        TenantContext.set(tenant);
        authenticate(user);

        assertThat(service.holds(READ)).isFalse();
    }
}
