package com.infinevo.shared.authz;

import java.lang.reflect.Method;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Enforces {@link RequiresAction} before the method runs (W-11.2, spec section 3).
 *
 * <p>A refusal throws {@link PermissionDeniedException} and the method body never executes.
 * {@link AuthzExceptionHandler} turns it into {@code 403}.
 *
 * <p>Ordered ahead of the transaction and audit advice, so a refused call opens no transaction and
 * writes no audit row.
 */
@Aspect
public class RequiresActionAspect implements Ordered {

    /** Early, but not first: leaves room for anything that must wrap even the permission check. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final PermissionService permissionService;

    public RequiresActionAspect(PermissionService permissionService) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @Around("@annotation(com.infinevo.shared.authz.RequiresAction) "
            + "|| @within(com.infinevo.shared.authz.RequiresAction)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        RequiresAction required = requiredActionOf(joinPoint);
        if (required == null) {
            // The pointcut matched, so the annotation is there; not finding it is a bug here, and
            // the answer to a bug in an authorization check is no.
            throw new PermissionDeniedException("<unresolved>");
        }
        if (permissionService.holds(required.value())) {
            return joinPoint.proceed();
        }
        for (String alt : required.anyOf()) {
            if (alt != null && !alt.isBlank() && permissionService.holds(alt)) {
                return joinPoint.proceed();
            }
        }
        permissionService.require(required.value());
        return joinPoint.proceed();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /** The method's annotation if it has one, otherwise its class's. */
    private static RequiresAction requiredActionOf(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? AopUtils.getTargetClass(joinPoint.getTarget())
                : method.getDeclaringClass();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        RequiresAction onMethod = AnnotatedElementUtils.findMergedAnnotation(specific, RequiresAction.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresAction.class);
    }
}
