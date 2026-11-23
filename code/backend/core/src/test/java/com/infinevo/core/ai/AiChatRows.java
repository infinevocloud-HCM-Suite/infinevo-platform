package com.infinevo.core.ai;

import com.infinevo.core.authz.AuthzTestSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Chat rows written and counted as the schema owner, which bypasses row-level security (W-114.3 tests). */
final class AiChatRows {

    private AiChatRows() {}

    static UUID session(UUID tenantId, UUID userId) throws SQLException {
        return insert("INSERT INTO core.ai_session (tenant_id, user_id) VALUES (?, ?) RETURNING id", tenantId, userId);
    }

    static UUID message(UUID tenantId, UUID sessionId, String role, String content) throws SQLException {
        return insert(
                "INSERT INTO core.ai_message (tenant_id, session_id, role, content) VALUES (?, ?, ?, ?) RETURNING id",
                tenantId,
                sessionId,
                role,
                content);
    }

    static UUID intent(UUID tenantId, UUID sessionId, UUID userId, UUID messageId) throws SQLException {
        return insert(
                "INSERT INTO core.ai_action_intent (tenant_id, session_id, user_id, message_id, action_code, payload,"
                        + " expires_at) VALUES (?, ?, ?, ?, 'core.leave.apply', '{}'::jsonb, now() + interval '10"
                        + " minutes') RETURNING id",
                tenantId,
                sessionId,
                userId,
                messageId);
    }

    /** Rows of the table with this session id (or id, for {@code ai_session}). */
    static long countForSession(String table, UUID sessionId) throws SQLException {
        String column = "ai_session".equals(table) ? "id" : "session_id";
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core." + table + " WHERE " + column + " = ?")) {
            ps.setObject(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** A column of a session row, read as the schema owner. */
    static Object sessionColumn(UUID sessionId, String column) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT " + column + " FROM core.ai_session WHERE id = ?")) {
            ps.setObject(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    private static UUID insert(String sql, Object... values) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }
}
