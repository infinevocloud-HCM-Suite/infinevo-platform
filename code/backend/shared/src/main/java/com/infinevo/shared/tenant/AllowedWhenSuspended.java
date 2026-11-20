package com.infinevo.shared.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a mutating endpoint (POST, PUT, PATCH, DELETE) that remains accessible even when the
 * bound tenant's subscription is suspended or cancelled (W-85.1).
 *
 * <p>Used for endpoints that support statutory data retention and essential tenant exit rights
 * (e.g. data exports, marking notifications read, public invitation acceptance).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AllowedWhenSuspended {}
