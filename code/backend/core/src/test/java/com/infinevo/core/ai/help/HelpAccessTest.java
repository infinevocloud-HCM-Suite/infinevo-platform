package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.help.TestArticles.article;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.navigation.NavigationItemResponse;
import com.infinevo.core.navigation.NavigationResponse;
import com.infinevo.core.navigation.NavigationService;
import com.infinevo.shared.authz.RoleSource;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class HelpAccessTest {

    private final HelpLibrary library = HelpLibrary.of(List.of(
            article("leave-types", "/leave/types"),
            article("apply-leave", "/me"),
            article(
                    "view-payslip",
                    "/me",
                    null,
                    "payroll.payslip.read_own",
                    List.of("employee"),
                    List.of("payslip"),
                    "See my payslip",
                    "1. Open My Payslips.")));

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the viewer holds every path of the feed, children included, the feed's actions and the role codes")
    void viewerFromTheFeed() {
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        TenantContext.set(tenant);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.toString(), null, List.of()));
        NavigationService navigation = mock(NavigationService.class);
        when(navigation.navigation())
                .thenReturn(new NavigationResponse(
                        List.of(new NavigationItemResponse(
                                "core.leave",
                                "nav.leave",
                                "/leave/types",
                                List.of(new NavigationItemResponse(
                                        "core.leave.requests", "nav.leave.requests", "/leave/requests")))),
                        Set.of("core.leave.manage"),
                        Set.of(PlatformModule.HRMS),
                        "Co",
                        "/leave/types"));
        RoleSource roles = mock(RoleSource.class);
        when(roles.roleCodesOf(tenant, user)).thenReturn(Set.of("hr"));

        HelpViewer viewer = new HelpAccess(navigation, provider(roles)).currentViewer();

        assertThat(viewer.screens()).containsExactlyInAnyOrder("/leave/types", "/leave/requests");
        assertThat(viewer.actions()).containsExactly("core.leave.manage");
        assertThat(viewer.roles()).containsExactly("hr");
        assertThat(viewer.modules()).containsExactly(PlatformModule.HRMS);
    }

    @Test
    @DisplayName("a role source that fails leaves the roles empty and access unchanged")
    void rolesFailClosedToEmpty() {
        TenantContext.set(UUID.randomUUID());
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        UUID.randomUUID().toString(), null, List.of()));
        NavigationService navigation = mock(NavigationService.class);
        when(navigation.navigation())
                .thenReturn(new NavigationResponse(
                        List.of(new NavigationItemResponse("core.me", "nav.me", "/me")),
                        Set.of(),
                        Set.of(),
                        "Co",
                        "/me"));
        RoleSource roles = mock(RoleSource.class);
        when(roles.roleCodesOf(any(), any())).thenThrow(new IllegalStateException("down"));

        HelpViewer viewer = new HelpAccess(navigation, provider(roles)).currentViewer();

        assertThat(viewer.roles()).isEmpty();
        assertThat(viewer.screens()).containsExactly("/me");
    }

    @Test
    @DisplayName("an article whose screen is not in the feed is not found by search and not found by id")
    void screenNotInFeed() {
        HelpViewer employee = new HelpViewer(Set.of("/me"), Set.of(), Set.of("employee"));

        assertThat(library.find("leave-types", employee)).isEmpty();
        assertThat(library.search("leave types", employee))
                .extracting(HelpArticle::id)
                .doesNotContain("leave-types");
        assertThat(library.find("apply-leave", employee)).isPresent();
    }

    @Test
    @DisplayName("an article's action is honoured: without it the article is hidden, with it the article is seen")
    void actionHonoured() {
        HelpViewer without = new HelpViewer(Set.of("/me"), Set.of(), Set.of());
        HelpViewer with = new HelpViewer(Set.of("/me"), Set.of("payroll.payslip.read_own"), Set.of());

        assertThat(library.find("view-payslip", without)).isEmpty();
        assertThat(library.search("payslip", without)).isEmpty();
        assertThat(library.find("view-payslip", with)).isPresent();
        assertThat(library.search("payslip", with)).extracting(HelpArticle::id).containsExactly("view-payslip");
    }

    @Test
    @DisplayName("an article's module is honoured: a held action is not enough when the customer lacks the module")
    void moduleHonoured() {
        HelpLibrary payslip = HelpLibrary.of(List.of(new HelpArticle(
                "view-payslip",
                "See my payslip",
                "Summary.",
                List.of("employee"),
                "/me",
                null,
                "payroll.payslip.read_own",
                List.of("payslip"),
                "1. Open My Payslips.",
                PlatformModule.PAYROLL)));
        Set<String> held = Set.of("payroll.payslip.read_own");
        HelpViewer hrmsOnly = new HelpViewer(Set.of("/me"), held, Set.of(), Set.of(PlatformModule.HRMS));
        HelpViewer withPayroll = new HelpViewer(Set.of("/me"), held, Set.of(), Set.of(PlatformModule.PAYROLL));

        assertThat(payslip.find("view-payslip", hrmsOnly)).isEmpty();
        assertThat(payslip.search("payslip", hrmsOnly)).isEmpty();
        assertThat(payslip.find("view-payslip", withPayroll)).isPresent();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<RoleSource> provider(RoleSource source) {
        ObjectProvider<RoleSource> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(source);
        return provider;
    }
}
