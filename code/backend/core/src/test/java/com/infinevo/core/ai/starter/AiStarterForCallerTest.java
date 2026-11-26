package com.infinevo.core.ai.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.help.HelpAccess;
import com.infinevo.core.ai.help.HelpViewer;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** W-114.22: forCaller builds the caller as the chat does - bound tenant, signed-in user, own employee record. */
class AiStarterForCallerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final HelpViewer EMPLOYEE_VIEWER = new HelpViewer(Set.of(), Set.of(), Set.of("employee"));

    private final EmployeeService employees = mock(EmployeeService.class);

    @BeforeEach
    void signIn() {
        TenantContext.set(TENANT);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(USER.toString(), null, List.of()));
        EmployeeResponse me = mock(EmployeeResponse.class);
        when(me.id()).thenReturn(EMPLOYEE);
        when(employees.currentEmployee()).thenReturn(Optional.of(me));
    }

    @AfterEach
    void signOut() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the registry is asked for this tenant, this user and this employee; its answer decides the starters")
    void callerIsTheChatsCaller() {
        AiToolRegistry tools = mock(AiToolRegistry.class);
        when(tools.toolsFor(any()))
                .thenReturn(List.of(new AiToolDescriptor("core.holidays", "d", JsonNodeFactory.instance.objectNode())));

        List<AiStarter> out = new AiStarterService(
                        new AiStarterRegistry(), tools, HelpAccess.fixed(EMPLOYEE_VIEWER), employees)
                .forCaller();

        verify(tools).toolsFor(new AiToolContext(TENANT, USER, EMPLOYEE));
        assertThat(out).extracting(AiStarter::id).containsExactly("holidays-next");
    }

    @Test
    @DisplayName("break the guard once: a registry with no tool at all gives no starter")
    void noToolsNoStarters() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenReturn(true);
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class))).thenReturn(true);
        AiToolRegistry empty = new AiToolRegistry(permissions, entitlements, List.of());

        assertThat(new AiStarterService(new AiStarterRegistry(), empty, HelpAccess.fixed(EMPLOYEE_VIEWER), employees)
                        .forCaller())
                .isEmpty();
    }

    @Test
    @DisplayName("a login with no employee record is asked for with no employee")
    void noEmployeeRecord() {
        when(employees.currentEmployee()).thenReturn(Optional.empty());
        AiToolRegistry tools = mock(AiToolRegistry.class);
        when(tools.toolsFor(any())).thenReturn(List.of());

        new AiStarterService(new AiStarterRegistry(), tools, HelpAccess.fixed(EMPLOYEE_VIEWER), employees).forCaller();

        verify(tools).toolsFor(new AiToolContext(TENANT, USER, null));
    }
}
