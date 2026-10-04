package com.infinevo.payroll.form16;

import com.infinevo.payroll.PayrollTestSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** ZIPs, PDFs and identification rows for the W-36.5 integration tests. Rows are written as migration_user. */
final class PartATestFixtures {

    static final String FY = "2026-2027";

    private PartATestFixtures() {}

    /** A small file that starts like a PDF, distinct per label so a download can be compared. */
    static byte[] pdf(String label) {
        return ("%PDF-1.4\n% Form 16 Part A " + label + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
    }

    /** A ZIP of the given entries, in order. */
    static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** A ZIP of {@code count} empty text entries. */
    static byte[] zipOfEntries(int count) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            entries.put("entry-" + i + ".txt", new byte[0]);
        }
        return zip(entries);
    }

    /**
     * A ZIP whose first entry claims to be encrypted: bit 0 of the general purpose flag in its local
     * header (offset 6). That is the bit {@code java.util.zip} refuses with "encrypted ZIP entry not
     * supported", exactly as it does for a password-protected TRACES download.
     */
    static byte[] encryptedZip() throws IOException {
        byte[] bytes = zip(Map.of("ABCDE1234F.pdf", pdf("encrypted")));
        bytes[6] = (byte) (bytes[6] | 1);
        return bytes;
    }

    static void insertPan(UUID tenantId, UUID employeeId, String pan) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee_identification (tenant_id, employee_id, pan_number) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setString(3, pan);
            ps.executeUpdate();
        }
    }

    static long count(String sql, Object... args) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
