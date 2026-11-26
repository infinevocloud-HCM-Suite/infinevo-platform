package com.infinevo.payroll.compliance.form24q;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Form 138 Q1–Q3 text file (W-89) against Protean's FVU 1.2 layout: FH 18, BH 72, CD 30, DD 45 fields, CR LF,
 * ASCII, and the control totals of the format's section 5.3. Field indexes below are zero-based, so BH[46] is the
 * layout's field 47.
 */
class Form138FileTest {

    private static final LocalDate CREATED = LocalDate.of(2026, 7, 10);
    private static final UUID C1 = UUID.randomUUID();
    private static final UUID C2 = UUID.randomUUID();

    private Annexure1PreviewResponse preview;
    private Form138Deductor deductor;

    @BeforeEach
    void setUp() {
        ChallanDetailRecord cd1 = new ChallanDetailRecord(
                C1,
                1,
                "0210045",
                "00123",
                LocalDate.of(2026, 6, 7),
                new BigDecimal("3000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                new BigDecimal("50.00"),
                new BigDecimal("200.00"),
                new BigDecimal("3470.00"),
                "200",
                null,
                2);
        ChallanDetailRecord cd2 = new ChallanDetailRecord(
                C2,
                2,
                "6390009",
                "7",
                LocalDate.of(2026, 7, 6),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("40.00"),
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                new BigDecimal("1040.00"),
                "200",
                null,
                1);
        List<DeducteeDetailRecord> dds = List.of(
                dd(C1, 1, "José^Kumar", "ABCDE1234F", "80000.00", "2000.00", null),
                dd(C1, 1, "Priya Nair", "PANNOTAVBL", "6100.00", "1220.00", "C"),
                dd(C2, 2, "Ravi Shah", "BCDEF2345G", "52000.50", "1040.00", null),
                // A row no challan covers is never written to the file.
                dd(null, 0, "Uncovered Person", "CDEFG3456H", "10000.00", "500.00", null));
        preview = new Annexure1PreviewResponse(
                "2026-2027",
                "Q2",
                "2027-2028",
                "BLRA12345B",
                "AAACA1234A",
                "Acme",
                2,
                new BigDecimal("4510.00"),
                3,
                new BigDecimal("138100.50"),
                new BigDecimal("4260.00"),
                new BigDecimal("4260.00"),
                true,
                BigDecimal.ZERO.setScale(2),
                1,
                List.of(),
                List.of(cd1, cd2),
                dds,
                List.of());
        deductor = new Form138Deductor(
                "BLRA12345B",
                "AAACA1234A",
                "Acme Café ^ Private Limited",
                Form138DeductorResolver.addressLines("12, 100 Feet Road", "Indiranagar", "Bengaluru"),
                15,
                "560038",
                "tax@acme.example",
                "9876543210",
                "K",
                "29AAACA1234A1Z5",
                "Asha Rao",
                "Director",
                "ABCPR1234K",
                "asha@acme.example",
                "9876500000");
    }

    private static DeducteeDetailRecord dd(
            UUID challan, int serial, String name, String pan, String gross, String tax, String reason) {
        BigDecimal t = new BigDecimal(tax);
        boolean covered = challan != null;
        return new DeducteeDetailRecord(
                UUID.randomUUID(),
                "E-" + name.length(),
                name,
                pan,
                null,
                "92B",
                LocalDate.of(2026, 7, 31),
                new BigDecimal(gross),
                t,
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2),
                t,
                covered ? t : BigDecimal.ZERO.setScale(2),
                LocalDate.of(2026, 7, 31),
                reason,
                null,
                challan,
                serial);
    }

    private byte[] write(String previousToken) {
        return Form138File.write(preview, deductor, previousToken, CREATED);
    }

    private static List<String[]> parse(byte[] file) {
        String text = new String(file, StandardCharsets.US_ASCII);
        assertThat(text).endsWith("\r\n");
        List<String[]> out = new ArrayList<>();
        for (String line : text.substring(0, text.length() - 2).split("\r\n", -1)) {
            out.add(line.split("\\^", -1));
        }
        return out;
    }

