package com.infinevo.shared.tenant;

import com.infinevo.shared.entitlement.EntitlementDeniedException;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.EntitlementSnapshot;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.impersonation.ActingAs;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Central write guard ensuring suspended or cancelled tenants cannot modify data (W-85.1).
 *
 * <p>Ordered ahead of {@code RequiresModuleAspect} (HIGHEST_PRECEDENCE + 80 vs + 90),
 * so mutating requests from suspended/cancelled tenants are refused with {@code 403 TENANT_SUSPENDED}
 * across all modules (Core, HRMS, Payroll).
 *
 * <p>Flow (§3):
 * <ul>
 *   <li>If switch disabled: pass</li>
 *   <li>Safe methods (GET, HEAD, OPTIONS): pass</li>
 *   <li>No tenant bound: pass</li>
 *   <li>Platform tenant or active impersonation (ActingAs): pass</li>
 *   <li>Method annotated with {@link AllowedWhenSuspended}: pass</li>
 *   <li>If tenant snapshot cannot be loaded (fail closed) or is suspended: throw {@code TENANT_SUSPENDED}</li>
 *   <li>Otherwise: pass</li>
 * </ul>
 */
@Aspect
public class TenantSuspensionAspect implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(TenantSuspensionAspect.class);

    /** Ordered before RequiresModuleAspect (HIGHEST_PRECEDENCE + 90). */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 80;

    private final EntitlementService entitlementService;
    private final PlatformTenant platformTenant;
    private final boolean enabled;

    public TenantSuspensionAspect(
            EntitlementService entitlementService, PlatformTenant platformTenant, boolean enabled) {
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
        this.platformTenant = Objects.requireNonNull(platformTenant, "platformTenant must not be null");
        this.enabled = enabled;
    }

    public TenantSuspensionAspect(EntitlementService entitlementService, PlatformTenant platformTenant) {
        this(entitlementService, platformTenant, true);
    }

    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.PutMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.PatchMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.DeleteMapping) "
            + "|| @annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object checkSuspension(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!enabled) {
            return joinPoint.proceed();
        }

        // Rule 1: Safe read-only HTTP methods pass
        if (isSafeMethod(joinPoint)) {
            return joinPoint.proceed();
        }

        // Rule 2: Unbound tenant passes (e.g. public endpoints before binding)
        Optional<UUID> tenantId = TenantContext.current();
        if (tenantId.isEmpty()) {
            return joinPoint.proceed();
        }

        // Rule 3: Platform tenant or active impersonation passes
        if (platformTenant.isPlatformTenant(tenantId.get()) || ActingAs.isActing()) {
            return joinPoint.proceed();
        }

        // Rule 4: Explicitly allowed when suspended passes
        if (isAllowedWhenSuspended(joinPoint)) {
            return joinPoint.proceed();
        }

        // Rule 5: Check tenant subscription suspension (fails closed on null or error)
        EntitlementSnapshot snapshot;
        try {
            snapshot = entitlementService.snapshotOf(tenantId.get());
        } catch (RuntimeException e) {
            log.warn("Tenant suspension check failed closed for tenant {}: {}", tenantId.get(), e.getMessage());
            throw new EntitlementDeniedException(ApiError.TENANT_SUSPENDED, null);
        }

        if (snapshot == null || snapshot.suspended()) {
            log.info("Write operation refused for tenant {}: subscription is suspended or cancelled", tenantId.get());
            throw new EntitlementDeniedException(ApiError.TENANT_SUSPENDED, null);
        }

        // Rule 6: Otherwise pass
        return joinPoint.proceed();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static boolean isSafeMethod(ProceedingJoinPoint joinPoint) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String method = request.getMethod();
            if (method != null) {
                String upper = method.toUpperCase();
                return "GET".equals(upper) || "HEAD".equals(upper) || "OPTIONS".equals(upper);
            }
        }

        // Fallback for tests or contexts without HttpServletRequest: check method annotations
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? AopUtils.getTargetClass(joinPoint.getTarget())
                : method.getDeclaringClass();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);

        if (AnnotatedElementUtils.hasAnnotation(specific, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(specific, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(specific, PatchMapping.class)
                || AnnotatedElementUtils.hasAnnotation(specific, DeleteMapping.class)) {
            return false;
        }

        RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(specific, RequestMapping.class);
        if (rm != null && rm.method().length > 0) {
            for (RequestMethod m : rm.method()) {
                if (m == RequestMethod.POST
                        || m == RequestMethod.PUT
                        || m == RequestMethod.PATCH
                        || m == RequestMethod.DELETE) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static boolean isAllowedWhenSuspended(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? AopUtils.getTargetClass(joinPoint.getTarget())
                : method.getDeclaringClass();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);

        return AnnotatedElementUtils.hasAnnotation(specific, AllowedWhenSuspended.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, AllowedWhenSuspended.class);
    }
}
