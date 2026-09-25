package com.infinevo.shared.security;

import java.util.List;

/**
 * The application endpoints reachable with no bearer token — the reviewed exception list of
 * {@code D-22} ({@code docs/target-state/05-azure-architecture.md} section 7a).
 *
 * <p><strong>One list, read by every place that decides.</strong> Three do: {@link ResourceServerConfig}
 * lets the request through security, {@code TenantContextFilter} lets it through without a tenant,
 * and {@code TenantBindingAutoConfiguration}'s fallback chain does the first job in a context with no
 * resource server. W-10 found two such lists drifting apart twice — a liveness probe permitted by one
 * and refused {@code 401} by the other — so this is the only place a path is added.
 *
 * <p><strong>Exact paths, never prefixes.</strong> A prefix grant publishes every controller later
 * mapped beneath it. That is how the frozen system came to serve payslips to anyone:
 * {@code /api/public/**} is open in its security config
 * ({@code legacy/Payroll-Bend-SBoot/.../config/SecurityConfig.java:26}), and a test controller mapped
 * under it mints a valid payslip link for any ids it is given
 * ({@code legacy/Payroll-Bend-SBoot/.../controller/payruns/PublicPayslipController.java:69-77}).
 *
 * <p>Health probes are not here: they are infrastructure, not application endpoints, and keep their
 * own constants in {@link ResourceServerConfig}. An endpoint here must authorise every request some
 * other way — a signed, expiring token — and must not log that token.
 */
public final class PublicEndpoints {

    /**
     * {@code GET} — serves a stored document to whoever holds a signed, unexpired link (W-21). The link
     * names the tenant, the document and the expiry, all inside an HMAC; the controller binds the
     * tenant from it. Emailed links are opened with no session, which is why it cannot require one.
     */
    public static final String DOCUMENT_DOWNLOAD = "/api/v1/documents/download";

    /** Every exact path a request may reach with no bearer token. Grows only on purpose. */
    public static final List<String> PATHS = List.of(DOCUMENT_DOWNLOAD);

    static {
        for (String path : PATHS) {
            if (path.contains("*") || path.contains("{") || !path.startsWith("/api/v1/")) {
                throw new IllegalStateException(
                        "PublicEndpoints holds exact /api/v1 paths only, never a pattern: " + path + " — see D-22");
            }
        }
    }

    private PublicEndpoints() {}

    /** The list as an array, the shape Spring Security's {@code requestMatchers} takes. */
    public static String[] paths() {
        return PATHS.toArray(String[]::new);
    }
}
