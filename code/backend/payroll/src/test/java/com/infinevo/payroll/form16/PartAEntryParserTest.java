package com.infinevo.payroll.form16;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PartAEntryParser (W-36.5 §7).
 */
class PartAEntryParserTest {

    @Test
    @DisplayName("Extracts upper-case PAN from standard file name: ABCDE1234F_2027-28.pdf")
    void extractsPanFromStandardFileName() {
        PartAEntryParser.ParseResult result = PartAEntryParser.parse(List.of("ABCDE1234F_2027-28.pdf"));

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).pan()).isEqualTo("ABCDE1234F");
        assertThat(result.candidates().get(0).entryName()).isEqualTo("ABCDE1234F_2027-28.pdf");
        assertThat(result.unmatched()).isEmpty();
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    @DisplayName("Extracts lower-case PAN and uppercases it: form16_abcde1234f.pdf")
    void extractsAndUppercasesLowerCasePan() {
        PartAEntryParser.ParseResult result = PartAEntryParser.parse(List.of("form16_abcde1234f.pdf"));

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).pan()).isEqualTo("ABCDE1234F");
        assertThat(result.candidates().get(0).entryName()).isEqualTo("form16_abcde1234f.pdf");
        assertThat(result.unmatched()).isEmpty();
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    @DisplayName("Skips non-PDF entries: readme.txt")
    void skipsNonPdfEntries() {
        PartAEntryParser.ParseResult result = PartAEntryParser.parse(List.of("readme.txt"));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.unmatched()).isEmpty();
        assertThat(result.skipped()).containsExactly("readme.txt");
    }

    @Test
    @DisplayName("Skips macOS metadata and hidden files: __MACOSX/._x.pdf")
    void skipsMacOsMetadataAndHiddenFiles() {
        PartAEntryParser.ParseResult result = PartAEntryParser.parse(List.of("__MACOSX/._x.pdf", "._hidden.pdf"));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.unmatched()).isEmpty();
        assertThat(result.skipped()).containsExactly("__MACOSX/._x.pdf", "._hidden.pdf");
    }

    @Test
    @DisplayName("Two entries for one PAN in ZIP are both skipped as duplicates")
    void skipsDuplicatePanEntriesInZip() {
        PartAEntryParser.ParseResult result =
                PartAEntryParser.parse(List.of("ABCDE1234F_part1.pdf", "ABCDE1234F_part2.pdf"));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.unmatched()).isEmpty();
        assertThat(result.skipped()).containsExactly("ABCDE1234F_part1.pdf", "ABCDE1234F_part2.pdf");
    }

    @Test
    @DisplayName("Skips path traversal entry: ../../evil.pdf")
    void skipsPathTraversalEntries() {
        PartAEntryParser.ParseResult result = PartAEntryParser.parse(List.of("../../evil.pdf", "dir/../evil.pdf"));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.unmatched()).isEmpty();
        assertThat(result.skipped()).containsExactly("../../evil.pdf", "dir/../evil.pdf");
    }

    @Test
    @DisplayName("Reports valid PDF without PAN as unmatched")
    void reportsPdfWithoutPanAsUnmatched() {
        PartAEntryParser.ParseResult result = PartAEntryParser.parse(List.of("notes_annual.pdf"));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.unmatched()).containsExactly("notes_annual.pdf");
        assertThat(result.skipped()).isEmpty();
    }
}
