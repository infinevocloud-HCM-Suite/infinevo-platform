package com.infinevo.core.report;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report definitions (W-23.1, spec section 4) and the failures a caller can act on. No method takes a
 * tenant; each reads it from {@code TenantContext}. All logic is in {@link ReportDefinitionServiceImpl}.
 */
public interface ReportDefinitionService {

    /** The tenant's definitions the caller may run — those whose {@code required_action} they hold. */
    List<ReportDefinitionResponse> list();

    /** Creates a tenant definition (decision D6: {@code POST} creates, {@code PUT} updates). */
    ReportDefinitionResponse create(ReportDefinitionRequest request);

    /** Replaces a tenant definition's fields, the code excepted. Refused for a system definition. */
    ReportDefinitionResponse update(UUID id, ReportDefinitionRequest request);

    /** No such definition in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No report definition " + id + " in this tenant");
        }
    }

    /** The request cannot be applied. Maps to {@code 400} with per-field detail. */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Map<String, String> fieldErrors;

        public ValidationException(Map<String, String> fieldErrors) {
            super("The request was not valid: " + fieldErrors);
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }

    /** This tenant already has a definition with this code. Maps to {@code 409}. */
    class DuplicateCodeException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public DuplicateCodeException(String code) {
            super("A report definition with code " + code + " already exists in this tenant");
        }
    }

    /** One of the seeded definitions, which nobody may change. Maps to {@code 409}. */
    class SystemDefinitionException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public SystemDefinitionException(String code) {
            super("Report definition " + code + " is a system definition and cannot be edited."
                    + " Create one of your own over the same source instead.");
        }
    }
}
