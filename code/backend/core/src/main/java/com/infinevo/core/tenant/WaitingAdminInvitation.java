package com.infinevo.core.tenant;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * A tenant's administrator invitation that nobody has taken up yet, as {@code core.list_waiting_admin_invitations()}
 * (V168) reports it across row-level security (W-73.2).
 *
 * @param status {@code PENDING} (still live) or {@code EXPIRED}
 */
record WaitingAdminInvitation(UUID tenantId, UUID invitationId, String email, String status, Instant expiresAt) {

    static final String PENDING = "PENDING";
    static final String EXPIRED = "EXPIRED";

    /** Every waiting invitation, one per tenant at most. */
    static final String SELECT_ALL = "SELECT tenant_id, invitation_id, email, invitation_status, expires_at"
            + " FROM core.list_waiting_admin_invitations()";

    /** The one tenant's waiting invitation, if any; bind the tenant id. */
    static final String SELECT_FOR_TENANT = SELECT_ALL + " WHERE tenant_id = ?";

    static final RowMapper<WaitingAdminInvitation> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp expires = rs.getTimestamp("expires_at");
        return new WaitingAdminInvitation(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("invitation_id", UUID.class),
                rs.getString("email"),
                rs.getString("invitation_status"),
                expires != null ? expires.toInstant() : null);
    };
}
