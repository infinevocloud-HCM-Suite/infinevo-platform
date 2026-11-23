package com.infinevo.core.option;

import java.util.List;

/**
 * Service for typed tenant options (W-104 §4).
 */
public interface TenantOptionService {

    boolean getBoolean(String key);

    String getString(String key);

    Integer getInteger(String key);

    <T> T getJson(String key, Class<T> targetClass);

    TenantOptionResponse set(String key, String rawValue);

    TenantOptionResponse reset(String key);

    List<TenantOptionResponse> list(OptionCategory category);

    class NoSuchOptionException extends IllegalArgumentException {
        public NoSuchOptionException(String key) {
            super("Unknown tenant option key: " + key);
        }
    }

    class OptionValidationException extends IllegalArgumentException {
        public OptionValidationException(String message) {
            super(message);
        }
    }
}
