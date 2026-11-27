package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.ai.help.RoleGuide;
import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.invitation.InvitationService;
import com.infinevo.core.invitation.UserInvitationRequest;
import com.infinevo.shared.authz.PermissionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Invites a user the AI helper proposed (W-114.28 step 5). Confirm calls
 * {@link InvitationService#createUserInvitation}, what {@code POST /api/v1/user-invitations} calls
 * ({@code invitation/UserInvitationController.java:52-60}), with the same extra rule: giving any role needs
 * {@code core.role.assign} as well. The payload holds role codes, never ids; they are found again at Confirm, so a role
 * removed in between, or an invitation sent in between, ends {@code FAILED}.
 */
@Component
public class UserInviteExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.user.invite";
    static final String USER_MANAGE = "core.user.manage";
    static final String ROLE_ASSIGN = "core.role.assign";
    static final String PLATFORM_ADMIN = "platform-admin";
    static final String EMAIL = "email";
    static final String ROLES = "roles";
    static final String NO_ROLE_ASSIGN =
            "Giving a role needs the right to assign roles. Invite without a role, or ask" + " someone who can.";
    static final String CUSTOM_ROLE = "A role made in this company. See Roles for what it can do.";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final InvitationService invitations;
    private final RoleRepository roles;
    private final PermissionService permissions;
    private final HelpLibrary help;

    public UserInviteExecutor(
            InvitationService invitations, RoleRepository roles, PermissionService permissions, HelpLibrary help) {
        this.invitations = Objects.requireNonNull(invitations, "invitations must not be null");
        this.roles = Objects.requireNonNull(roles, "roles must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.help = Objects.requireNonNull(help, "help must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return USER_MANAGE;
    }

    /** Company set-up: an administrator need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String email = OrgMasterCreateExecutor.text(payload, EMAIL);
        List<String> codes = codes(payload);
        List<AiActionField> fields = new ArrayList<>();
        fields.add(new AiActionField("Email", email));
        if (codes.isEmpty()) {
            fields.add(new AiActionField("Roles", "(none)"));
        }
        List<String> names = new ArrayList<>();
        for (String code : codes) {
            Optional<Role> role = roles.findByTenantIdAndCode(ctx.tenantId(), code);
            String name = role.map(Role::getName).orElse(code);
            names.add(name);
            fields.add(new AiActionField("Role: " + name, whatItDoes(help, code)));
        }
        String summary = "Invite " + email + (names.isEmpty() ? " with no role" : " as " + String.join(", ", names));
        return new AiActionDescription(summary, fields);
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        List<String> codes = codes(payload);
        if (!codes.isEmpty() && !permissions.holds(ROLE_ASSIGN)) {
            throw new IllegalStateException(NO_ROLE_ASSIGN);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String code : codes) {
            Role role = roles.findByTenantIdAndCode(ctx.tenantId(), code)
                    .filter(r -> !PLATFORM_ADMIN.equals(r.getCode()))
                    .orElseThrow(() -> new IllegalStateException("The role " + code + " is no longer there."));
            ids.add(role.getId());
        }
        String email = OrgMasterCreateExecutor.text(payload, EMAIL);
        invitations.createUserInvitation(new UserInvitationRequest(email, ids), ctx.userId());
        ObjectNode result = NODES.objectNode();
        result.put("email", email);
        return result;
    }

    /** The role guide's first duty for a shipped role; a role the company made has no guide. */
    static String whatItDoes(HelpLibrary help, String code) {
        return help.guide(code)
                .flatMap(g -> g.sections().stream()
                        .flatMap(s -> s.lines().stream())
                        .map(RoleGuide.Line::text)
                        .filter(t -> t != null && !t.isBlank())
                        .findFirst())
                .orElse(CUSTOM_ROLE);
    }

    static List<String> codes(JsonNode payload) {
        List<String> out = new ArrayList<>();
        if (payload != null && payload.path(ROLES).isArray()) {
            payload.path(ROLES).forEach(n -> out.add(n.asText()));
        }
        return out;
    }
}