    private static List<String[]> ofType(List<String[]> recs, String type) {
        return recs.stream().filter(r -> r[1].equals(type)).toList();
    }

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    @Test
    @DisplayName("Every line ends CR LF, the file is pure ASCII, and no bare LF or CR appears")
    void lineEndingsAndAscii() {
        byte[] file = write(null);
        for (byte b : file) {
            assertThat(b & 0xFF).isLessThan(0x80);
        }
        String text = new String(file, StandardCharsets.US_ASCII);
        assertThat(text.replace("\r\n", "")).doesNotContain("\r").doesNotContain("\n");
        assertThat(text.split("\r\n", -1)).hasSize(7 + 1); // 7 records and the empty tail after the last CR LF
    }

    @Test
    @DisplayName("Field counts are exactly FH 18, BH 72, CD 30, DD 45; line numbers run 1..n in order")
    void fieldCountsAndLineNumbers() {
        List<String[]> recs = parse(write(null));
        assertThat(recs).extracting(r -> r[1]).containsExactly("FH", "BH", "CD", "DD", "DD", "CD", "DD");
        for (int i = 0; i < recs.size(); i++) {
            String[] r = recs.get(i);
            assertThat(r[0]).isEqualTo(String.valueOf(i + 1));
            int expected =
                    switch (r[1]) {
                        case "FH" -> 18;
                        case "BH" -> 72;
                        case "CD" -> 30;
                        default -> 45;
                    };
            assertThat(r).as("%s fields", r[1]).hasSize(expected);
        }
    }

    @Test
    @DisplayName("File header: SL1, R, creation date, sequence 1, uploader D, TAN, 1 batch, utility name")
    void fileHeader() {
        String[] fh = parse(write(null)).get(0);
        assertThat(fh[2]).isEqualTo("SL1");
        assertThat(fh[3]).isEqualTo("R");
        assertThat(fh[4]).isEqualTo("10072026");
        assertThat(fh[5]).isEqualTo("1");
        assertThat(fh[6]).isEqualTo("D");
        assertThat(fh[7]).isEqualTo("BLRA12345B");
        assertThat(fh[8]).isEqualTo("1");
        assertThat(fh[9]).isEqualTo(Form138File.RPU_NAME).isNotBlank();
        for (int i = 10; i < 18; i++) {
            assertThat(fh[i]).as("FH[%d] not applicable", i).isEmpty();
        }
    }

    @Test
    @DisplayName("Batch header: challan count, form 138, AY 202728, tax year 202627, quarter, total, employer")
    void batchHeader() {
        List<String[]> recs = parse(write(null));
        String[] bh = recs.get(1);
        List<String[]> cds = ofType(recs, "CD");
        assertThat(bh[2]).isEqualTo("1");
        assertThat(bh[3]).isEqualTo("2");
        assertThat(bh[4]).isEqualTo("138");
        assertThat(bh[12]).isEqualTo("BLRA12345B");
        assertThat(bh[14]).isEqualTo("AAACA1234A");
        assertThat(bh[15]).isEqualTo("202728");
        assertThat(bh[16]).isEqualTo("202627");
        assertThat(bh[17]).isEqualTo("Q2");
        assertThat(bh[19]).isEqualTo("INDIA");
        assertThat(bh[20]).isNotBlank();
        assertThat(bh[25]).isEqualTo("15");
        assertThat(bh[26]).isEqualTo("560038");
        assertThat(bh[28]).isEqualTo("91");
        assertThat(bh[31]).isEqualTo("K");
        assertThat(bh[32]).isEqualTo("Asha Rao");
        assertThat(bh[33]).isEqualTo("Director");
        assertThat(bh[34]).isEqualTo(bh[20]);
        assertThat(bh[39]).isEqualTo("15");
        assertThat(bh[58]).isEqualTo("ABCPR1234K");
        assertThat(bh[68]).isEqualTo("29AAACA1234A1Z5");

        BigDecimal sumCd11 = cds.stream().map(c -> n(c[11])).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(n(bh[46])).isEqualByComparingTo(sumCd11);
        assertThat(bh[46]).isEqualTo("4510.00");

        for (int i = 20; i < 25; i++) {
            assertThat(bh[i].length()).as("BH[%d] address length", i).isLessThanOrEqualTo(25);
        }
    }

