package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.impersonation.ActingAs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The AI helper is off while acting as another user (W-114.13b, rule 1).
 *
 * <p>Act-as resolves the target user's actions ({@code PermissionService}), so {@code core.ai.use} alone would let
 * staff ask questions, read the target's chats and confirm actions in their name. Every request under
 * {@code /api/v1/ai/} is answered {@code 403 AI_DISABLED} here, before any AI controller runs, so nothing is stored,
 * counted or audited. {@code ActingAs} is bound by {@code TenantContextFilter}, which runs before any interceptor.
 */
@Configuration
public class AiActAsGuard implements HandlerInterceptor, WebMvcConfigurer {

    static final String PATHS = "/api/v1/ai/**";
    static final String MESSAGE = "The AI helper is off while acting as another user.";

    private final ObjectMapper json;

    public AiActAsGuard(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json must not be null");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns(PATHS);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!ActingAs.isActing()) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(
                response.getOutputStream(),
                AiErrorResponses.disabled(new AiDisabledException(MESSAGE)).getBody());
        return false;
    }
}
