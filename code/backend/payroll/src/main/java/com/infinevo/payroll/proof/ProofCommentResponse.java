package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing one comment on a proof item (W-34.2 spec §4).
 *
 * @param id comment unique ID
 * @param itemId target item ID
 * @param authorEmployeeId employee ID of the author
 * @param authorRole role of the author ({@code EMPLOYEE} or {@code REVIEWER})
 * @param body comment text
 * @param createdAt timestamp when the comment was created
 */
public record ProofCommentResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("item_id") UUID itemId,
        @JsonProperty("author_employee_id") UUID authorEmployeeId,
        @JsonProperty("author_role") ProofCommentRole authorRole,
        @JsonProperty("body") String body,
        @JsonProperty("created_at") Instant createdAt) {

    public static ProofCommentResponse from(EmployeeProofItemComment entity) {
        return new ProofCommentResponse(
                entity.getId(),
                entity.getItemId(),
                entity.getAuthorEmployeeId(),
                entity.getAuthorRole(),
                entity.getBody(),
                entity.getCreatedAt());
    }
}
