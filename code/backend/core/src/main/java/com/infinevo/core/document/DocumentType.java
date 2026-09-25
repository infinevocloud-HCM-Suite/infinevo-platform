package com.infinevo.core.document;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The five file types the store accepts (W-21, contracts section 5 row 16): pdf, jpg, png, xlsx,
 * csv. Anything else is refused {@code 415}.
 *
 * <p><strong>The type is decided by the file name and then checked against the bytes.</strong> A
 * client-declared content type is a claim, not evidence, so it is not consulted at all. Each type
 * carries the signature its files begin with, and a file whose first bytes do not match its
 * extension is refused — a renamed executable does not become a PDF by being called {@code .pdf}.
 *
 * <p>Two limits, stated rather than hidden. An {@code xlsx} is a zip archive, so any zip renamed to
 * {@code .xlsx} passes the signature check; telling them apart means opening the archive, which is
 * the scanner's job ({@code W-59}), not this one. And a {@code csv} has no signature at all, so it is
 * only refused when it carries a NUL byte, which no text file does.
 */
enum DocumentType {
    PDF("application/pdf", List.of("pdf"), new byte[] {'%', 'P', 'D', 'F', '-'}),
    JPG("image/jpeg", List.of("jpg", "jpeg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", List.of("png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", List.of("xlsx"), new byte[] {
        'P', 'K', 0x03, 0x04
    }),
    CSV("text/csv", List.of("csv"), null);

    /** How far into a csv to look for a NUL byte. A binary file shows one almost at once. */
    private static final int TEXT_SNIFF_BYTES = 8192;

    private final String contentType;
    private final List<String> extensions;
    private final byte[] signature;

    DocumentType(String contentType, List<String> extensions, byte[] signature) {
        this.contentType = contentType;
        this.extensions = extensions;
        this.signature = signature;
    }

    /** The content type stored on the row and set on the blob. */
    String contentType() {
        return contentType;
    }

    /** The type a file name's extension names, if it is one of the five. Case-insensitive. */
    static Optional<DocumentType> ofFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.extensions.contains(extension))
                .findFirst();
    }

    /** The type named by a configuration value such as {@code pdf}. Unknown names are refused. */
    static DocumentType ofName(String name) {
        String wanted = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.extensions.contains(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown document type '" + name
                        + "' in document.upload.allowed-types; the store knows pdf, jpg, png, xlsx and csv"));
    }

    /** True when {@code content} begins the way a file of this type must. */
    boolean matches(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        if (signature == null) {
            int limit = Math.min(content.length, TEXT_SNIFF_BYTES);
            for (int i = 0; i < limit; i++) {
                if (content[i] == 0) {
                    return false;
                }
            }
            return true;
        }
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
