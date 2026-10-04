package com.infinevo.payroll.form16;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one entry name from the TRACES Part A ZIP and says what to do with it (W-36.5 §2, §3, §9).
 *
 * <p>Pure: no file is opened and the name is never used as a path — the service writes each candidate
 * to a temp file of its own naming. A PDF is matched by the <em>first</em> PAN in its file name, after
 * upper-casing (spec §13 question 2). The frozen screen had no handler at all
 * ({@code legacy/Payroll-Fend-react/.../form16/generateForm16.js:38-43}), so there is nothing to port.
 */
public final class PartAEntryParser {

    /** The PAN shape, as {@code EmployeeIdentificationServiceImpl} validates it. */
    static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");

    private static final String MAC_METADATA = "__MACOSX";

    private PartAEntryParser() {}

    /** What an entry is. Only {@link #CANDIDATE} is ever read to disk and filed. */
    public enum Outcome {
        /** A PDF with a PAN in its name. */
        CANDIDATE,
        /** A PDF with no PAN in its name — reported unmatched, never guessed. */
        NO_PAN,
        /** Not a {@code .pdf}. Skipped. */
        NOT_PDF,
        /** macOS resource-fork metadata under {@code __MACOSX/}. Skipped. */
        MAC_METADATA,
        /** An absolute path, a {@code ..} segment or a control character. Skipped. */
        UNSAFE_PATH,
        /** One of two or more entries naming the same PAN. Every one is skipped. */
        DUPLICATE_PAN;

        /** True for an outcome the response lists under {@code skipped}. */
        public boolean isSkipped() {
            return this != CANDIDATE && this != NO_PAN;
        }
    }

    /** One entry: its name as it sits in the ZIP, the outcome, and the upper-case PAN for a candidate. */
    public record ParsedEntry(String name, Outcome outcome, String pan) {

        ParsedEntry withOutcome(Outcome newOutcome) {
            return new ParsedEntry(name, newOutcome, pan);
        }
    }

    /** Classifies one entry name. Directories are the caller's to ignore. */
    public static ParsedEntry parse(String entryName) {
        if (entryName == null || entryName.isBlank() || !isSafe(entryName)) {
            return new ParsedEntry(entryName == null ? "" : entryName, Outcome.UNSAFE_PATH, null);
        }
        String normalised = entryName.replace('\\', '/');
        for (String segment : normalised.split("/")) {
            if (MAC_METADATA.equals(segment)) {
                return new ParsedEntry(entryName, Outcome.MAC_METADATA, null);
            }
        }
        String fileName = normalised.substring(normalised.lastIndexOf('/') + 1);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return new ParsedEntry(entryName, Outcome.NOT_PDF, null);
        }
        Matcher matcher = PAN.matcher(fileName.toUpperCase(Locale.ROOT));
        if (!matcher.find()) {
            return new ParsedEntry(entryName, Outcome.NO_PAN, null);
        }
        return new ParsedEntry(entryName, Outcome.CANDIDATE, matcher.group());
    }

    /**
     * Marks every candidate whose PAN appears on more than one candidate as {@link Outcome#DUPLICATE_PAN}.
     * Filing either would be a guess (spec §9). Order is kept.
     */
    public static List<ParsedEntry> markDuplicates(List<ParsedEntry> entries) {
        Map<String, Integer> counts = new HashMap<>();
        for (ParsedEntry entry : entries) {
            if (entry.outcome() == Outcome.CANDIDATE) {
                counts.merge(entry.pan(), 1, Integer::sum);
            }
        }
        List<ParsedEntry> result = new ArrayList<>(entries.size());
        for (ParsedEntry entry : entries) {
            if (entry.outcome() == Outcome.CANDIDATE && counts.get(entry.pan()) > 1) {
                result.add(entry.withOutcome(Outcome.DUPLICATE_PAN));
            } else {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * No absolute path, no drive letter, no {@code ..} segment and no control character. The name is
     * never resolved against the file system, so this is defence in depth and an honest report: an
     * archive built to escape its directory is not a TRACES download.
     */
    private static boolean isSafe(String name) {
        if (name.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        String normalised = name.replace('\\', '/');
        if (normalised.startsWith("/")) {
            return false;
        }
        if (normalised.length() >= 2 && normalised.charAt(1) == ':') {
            return false;
        }
        for (String segment : normalised.split("/")) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        return true;
    }
}
