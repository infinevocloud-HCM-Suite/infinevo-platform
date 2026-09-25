package com.infinevo.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-23.1 — the streaming xlsx writer: memory stays bounded however many rows arrive, and what it writes
 * reads back as it went in.
 *
 * <p>The spec's 10,000-row case is asserted here deterministically, by counting the rows the workbook
 * holds after every row, rather than by sampling heap — a heap measurement depends on the collector and
 * would pass or fail by luck. {@code ExportStreamingIT} runs the same size through the whole path.
 *
 * <p>Reading the file back uses the in-memory {@link XSSFWorkbook}, in a test only. Main code never
 * does — the verification grep in spec section 8 checks.
 */
class XlsxStreamingWriterTest {

    private static final List<ReportColumn> COLUMNS = List.of(
            ReportColumn.text("name", "Name"),
            new ReportColumn("count", "Count", ReportColumn.Type.NUMBER),
            ReportColumn.text("amount", "Amount"));

    @Test
    @DisplayName("10,000 rows: never more than the window in memory, and all of them in the file")
    void memoryIsBoundedByTheWindow() throws Exception {
        int window = 50;
        AtomicInteger mostInMemory = new AtomicInteger();
        XlsxStreamingWriter writer =
                new XlsxStreamingWriter(window, rows -> mostInMemory.accumulateAndGet(rows, Math::max));
        Stream<Map<String, Object>> rows = IntStream.range(0, 10_000).mapToObj(i -> row("E-" + i, i, "1.00"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = writer.write(COLUMNS, rows, out);

        assertThat(count).isEqualTo(10_000);
        assertThat(mostInMemory.get()).as("rows held at once").isLessThanOrEqualTo(window);
        try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(read.getSheetAt(0).getLastRowNum()).isEqualTo(10_000);
        }
    }

    @Test
    @DisplayName("Header in bold row 0; counts are numbers; money and formula-looking text stay text")
    void cellsReadBackAsWritten() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        new XlsxStreamingWriter()
                .write(COLUMNS, Stream.of(row("=cmd|' /C calc'!A0", 7, new BigDecimal("1234.5600"))), out);

        try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = read.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Name");
            assertThat(read.getFontAt(header.getCell(0).getCellStyle().getFontIndex())
                            .getBold())
                    .isTrue();

            Row data = sheet.getRow(1);
            assertThat(data.getCell(0).getCellType())
                    .as("a leading = is a string cell, never a formula")
                    .isEqualTo(CellType.STRING);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("=cmd|' /C calc'!A0");
            assertThat(data.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(data.getCell(1).getNumericCellValue()).isEqualTo(7.0);
            assertThat(data.getCell(2).getCellType())
                    .as("money is text from its BigDecimal, never a double")
                    .isEqualTo(CellType.STRING);
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("1234.5600");
        }
    }

    @Test
    @DisplayName("An empty result is a header-only workbook")
    void emptyResultIsHeaderOnly() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        long count = new XlsxStreamingWriter().write(COLUMNS, Stream.empty(), out);

        assertThat(count).isZero();
        try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(read.getSheetAt(0).getLastRowNum()).isZero();
        }
    }

    @Test
    @DisplayName("The file starts as a zip does - the document store's xlsx check accepts it")
    void outputIsAZipPackage() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new XlsxStreamingWriter().write(COLUMNS, Stream.empty(), out);

        byte[] bytes = out.toByteArray();
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2], bytes[3]}).isEqualTo(new byte[] {'P', 'K', 3, 4});
    }

    private static Map<String, Object> row(String name, int count, Object amount) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("count", count);
        row.put("amount", amount);
        return row;
    }
}
