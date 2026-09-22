package com.infinevo.shared.logging;

import org.slf4j.MDC;

/**
 * AutoCloseable utility for managing SLF4J MDC tags with guaranteed cleanup in try-with-resources.
 */
public final class MdcLoggingContext implements AutoCloseable {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String TENANT_ID_KEY = "tenantId";
    public static final String USER_ID_KEY = "userId";

    private final String key;

    private MdcLoggingContext(String key, String value) {
        this.key = key;
        if (value != null) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

    public static MdcLoggingContext with(String key, String value) {
        return new MdcLoggingContext(key, value);
    }

    public static MdcLoggingContext withCorrelationId(String correlationId) {
        return with(CORRELATION_ID_KEY, correlationId);
    }

    public static MdcLoggingContext withTenantId(Object tenantId) {
        return with(TENANT_ID_KEY, tenantId != null ? tenantId.toString() : null);
    }

    public static MdcLoggingContext withUserId(Object userId) {
        return with(USER_ID_KEY, userId != null ? userId.toString() : null);
    }

    @Override
    public void close() {
        MDC.remove(key);
    }
}
