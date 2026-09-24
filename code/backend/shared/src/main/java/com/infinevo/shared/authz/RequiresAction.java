package com.infinevo.shared.authz;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The action a caller must hold to run this method (W-11.2, spec section 2).
 *
 * <pre>{@code
 * @GetMapping("/{id}")
 * @RequiresAction("core.employee.read")
 * public EmployeeResponse get(@PathVariable UUID id) { ... }
 * }</pre>
 *
 * <p>The code is one of those seeded in {@code reference.action}
 * ({@code migration/.../reference/V020__action.sql}). A caller who does not hold it in the bound
 * tenant gets {@code 403 FORBIDDEN} in the shared {@code ApiErrorResponse} envelope; so does every
 * caller when the check itself cannot be made — it fails closed ({@link PermissionService}).
 *
 * <p>On a type it guards every method the type declares; a method-level annotation wins over the
 * type's. Put it on the class that is the Spring bean — the controller — not on an interface it
 * implements: Spring AOP matches annotations on the target class's methods.
 *
 * <p>This replaces the frozen HRMS check by role name against a URL prefix
 * ({@code legacy/HRMS_Backend/.../config/SecurityConfig.java:39-43}) with a check by action, which is
 * how Payroll already decided ({@code AuthzServiceImpl.canPerform},
 * {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:84-89}).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresAction {

    /** The action code, {@code <module>.<resource>.<verb>} — e.g. {@code core.employee.read}. */
    String value();
}
