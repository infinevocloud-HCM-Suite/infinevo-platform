package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.action.OrgMasterActionToolProvider.answer;
import static com.infinevo.core.ai.action.OrgMasterActionToolProvider.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.core.invitation.UserInvitationRepository;
import com.infinevo.shared.authz.PermissionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.28 step 5: {@code core.propose_user_invite}. Names roles in words and finds them among the company's roles
 * (by name or code; never platform-admin), refuses roles for a caller who may not assign them, refuses an email that
 * already has an invitation waiting, then proposes; nothing is sent until Confirm. Needs {@code core.user.manage}, the
 * Users screen's invite action.
 */
@Component
public class UserInviteToolProvider implements AiToolProvider {

    static final String TOOL = "core.propose_user_invite";
    static final int MAX_ROLES = 5;
    static final int MAX_EMAIL = 254;
    static final String PENDING = "An invitation to that email is already waiting. Resend it from the Users screen.";
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final RoleRepository roles;
    private final UserInvitationRepository pending;
    private final PermissionService permissions;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final ObjectNode inputSchema;

    @Autowired
    public UserInviteToolProvider(
            RoleRepository roles,
            UserInvitationRepository pending,
            PermissionService permissions,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(roles, pending, permissions, proposals, (TransactionOperations)
                new TransactionTemplate(transactionManager));
    }

    UserInviteToolProvider(
            RoleRepository roles,
            UserInvitationRepository pending,
            PermissionService permissions,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.roles = Objects.requireNonNull(roles, "roles must not be null");
        this.pending = Objects.requireNonNull(pending, "pending must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put(
                "email",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", MAX_EMAIL)
                        .put("description", "The email address to invite."));
        ObjectNode rolesProp = NODES.objectNode()
                .put("type", "array")
                .put("maxItems", MAX_ROLES)
                .put("description", "Role names, for example HR or Payroll officer. Leave out for no role.");
        rolesProp.putObject("items").put("type", "string").put("maxLength", 100);
        props.put("roles", rolesProp);
        this.inputSchema = schema(props, "email");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeInvite());
    }

    private final class ProposeInvite implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes inviting a person to use the app, with the roles named. Nothing is sent: the user sees a"
                    + " card and must press Confirm. Ask for the email if it is not given; never guess one.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return UserInviteExecutor.USER_MANAGE;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String raw = given(args, "email");
                if (raw == null) {
                    throw new BadInput("email is required. Ask the user for it.");
                }
                String email = raw.toLowerCase(Locale.ROOT);
                if (email.length() > MAX_EMAIL || !EMAIL_SHAPE.matcher(email).matches()) {
                    throw new BadInput("That is not an email address.");
                }
                List<String> wanted = roleNames(args);
                if (!wanted.isEmpty() && !permissions.holds(UserInviteExecutor.ROLE_ASSIGN)) {
                    throw new BadInput(UserInviteExecutor.NO_ROLE_ASSIGN);
                }
                AiProposedAction card = tx.execute(s -> {
                    List<Role> all = roles.findByTenantIdOrderByCodeAsc(ctx.tenantId()).stream()
                            .filter(r -> !UserInviteExecutor.PLATFORM_ADMIN.equals(r.getCode()))
                            .toList();
                    ArrayNode codes = NODES.arrayNode();
                    for (String w : wanted) {
                        Role role = match(all, w);
                        if (!contains(codes, role.getCode())) {
                            codes.add(role.getCode());
                        }
                    }
                    if (pending.findActivePendingByEmail(ctx.tenantId(), email, Instant.now())
                            .isPresent()) {
                        throw new BadInput(PENDING);
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(UserInviteExecutor.EMAIL, email);
                    payload.set(UserInviteExecutor.ROLES, codes);
                    return proposals.propose(ctx, UserInviteExecutor.ACTION_CODE, payload);
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return AiToolResult.failure(
                        e.getMessage() != null && e.getMessage().contains("already proposed")
                                ? "One step at a time. Ask the user about the next one."
                                : e.getMessage());
            }
        }
    }

    private static List<String> roleNames(JsonNode args) {
        JsonNode node = args.get("roles");
        List<String> out = new ArrayList<>();
        if (node == null || node.isNull()) {
            return out;
        }
        if (!node.isArray() || node.size() > MAX_ROLES) {
            throw new BadInput("roles must be a list of at most " + MAX_ROLES + " role names");
        }
        for (JsonNode n : node) {
            if (!n.isTextual() || n.asText().isBlank()) {
                throw new BadInput("each role must be a name");
            }
            out.add(n.asText().trim());
        }
        return out;
    }

    /** The role with that name or code, any case; an unknown one lists the company's roles. */
    static Role match(List<Role> all, String wanted) {
        return all.stream()
                .filter(r -> (r.getName() != null && r.getName().trim().equalsIgnoreCase(wanted))
                        || (r.getCode() != null && r.getCode().equalsIgnoreCase(wanted)))
                .findFirst()
                .orElseThrow(() -> new BadInput("No role named " + wanted + ". The roles are: "
                        + String.join(", ", all.stream().map(Role::getName).toList()) + "."));
    }

    private static boolean contains(ArrayNode codes, String code) {
        for (JsonNode n : codes) {
            if (n.asText().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
