package com.infinevo.payroll.proof;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A {@link DocumentService} for the proof tests that writes real {@code core.document} rows and keeps the
 * bytes in memory (W-34.1).
 *
 * <p>{@code PayrollTestApp}'s stand-in writes no row, but a proof link has a foreign key to
 * {@code core.document}, so it cannot serve here. This one is {@code @Primary} and only imported by the
 * proof tests, which leaves the shared stand-in as the other payroll tests expect it.
 */
@TestConfiguration
public class ProofTestDocuments {

    /** Bytes by document id, so a download can be compared with what was uploaded. */
    static final Map<UUID, byte[]> BYTES = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public DocumentService proofDocumentService() {
        return new DocumentService() {
            @Override
            public UUID store(DocumentKind kind, UUID employeeId, String fileName, InputStream content) {
                byte[] bytes;
                try {
                    bytes = content.readAllBytes();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                UUID tenantId = TenantContext.require();
                UUID id = UUID.randomUUID();
                String name = fileName == null || fileName.isBlank() ? "proof.pdf" : fileName;
                try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                        PreparedStatement ps = conn.prepareStatement(
                                """
                                INSERT INTO core.document (id, tenant_id, employee_id, kind, file_name, content_type,
                                    size_bytes, blob_container, blob_path, checksum_sha256, created_by, updated_by)
                                VALUES (?, ?, ?, ?, ?, 'application/pdf', ?, 'test', ?, ?, 'test', 'test')
                                """)) {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, employeeId);
                    ps.setString(4, kind.name());
                    ps.setString(5, name);
                    ps.setLong(6, bytes.length);
                    ps.setString(7, tenantId + "/" + employeeId + "/" + kind + "/" + id);
                    ps.setString(8, sha256(bytes));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
                BYTES.put(id, bytes);
                return id;
            }

            @Override
            public UUID storeFile(DocumentKind kind, UUID employeeId, String fileName, Path file) {
                try (InputStream in = Files.newInputStream(file)) {
                    return store(kind, employeeId, fileName, in);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public DocumentResponse get(UUID id) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                        PreparedStatement ps = conn.prepareStatement(
                                """
                                SELECT employee_id, kind, file_name, content_type, size_bytes, checksum_sha256, created_at,
                                       created_by
                                  FROM core.document WHERE id = ? AND tenant_id = ? AND NOT is_deleted
                                """)) {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new NotFoundException(id);
                        }
                        return new DocumentResponse(
                                id,
                                rs.getObject(1, UUID.class),
                                DocumentKind.valueOf(rs.getString(2)),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getLong(5),
                                rs.getString(6).trim(),
                                rs.getTimestamp(7).toInstant(),
                                rs.getString(8));
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public DocumentContent open(UUID id) {
                DocumentResponse metadata = get(id);
                return new DocumentContent(metadata, new ByteArrayInputStream(BYTES.getOrDefault(id, new byte[0])));
            }

            @Override
            public void delete(UUID id) {
                get(id);
                try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                        PreparedStatement ps = conn.prepareStatement(
                                "UPDATE core.document SET is_deleted = true, updated_at = ? WHERE id = ?")) {
                    ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
                    ps.setObject(2, id);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