    @Test
    @DisplayName("Without a previous token BH[51] is N and BH[8] empty; with one, Y and the token")
    void previousStatement() {
        String[] first = parse(write(null)).get(1);
        assertThat(first[51]).isEqualTo("N");
        assertThat(first[8]).isEmpty();

        String[] later = parse(write("123456789012345")).get(1);
        assertThat(later[51]).isEqualTo("Y");
        assertThat(later[8]).isEqualTo("123456789012345");
    }

    @Test
    @DisplayName("Challan detail: counts, whole-rupee amounts with .00, and the section 5.3 control totals")
    void challanDetail() {
        List<String[]> recs = parse(write(null));
        // CD then its own DDs: index of CD1 is 2, its DDs 3 and 4; CD2 is 5, its DD 6.
        String[] cd1 = recs.get(2);
        String[] cd2 = recs.get(5);
        List<String[]> dd1 = recs.subList(3, 5);
        List<String[]> dd2 = recs.subList(6, 7);

        for (Object[] pair : new Object[][] {{cd1, dd1}, {cd2, dd2}}) {
            String[] cd = (String[]) pair[0];
            @SuppressWarnings("unchecked")
            List<String[]> dds = (List<String[]>) pair[1];

            assertThat(cd[2]).isEqualTo("1");
            assertThat(cd[4]).isEqualTo(String.valueOf(dds.size()));
            assertThat(cd[5]).isEqualTo("N");
            for (int i = 7; i <= 11; i++) {
                assertThat(cd[i]).as("CD[%d]", i).matches("^[0-9]+\\.00$");
            }
            assertThat(n(cd[11]))
                    .isEqualByComparingTo(n(cd[7]).add(n(cd[8])).add(n(cd[9])).add(n(cd[10])));
            assertThat(n(cd[20]))
                    .isEqualByComparingTo(dds.stream().map(d -> n(d[24])).reduce(BigDecimal.ZERO, BigDecimal::add));
            assertThat(n(cd[21]))
                    .isEqualByComparingTo(dds.stream().map(d -> n(d[23])).reduce(BigDecimal.ZERO, BigDecimal::add));
            assertThat(cd[23]).isEqualTo(cd[8]);
            assertThat(cd[24]).isEqualTo(cd[10]);
            // Mode C: the challan covers its deductees' deposits plus interest, others and fee.
            assertThat(n(cd[11]))
                    .isGreaterThanOrEqualTo(
                            n(cd[20]).add(n(cd[23])).add(n(cd[24])).add(n(cd[9])));
            assertThat(cd[12]).isEqualTo("C");
            assertThat(cd[22]).isEqualTo("200");

            for (String[] dd : dds) {
                assertThat(dd[4]).isEqualTo(cd[3]);
            }
        }
        assertThat(cd1[3]).isEqualTo("1");
        assertThat(cd2[3]).isEqualTo("2");
        // Tax column B is tax + surcharge + cess: Form 138 has no separate surcharge or cess.
        assertThat(cd1[7]).isEqualTo("3220.00");
        assertThat(cd1[8]).isEqualTo("50.00");
        assertThat(cd1[9]).isEqualTo("200.00");
        assertThat(cd1[11]).isEqualTo("3470.00");
        assertThat(cd1[14]).isEqualTo("0210045");
        assertThat(cd1[16]).isEqualTo("123");
        assertThat(cd1[18]).isEqualTo("07062026");
        assertThat(cd1[20]).isEqualTo("3220.00");
    }

