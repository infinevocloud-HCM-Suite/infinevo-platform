package com.infinevo.shared.entitlement;

import com.infinevo.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Enforces {@link RequiresModule} before the method executes (W-12.2, spec section 3).
 *
 * <p>Ordered ahead of {@code RequiresActionAspect} (ORDER = HIGHEST_PRECEDENCE + 90 vs 100),
 * so a tenant who has not bought a module is refused with {@code MODULE_NOT_ENTITLED} or
 * {@code TENANT_SUSPENDED} before user permissions are evaluated.
 */
@Aspect
public class RequiresModuleAspect implements Ordered {

    /** Ordered before RequiresActionAspect (HIGHEST_PRECEDENCE + 100). */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 90;

    private final EntitlementService entitlementService;

    public RequiresModuleAspect(EntitlementService entitlementService) {
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
    }

    @Around("@annotation(com.infinevo.shared.entitlement.RequiresModule) "
            + "|| @within(com.infinevo.shared.entitlement.RequiresModule)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        RequiresModule annotation = requiredModuleOf(joinPoint);
        if (annotation == null) {
            throw new EntitlementDeniedException(ApiError.MODULE_NOT_ENTITLED, null);
        }

        boolean isReadOnly = isReadOnlyRequest();
        entitlementService.require(annotation.value(), annotation.mode(), isReadOnly);
        return joinPoint.proceed();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static boolean isReadOnlyRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String method = request.getMethod();
            if (method != null) {
                String upper = method.toUpperCase();
                return "GET".equals(upper) || "HEAD".equals(upper) || "OPTIONS".equals(upper);
            }
        }
        return false;
    }

    private static RequiresModule requiredModuleOf(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? AopUtils.getTargetClass(joinPoint.getTarget())
                : method.getDeclaringClass();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        RequiresModule onMethod = AnnotatedElementUtils.findMergedAnnotation(specific, RequiresModule.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresModule.class);
    }
}
