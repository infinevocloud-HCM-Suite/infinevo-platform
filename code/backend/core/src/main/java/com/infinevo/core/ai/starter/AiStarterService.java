package com.infinevo.core.ai.starter;

import com.infinevo.core.ai.AiCaller;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.help.HelpAccess;
import com.infinevo.core.ai.help.HelpViewer;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which starters one caller sees (W-114.22, rules 2 to 4).
 *
 * <p>A starter is kept when its tool is among {@link AiToolRegistry#toolsFor} for this caller - which already applies
 * the tool's action, the employee record and the customer's modules - and its {@code screenKey}, when set, is a screen
 * of the caller's own menu. The caller's menu and role codes come from {@link HelpAccess}, the navigation feed and
 * role source the help library reads. Only starters written for one of the caller's roles are shown (founder
 * 2026-11-26), taken one role at a time in turn so no role fills all six. At most {@value #MAX}.
 */
@Service
public class AiStarterService {

    static final int MAX = 6;

    private final AiStarterRegistry registry;
    private final AiToolRegistry tools;
    private final HelpAccess access;
    private final EmployeeService employees;

    public AiStarterService(
            AiStarterRegistry registry, AiToolRegistry tools, HelpAccess access, EmployeeService employees) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.tools = Objects.requireNonNull(tools, "tools must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
    }

    /** The caller's starters, at most six, in the order the chips show. */
    @Transactional(readOnly = true)
    public List<AiStarter> forCaller() {
        UUID tenantId = TenantContext.require();
        UUID userId = AiCaller.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("No signed-in user with a UUID subject"));
        UUID employeeId = employees.currentEmployee().map(EmployeeResponse::id).orElse(null);
        Set<String> offered = tools.toolsFor(new AiToolContext(tenantId, userId, employeeId)).stream()
                .map(AiToolDescriptor::name)
                .collect(Collectors.toSet());
        return pick(registry.all(), offered, access.currentViewer());
    }

    /** Rules 2 to 4 over a fixed list; no lookups, for the unit tests. */
    static List<AiStarter> pick(List<AiStarter> all, Set<String> offered, HelpViewer viewer) {
        List<AiStarter> usable = all.stream()
                .filter(s -> offered.contains(s.toolName()))
                .filter(s -> s.screenKey() == null || viewer.leaves().containsKey(s.screenKey()))
                .toList();

        List<String> held = new ArrayList<>();
        AiStarterRegistry.ROLE_ORDER.stream().filter(viewer.roles()::contains).forEach(held::add);
        viewer.roles().stream().filter(r -> !held.contains(r)).sorted().forEach(held::add);

        Map<String, Deque<AiStarter>> byRole = new LinkedHashMap<>();
        for (String role : held) {
            byRole.put(
                    role,
                    new ArrayDeque<>(usable.stream()
                            .filter(s -> s.roles().contains(role))
                            .toList()));
        }
        Set<AiStarter> out = new LinkedHashSet<>();
        boolean any = true;
        while (out.size() < MAX && any) {
            any = false;
            for (Deque<AiStarter> queue : byRole.values()) {
                while (!queue.isEmpty() && out.contains(queue.peekFirst())) {
                    queue.pollFirst();
                }
                if (!queue.isEmpty() && out.size() < MAX) {
                    out.add(queue.pollFirst());
                    any = true;
                }
            }
        }
        // Own roles only (founder 2026-11-26): no starter written for a role the caller does not hold, even when
        // that leaves fewer than six.
        return List.copyOf(out);
    }
}
