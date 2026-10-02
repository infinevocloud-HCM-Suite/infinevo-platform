package com.infinevo.core.portal;

import java.util.Objects;

/**
 * Descriptor of a self-service portal panel (W-25, spec section 4; {@code 12-core-contracts.md:109}).
 *
 * @param code the panel id the shell routes on, e.g. "profile", "leave", "documents", "payslips", "timesheet"
 * @param title the user-facing title of the panel
 * @param displayOrder the order in which panels are presented in the portal
 * @param endpoint the data endpoint the panel reads
 * @param requiredAction the action code required to view this panel
 */
public record PanelDescriptor(String code, String title, int displayOrder, String endpoint, String requiredAction) {

    public PanelDescriptor {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(requiredAction, "requiredAction must not be null");
    }
}
