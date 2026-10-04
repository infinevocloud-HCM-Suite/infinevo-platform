package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * The caller's own certificate (W-36.5 §3): its document id and a W-21 signed link.
 *
 * <p>{@link #toString()} withholds the link, as {@code DocumentLinkService.SignedLink} does: the URL carries
 * the signature, and a signature in a log is a working download.
 */
public record PartAOwn(
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("link") String link,
        @JsonProperty("expires_at") Instant expiresAt) {

    @Override
    public String toString() {
        return "PartAOwn[documentId=" + documentId + ", link=<withheld>, expiresAt=" + expiresAt + "]";
    }
}