    @Test
    @DisplayName("Deductee detail: running number, challan reference, section 1002, deposited Y, deducted = deposited")
    void deducteeDetail() {
        List<String[]> recs = parse(write(null));
        List<String[]> dds = ofType(recs, "DD");
        assertThat(dds).hasSize(3);
        assertThat(dds).extracting(d -> d[8]).doesNotContain("Uncovered Person");
        for (String[] dd : dds) {
            assertThat(dd[2]).isEqualTo("1");
            assertThat(dd[5]).isEqualTo("O");
            assertThat(dd[14]).isEqualTo("1002");
            assertThat(dd[17]).isEqualTo("Y");
            assertThat(dd[18]).isEqualTo("31072026");
            assertThat(n(dd[19])).isPositive();
            assertThat(dd[19]).matches("^[0-9]+\\.[0-9]{2}$");
            assertThat(dd[23]).isEqualTo(dd[24]);
            assertThat(dd[26]).isEqualTo("31072026");
            assertThat(dd[28]).isNotEmpty();
        }
        assertThat(dds.get(0)[3]).isEqualTo("1");
        assertThat(dds.get(1)[3]).isEqualTo("2");
        assertThat(dds.get(1)[7]).isEqualTo("PANNOTAVBL");
        assertThat(dds.get(1)[31]).isEqualTo("C");
        assertThat(dds.get(0)[31]).isEmpty();
        assertThat(dds.get(2)[19]).isEqualTo("52000.50");
        // Date of deposit is the challan's tender date.
        assertThat(dds.get(2)[28]).isEqualTo("06072026");
    }

    @Test
    @DisplayName("A name with ^ and an accented letter comes out ASCII without ^")
    void namesAreAsciiWithoutCaret() {
        List<String[]> recs = parse(write(null));
        String[] dd = ofType(recs, "DD").get(0);
        assertThat(dd[8]).isEqualTo("Jose Kumar");
        assertThat(recs.get(1)[18]).isEqualTo("Acme Cafe Private Limited");
        assertThat(Form138File.text("Ünïcödé^ name", 75)).isEqualTo("Unicode name");
        assertThat(Form138File.text("x".repeat(80), 75)).hasSize(75);
    }

    @Test
    @DisplayName("A challan amount with paise cannot be written")
    void paiseOnAChallanIsRefused() {
        assertThatThrownBy(() -> Form138File.whole(new BigDecimal("10.50"))).isInstanceOf(IllegalStateException.class);
        assertThat(Form138File.whole(new BigDecimal("10"))).isEqualTo("10.00");
        assertThat(Form138File.money(new BigDecimal("10.5"))).isEqualTo("10.50");
    }

    // ---------------------------------------------------------------- states and address

    @Test
    void stateCodes() {
        assertThat(Form138States.code(null, "Karnataka")).contains(15);
        assertThat(Form138States.code("KA", null)).contains(15);
        assertThat(Form138States.code("IN-MH", null)).contains(19);
        assertThat(Form138States.code(null, "Delhi")).contains(9);
        assertThat(Form138States.code(null, "Tamil Nadu")).contains(29);
        assertThat(Form138States.code(null, "Jammu & Kashmir")).contains(14);
        assertThat(Form138States.code("15", null)).contains(15);
        assertThat(Form138States.code("8", null)).isEmpty();
        assertThat(Form138States.code("XX", "Karnataka")).contains(15);
        assertThat(Form138States.code(null, "Atlantis")).isEmpty();
        assertThat(Form138States.code(" ", null)).isEmpty();
    }

    @Test
    @DisplayName("Address flows into at most 5 lines of at most 25 characters, words kept whole where they fit")
    void addressLines() {
        List<String> short3 = Form138DeductorResolver.addressLines("12, 100 Feet Road", "Indiranagar", "Bengaluru");
        assertThat(short3).containsExactly("12, 100 Feet Road", "Indiranagar Bengaluru");

        List<String> long1 = Form138DeductorResolver.addressLines(
                "Flat 1203 Tower B Prestige Shantiniketan Whitefield Main Road Hoodi Village Krishnarajapuram"
                        + " Hobli Bengaluru East Taluk",
                "Bengaluru Urban District Karnataka");
        assertThat(long1).hasSizeLessThanOrEqualTo(5).allSatisfy(l -> assertThat(l.length())
                .isBetween(1, 25));

        List<String> oneLongWord = Form138DeductorResolver.addressLines("Abcdefghijklmnopqrstuvwxyz0123");
        assertThat(oneLongWord).containsExactly("Abcdefghijklmnopqrstuvwxy", "z0123");

        assertThat(Form138DeductorResolver.addressLines(null, " ", "")).isEmpty();
        assertThat(Form138DeductorResolver.addressLines("Café Street")).containsExactly("Cafe Street");
    }
}
