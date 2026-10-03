package com.infinevo.payroll.form16;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and filters entries from the Form 16 Part A TRACES ZIP archive (W-36.5 §2, §4, §7).
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>Path traversal entries (containing {@code ..}) are skipped.</li>
 *   <li>macOS metadata and hidden files ({@code __MACOSX}, {@code ._}, dot-files) are skipped.</li>
 *   <li>Non-PDF entries (not ending with {@code .pdf}) are skipped.</li>
 *   <li>PDF entries without a PAN are unmatched.</li>
 *   <li>Duplicate PAN entries within the same ZIP are both/all skipped.</li>
 * </ul>
 */
public final class PartAEntryParser {

    private static final Pattern PAN_PATTERN = Pattern.compile("(?i)[a-z]{5}[0-9]{4}[a-z]");

    private PartAEntryParser() {}

    public record ParsedEntry(String entryName, String pan) {}

    public record ParseResult(List<ParsedEntry> candidates, List<String> unmatched, List<String> skipped) {}

    public static Optional<String> extractPan(String entryName) {
        if (entryName == null) {
            return Optional.empty();
        }
        Matcher matcher = PAN_PATTERN.matcher(entryName);
        if (matcher.find()) {
            return Optional.of(matcher.group().toUpperCase());
        }
        return Optional.empty();
    }

    public static boolean isPathTraversal(String entryName) {
        return entryName != null && entryName.contains("..");
    }

    public static boolean isSystemOrMetadata(String entryName) {
        if (entryName == null) {
            return false;
        }
        return entryName.startsWith("__MACOSX/")
                || entryName.contains("/__MACOSX/")
                || entryName.startsWith("._")
                || entryName.contains("/._")
                || entryName.startsWith(".");
    }

    public static boolean isPdf(String entryName) {
        return entryName != null && entryName.toLowerCase().endsWith(".pdf");
    }

    public static ParseResult parse(List<String> entryNames) {
        if (entryNames == null || entryNames.isEmpty()) {
            return new ParseResult(List.of(), List.of(), List.of());
        }

        List<String> skipped = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        List<ParsedEntry> validEntries = new ArrayList<>();

        for (String name : entryNames) {
            if (name == null || name.isBlank() || name.endsWith("/")) {
                // skip directory entries
                continue;
            }
            if (isPathTraversal(name) || isSystemOrMetadata(name) || !isPdf(name)) {
                skipped.add(name);
                continue;
            }

            Optional<String> panOpt = extractPan(name);
            if (panOpt.isEmpty()) {
                unmatched.add(name);
                continue;
            }

            validEntries.add(new ParsedEntry(name, panOpt.get()));
        }

        // Group by PAN to detect duplicates within the ZIP
        Map<String, List<ParsedEntry>> byPan = new LinkedHashMap<>();
        for (ParsedEntry entry : validEntries) {
            byPan.computeIfAbsent(entry.pan(), k -> new ArrayList<>()).add(entry);
        }

        List<ParsedEntry> candidates = new ArrayList<>();
        for (Map.Entry<String, List<ParsedEntry>> entry : byPan.entrySet()) {
            List<ParsedEntry> group = entry.getValue();
            if (group.size() > 1) {
                // Duplicate PAN in ZIP: both/all skipped
                for (ParsedEntry dup : group) {
                    skipped.add(dup.entryName());
                }
            } else {
                candidates.add(group.get(0));
            }
        }

        return new ParseResult(
                Collections.unmodifiableList(candidates),
                Collections.unmodifiableList(unmatched),
                Collections.unmodifiableList(skipped));
    }
}
