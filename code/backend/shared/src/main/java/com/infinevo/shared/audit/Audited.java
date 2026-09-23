package com.infinevo.shared.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts an entity in to the audit trail (W-22.1).
 *
 * <p>Capture is <strong>opt-in, never blanket</strong> — decision 2 of the spec. An entity
 * without this annotation is ignored by {@link AuditEventListener} and costs nothing, which
 * is what keeps a payroll batch from paying for a second write per row it never wanted.
 *
 * <pre>{@code
 * @Entity
 * @Table(name = "tenant", schema = "core")
 * @Audited
 * public class Tenant { ... }
 * }</pre>
 *
 * <p>To stop capturing an entity, remove the annotation. The rows already written stay
 * readable — see spec section 10.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Audited {}
