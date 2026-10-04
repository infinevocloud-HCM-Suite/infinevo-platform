package com.infinevo.payroll.form16;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.form16.PartAEntryParser.Outcome;
import com.infinevo.payroll.form16.PartAEntryParser.ParsedEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-36.5 §7: how one Part A ZIP entry name is read. */
class PartAEntryParserTest {

    @Test
    @DisplayName("ABCDE1234F_2027-28.pdf yields PAN ABCDE1234F")
    void panAtStart() {
        ParsedEntry entry = PartAEntryParser.parse("ABCDE1234F_2027-28.pdf");
        assertThat(entry.outcome()).isEqualTo(Outcome.CANDIDATE);
        assertThat(entry.pan()).isEqualTo("ABCDE1234F");
        assertThat(entry.name()).isEqualTo("ABCDE1234F_2027-28.pdf");
    }

    @Test
    @DisplayName("a lower-case PAN is matched after upper-casing")
    void lowerCasePan() {
        ParsedEntry entry = PartAEntryParser.parse("form16_abcde1234f.pdf");
        assertThat(entry.outcome()).isEqualTo(Outcome.CANDIDATE);
        assertThat(entry.pan()).isEqualTo("ABCDE1234F");
    }

    @Test
    @DisplayName("the first PAN in the name wins, and the extension is case-insensitive")
    void firstPanWins() {
        ParsedEntry entry = PartAEntryParser.parse("TRACES/ZZZZZ9999Z_AAAAA1111A.PDF");
        assertThat(entry.outcome()).isEqualTo(Outcome.CANDIDATE);
        assertThat(entry.pan()).isEqualTo("ZZZZZ9999Z");
    }

    @Test
    @DisplayName("a PAN in a folder name does not count; only the file name is read")
    void folderPanIgnored() {
        ParsedEntry entry = PartAEntryParser.parse("ABCDE1234F/certificate.pdf");
        assertThat(entry.outcome()).isEqualTo(Outcome.NO_PAN);
        assertThat(entry.outcome().isSkipped()).isFalse();
    }

    @Test
    @DisplayName("readme.txt is skipped as not a PDF")
    void notPdf() {
        ParsedEntry entry = PartAEntryParser.parse("readme.txt");
        assertThat(entry.outcome()).isEqualTo(Outcome.NOT_PDF);
        assertThat(entry.outcome().isSkipped()).isTrue();
    }

    @Test
    @DisplayName("__MACOSX/._x.pdf is skipped")
    void macMetadata() {
        ParsedEntry entry = PartAEntryParser.parse("__MACOSX/._x.pdf");
        assertThat(entry.outcome()).isEqualTo(Outcome.MAC_METADATA);
        assertThat(entry.outcome().isSkipped()).isTrue();
        assertThat(PartAEntryParser.parse("certs/__MACOSX/._ABCDE1234F.pdf").outcome())
                .isEqualTo(Outcome.MAC_METADATA);
    }

    @Test
    @DisplayName("../../evil.pdf is skipped as path traversal, as are absolute and drive paths")
    void pathTraversal() {
        assertThat(PartAEntryParser.parse("../../evil.pdf").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("../../ABCDE1234F.pdf").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("a\\..\\ABCDE1234F.pdf").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("/etc/ABCDE1234F.pdf").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("C:/ABCDE1234F.pdf").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("ABCDE1234F\u0000.pdf").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("../../evil.pdf").outcome().isSkipped())
                .isTrue();
    }

    @Test
    @DisplayName("a dotted file name that is not a .. segment is not traversal")
    void dotsInNameAreFine() {
        assertThat(PartAEntryParser.parse("ABCDE1234F..2027.pdf").outcome()).isEqualTo(Outcome.CANDIDATE);
    }

    @Test
    @DisplayName("a PDF with no PAN in its name is unmatched, not skipped")
    void noPan() {
        ParsedEntry entry = PartAEntryParser.parse("Form16_PartA.pdf");
        assertThat(entry.outcome()).isEqualTo(Outcome.NO_PAN);
        assertThat(entry.pan()).isNull();
    }

    @Test
    @DisplayName("two entries for one PAN are both skipped as duplicates; the others are kept in order")
    void duplicatePan() {
        List<ParsedEntry> marked = PartAEntryParser.markDuplicates(List.of(
                PartAEntryParser.parse("ABCDE1234F_a.pdf"),
                PartAEntryParser.parse("readme.txt"),
                PartAEntryParser.parse("PQRST5678K.pdf"),
                PartAEntryParser.parse("copy/abcde1234f_b.pdf")));

        assertThat(marked)
                .extracting(ParsedEntry::outcome)
                .containsExactly(Outcome.DUPLICATE_PAN, Outcome.NOT_PDF, Outcome.CANDIDATE, Outcome.DUPLICATE_PAN);
        assertThat(marked.get(0).outcome().isSkipped()).isTrue();
        assertThat(marked.get(3).name()).isEqualTo("copy/abcde1234f_b.pdf");
    }

    @Test
    @DisplayName("a null or blank name is skipped")
    void blank() {
        assertThat(PartAEntryParser.parse(null).outcome()).isEqualTo(Outcome.UNSAFE_PATH);
        assertThat(PartAEntryParser.parse("  ").outcome()).isEqualTo(Outcome.UNSAFE_PATH);
    }
}
