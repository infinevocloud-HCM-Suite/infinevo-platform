package com.infinevo.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A test-only {@code @Embedded} value object: <strong>one</strong> persistent property on
 * {@link AuditedProbe}, <strong>two</strong> database columns.
 *
 * <p>This is the shape the W-13.2 redaction fix exists for. Hibernate reports the property as
 * {@code address}, a Java name, and {@code AuditEventListener.columnNames} cannot resolve it to a
 * single column — so {@code AuditWriter} withholds its value outright. Before the fix, {@link
 * #toString()} was stored in {@code core.audit_log} in clear, and no deny-list entry could have
 * stopped it, because the deny-list matches column names and {@code address} is not one.
 */
@Embeddable
public class ProbeAddress {

    @Column(name = "address_line1", length = 200)
    private String line1;

    @Column(name = "zip_code", length = 12)
    private String zipCode;

    public ProbeAddress() {}

    public ProbeAddress(String line1, String zipCode) {
        this.line1 = line1;
        this.zipCode = zipCode;
    }

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    /**
     * Value equality, not identity. Hibernate deep-copies a component into the loaded state, so
     * the old and the new state hold different instances of an address that never changed;
     * without this, {@code AuditWriter.changedProperties} would report {@code address} as changed
     * on every single update.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProbeAddress address)) {
            return false;
        }
        return Objects.equals(line1, address.line1) && Objects.equals(zipCode, address.zipCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(line1, zipCode);
    }

    /** Deliberately revealing: the assertion in {@code AuditCaptureIT} is that this never lands. */
    @Override
    public String toString() {
        return line1 + ", " + zipCode;
    }
}
