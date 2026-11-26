package com.infinevo.core.ai.starter;

import com.infinevo.core.ai.starter.AiStarter.Kind;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The shipped starters (W-114.22, "The first starters"), in code because they are product text reviewed with it.
 *
 * <p>Tools of {@code hrms} and {@code payroll} are named as text only. A tool name that matches no registered tool is
 * not an error: it is how a part that is not built yet looks, and its starters simply never show (rule 7). The startup
 * checks refuse a duplicate id, a label over 40 characters and a tool name not in {@code module.verb_noun} form.
 */
@Component
public class AiStarterRegistry {

    static final int MAX_LABEL = 40;

    /** The roles in the order their starters are taken in turn: the narrower role first, "employee" last. */
    static final List<String> ROLE_ORDER = List.of("tenant-admin", "payroll-officer", "hr", "manager", "employee");

    private static final Pattern TOOL_NAME = Pattern.compile("^(core|hrms|payroll)\\.[a-z][a-z0-9]*(_[a-z0-9]+)*$");

    private static final List<String> EMPLOYEE = List.of("employee");
    private static final List<String> MANAGER = List.of("manager");
    private static final List<String> HR = List.of("hr");
    private static final List<String> PAYROLL = List.of("payroll-officer");
    private static final List<String> ADMIN = List.of("tenant-admin");

    static final List<AiStarter> SHIPPED = List.of(
            // Employee
            q("leave-left", "How many leave days do I have left?", "core.my_leave_balances", null, EMPLOYEE),
            q("payslip-explain", "Explain my last payslip", "payroll.explain_payslip", null, EMPLOYEE),
            q("tds-change", "Why did my TDS change this month?", "payroll.explain_tds", null, EMPLOYEE),
            q("holidays-next", "Which holidays are coming up?", "core.holidays", null, EMPLOYEE),
            t("leave-apply", "Apply leave for me", "core.propose_leave_request", null, EMPLOYEE),
            t("wfh-request", "Request work from home", "core.propose_wfh_request", null, EMPLOYEE),
            t("leave-cancel", "Cancel one of my leave requests", "core.propose_leave_cancel", null, EMPLOYEE),
            // Manager
            q("approvals-waiting", "What is waiting for my approval?", "core.my_pending_approvals", null, MANAGER),
            q("team-leave-week", "Who in my team is on leave this week?", "core.team_on_leave", null, MANAGER),
            q("manager-guide", "What can I do as a manager here?", "core.my_role_guide", null, MANAGER),
            // HR
            q("leave-today", "Who is on leave today?", "core.team_on_leave", null, HR),
            q("joiners-month", "Who joined this month?", "core.joiners", null, HR),
            q("leave-type-howto", "How do I add a leave type?", "core.search_help", "core.leave.types", HR),
            q("hr-month-end", "What should HR do before month end?", "core.my_role_guide", null, HR),
            t("employee-add", "Add a new employee", "core.propose_employee_create", null, HR),
            t("go-leave-allocations", "Take me to leave allocations", "core.find_screen", "core.leave.allocations", HR),
            // Payroll officer
            q("payrun-summary", "Summarise this month's pay run", "payroll.payrun_summary", null, PAYROLL),
            q("payrun-changes", "What changed from last month?", "payroll.payrun_summary", null, PAYROLL),
            q("payrun-zero-pay", "Who has zero or negative pay?", "payroll.payrun_summary", null, PAYROLL),
            q("payrun-next-step", "What is my next step on this run?", "payroll.payrun_next_step", null, PAYROLL),
            t("go-payruns", "Take me to the pay runs", "core.find_screen", "payroll.runs", PAYROLL),
            t("go-components", "Take me to salary components", "core.find_screen", "payroll.components", PAYROLL),
            // Tenant admin
            q("setup-left", "What is left to set up?", "core.setup_status", null, ADMIN),
            q("invite-hr", "How do I invite my HR team?", "core.search_help", "core.users", ADMIN),
            q("roles-what", "What can each role do?", "core.my_role_guide", "core.roles", ADMIN),
            q("statutory-howto", "How do I set up statutory details?", "core.search_help", "payroll.settings", ADMIN),
            t("go-company", "Take me to the company profile", "core.find_screen", "core.settings.company", ADMIN),
            t("go-roles", "Take me to roles", "core.find_screen", "core.roles", ADMIN));

    private final List<AiStarter> starters;

    public AiStarterRegistry() {
        this(SHIPPED);
    }

    /** For tests: another list, checked the same way. */
    AiStarterRegistry(List<AiStarter> starters) {
        this.starters = List.copyOf(check(starters));
    }

    /** Every starter, in registry order. */
    public List<AiStarter> all() {
        return starters;
    }

    /** The rule 7 checks; throws {@link IllegalStateException} naming the first bad starter. */
    static List<AiStarter> check(List<AiStarter> starters) {
        Objects.requireNonNull(starters, "starters must not be null");
        Set<String> ids = new HashSet<>();
        for (AiStarter s : starters) {
            if (s.id() == null || s.id().isBlank() || !ids.add(s.id())) {
                throw new IllegalStateException("AI starter id missing or used twice: " + s.id());
            }
            if (s.label() == null || s.label().isBlank() || s.label().length() > MAX_LABEL) {
                throw new IllegalStateException(
                        "AI starter " + s.id() + " needs a label of 1 to " + MAX_LABEL + " characters");
            }
            if (s.prompt() == null || s.prompt().isBlank() || s.kind() == null) {
                throw new IllegalStateException("AI starter " + s.id() + " needs a prompt and a kind");
            }
            if (s.toolName() == null || !TOOL_NAME.matcher(s.toolName()).matches()) {
                throw new IllegalStateException(
                        "AI starter " + s.id() + " names a tool not in module.verb_noun form: " + s.toolName());
            }
        }
        return starters;
    }

    private static AiStarter q(String id, String label, String tool, String screenKey, List<String> roles) {
        return new AiStarter(id, label, label, Kind.QUESTION, tool, screenKey, roles);
    }

    private static AiStarter t(String id, String label, String tool, String screenKey, List<String> roles) {
        return new AiStarter(id, label, label, Kind.TASK, tool, screenKey, roles);
    }
}
