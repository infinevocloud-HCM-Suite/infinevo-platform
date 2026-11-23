package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Every module's AI helper tools, collected at startup (W-114.3), the way {@code PortalPanelService} collects its
 * providers ({@code core/portal/PortalPanelService.java:35-47}).
 *
 * <p>Startup fails on a tool with no name, a name that is not {@code module.verb_noun} (lower case, one dot, the
 * module first), two tools of one name, or a tool with no {@code requiredAction}.
 *
 * <p>A caller may use a tool when they hold its {@code requiredAction} or any code of its {@code requiredAnyOf},
 * checked through {@link PermissionService} as {@code PortalPanelService} checks a panel ({@code :78}). The check is
 * made for {@link #toolsFor}, so the model is never told of a tool the user may not use, and again by
 * {@link #execute} at the moment of the call. Both refuse a context that is not the bound tenant and signed-in user:
 * nothing here switches tenant or raises rights.
 */
@Component
public class AiToolRegistry {

    /** {@code module.verb_noun}: a lower-case module, one dot, then a lower-case name of letters, digits and _. */
    static final Pattern NAME = Pattern.compile("^[a-z]+\\.[a-z][a-z0-9_]*$");

    private static final Logger log = LoggerFactory.getLogger(AiToolRegistry.class);

    private final PermissionService permissionService;
    private final Map<String, AiTool> tools;

    @Autowired
    public AiToolRegistry(PermissionService permissionService, ObjectProvider<AiToolProvider> providers) {
        this(permissionService, providers.orderedStream().toList());
    }

    public AiToolRegistry(PermissionService permissionService, List<AiToolProvider> providers) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.tools = collect(providers == null ? List.of() : providers);
        log.info("AI tool registry holds {} tool(s): {}", tools.size(), tools.keySet());
    }

    /** The tool of this name, whether or not the caller may use it. */
    public Optional<AiTool> find(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name));
    }

    /** The tools the caller may use, in the order the providers declared them. Empty for a context not the caller's. */
    public List<AiTool> toolsFor(AiToolContext caller) {
        if (!isBoundCaller(caller)) {
            return List.of();
        }
        return tools.values().stream().filter(this::mayUse).toList();
    }

    /**
     * Runs the tool for the caller, after checking again that they may use it. A refusal runs nothing and answers a
     * failed {@link AiToolResult}, which the model reads as any other tool error.
     */
    public AiToolResult execute(String name, AiToolContext ctx, JsonNode input) {
        AiTool tool = name == null ? null : tools.get(name);
        if (tool == null) {
            log.warn("AI tool refused: no tool named {}", name);
            return AiToolResult.failure("Unknown tool: " + name);
        }
        if (!isBoundCaller(ctx)) {
            log.warn("AI tool {} refused: the context is not the bound tenant and signed-in user", name);
            return AiToolResult.failure("Not permitted");
        }
        if (!mayUse(tool)) {
            log.warn(
                    "AI tool {} refused: caller holds neither {} nor any of {}",
                    name,
                    tool.requiredAction(),
                    tool.requiredAnyOf());
            return AiToolResult.failure("Not permitted");
        }
        return tool.execute(ctx, input);
    }

    private boolean mayUse(AiTool tool) {
        if (permissionService.holds(tool.requiredAction())) {
            return true;
        }
        List<String> anyOf = tool.requiredAnyOf();
        return anyOf != null && anyOf.stream().anyMatch(code -> code != null && permissionService.holds(code));
    }

    private static boolean isBoundCaller(AiToolContext ctx) {
        if (ctx == null) {
            return false;
        }
        Optional<UUID> tenant = TenantContext.current();
        Optional<UUID> user = AiCaller.currentUserId();
        return tenant.isPresent()
                && tenant.get().equals(ctx.tenantId())
                && user.isPresent()
                && user.get().equals(ctx.userId());
    }

    private static Map<String, AiTool> collect(List<AiToolProvider> providers) {
        Map<String, AiTool> byName = new LinkedHashMap<>();
        for (AiToolProvider provider : providers) {
            if (provider == null || provider.tools() == null) {
                continue;
            }
            for (AiTool tool : provider.tools()) {
                if (tool == null) {
                    continue;
                }
                String name = tool.name();
                if (name == null || !NAME.matcher(name).matches()) {
                    throw new IllegalStateException("AI tool name '" + name + "' from "
                            + tool.getClass().getName()
                            + " is not module.verb_noun (lower case, one dot, the module first)");
                }
                if (tool.requiredAction() == null || tool.requiredAction().isBlank()) {
                    throw new IllegalStateException("AI tool '" + name + "' declares no requiredAction");
                }
                AiTool earlier = byName.putIfAbsent(name, tool);
                if (earlier != null) {
                    throw new IllegalStateException("Two AI tools are named '" + name + "': "
                            + earlier.getClass().getName() + " and "
                            + tool.getClass().getName());
                }
            }
        }
        return Collections.unmodifiableMap(byName);
    }
}
