package com.infinevo.shared.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The one security filter chain: a JWT resource server and nothing else (W-10, spec section 4).
 *
 * <p>Keycloak is the only credential authority. This application mints no token, holds no password
 * and has no local login path — HRMS's custom JWT (`legacy/HRMS_Backend/.../service/JWTUtils.java:26`)
 * is being deleted, not adapted. Every request arrives with a bearer token or it does not arrive.
 *
 * <p><strong>No issuer configured means no startup.</strong> If Spring Boot has built no
 * {@link JwtDecoder} — which is what happens when
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is unset — this bean throws and the
 * application fails to start. The tempting alternative, configuring the resource server only
 * <em>if</em> a decoder is present, leaves a process that boots cleanly, reads as secured and
 * authenticates nobody. Spec section 10 settles the direction: failing to start is the safe one.
 *
 * <p>Authorisation decisions are deliberately absent. Roles, scopes and {@code hasAuthority} are
 * {@code W-11}; here every path but the health probe requires an authenticated principal, and the
 * tenant is bound afterwards by {@code TenantContextFilter}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class ResourceServerConfig {

    /**
     * The only unauthenticated infrastructure path. The orchestrator's liveness probe carries no
     * token, and a probe that cannot answer is a replica restart loop.
     *
     * <p>Kept to one entry on purpose: every path added here is a path no one has to authenticate
     * for, so the list is the security boundary and should stay readable at a glance. Application
     * endpoints that must be reachable without a token are not added here but to
     * {@link PublicEndpoints}, the {@code D-22} exception list, which this chain also permits.
     */
    public static final String HEALTH_PATH = "/actuator/health";

    /** Health sub-paths — {@code /actuator/health/liveness} and {@code /readiness}. */
    public static final String HEALTH_SUBPATHS = "/actuator/health/**";

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoders)
            throws Exception {

        JwtDecoder jwtDecoder = jwtDecoders.getIfAvailable();
        if (jwtDecoder == null) {
            throw new IllegalStateException(
                    "No JwtDecoder: the resource server has no issuer to validate tokens against. Set "
                            + "spring.security.oauth2.resourceserver.jwt.issuer-uri (KEYCLOAK_ISSUER_URI) and restart. "
                            + "This application refuses to serve HTTP without it rather than serve it "
                            + "unauthenticated — W-10 spec section 10.");
        }

        http.csrf(csrf -> csrf.disable())
                // Stateless: the token is the session. Nothing is kept between requests, so a
                // replica can be replaced mid-flight without logging anyone out.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        // Error dispatch is not a second request to authorise. Spring runs the
                        // chain again on the way out, and the bearer filter is once-per-request so
                        // it does not re-authenticate - under anyRequest().authenticated() a
                        // genuine 404 or 500 is therefore re-answered as 401. That masks every
                        // server-side fault as an authentication failure, which is both wrong and
                        // very hard to debug. It opens nothing: the original request was already
                        // authorised, or it never reached a handler.
                        .permitAll()
                        .requestMatchers(HEALTH_PATH, HEALTH_SUBPATHS)
                        .permitAll()
                        // The D-22 exception list: exact paths, each authorising its requests
                        // another way. TenantContextFilter exempts the same list.
                        .requestMatchers(PublicEndpoints.paths())
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                // Neither is a way in. Both are Spring Boot defaults, and a default login form on
                // an API is how a "temporary" local credential path gets born.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}
