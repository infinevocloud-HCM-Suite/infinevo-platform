package com.infinevo.core.report;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * xlsx through POI's streaming workbook, {@link SXSSFWorkbook} (W-23.1).
 *
 * <p>The workbook keeps {@link #WINDOW} rows in memory and writes older ones to a temporary file as
 * new ones arrive. The frozen exports use the in-memory workbook
 * ({@code legacy/Payroll-Bend-SBoot/.../controller/payruns/PayRunReportController.java:29,77}), which
 * holds the whole file in heap — a 5,000-employee export is a heap spike on a container with a fixed
 * memory limit. That is the whole difference, and the verification grep in spec section 8 checks the
 * in-memory class is not used here.
 *
 * <p>Every value is written as a string cell except {@link ReportColumn.Type#NUMBER} integers. A string
 * cell is never evaluated, so a value beginning {@code =} cannot become a formula; and a money value,
 * written as text from its {@code BigDecimal}, never passes through a double.
 */
public class XlsxStreamingWriter implements ReportWriter {

    /** Rows held in memory at once; everything older is on disk. */
    static final int WINDOW = 100;

    /** Excel's own ceiling on the rows one sheet holds. */
    static final int MAX_ROWS = 1_048_576;

    private final int window;
    private final IntConsumer rowsInMemoryProbe;

    public XlsxStreamingWriter() {
        this(WINDOW, rows -> {});
    }

    /** For tests: a smaller window, and a probe told how many rows are in memory after each row. */
    XlsxStreamingWriter(int window, IntConsumer rowsInMemoryProbe) {
        this.window = window;
        this.rowsInMemoryProbe = rowsInMemoryProbe;
    }

    @Override
    public long write(List<ReportColumn> columns, Stream<Map<String, Object>> rows, OutputStream out)
            throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(window);
        try {
            SXSSFSheet sheet = workbook.createSheet("Export");
            writeHeader(workbook, sheet, columns);

            long count = 0;
            int rowIndex = 1;
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                if (rowIndex >= MAX_ROWS) {
                    throw new IllegalStateException(
                            "An xlsx sheet holds at most " + MAX_ROWS + " rows; export this as CSV instead");
                }
                Map<String, Object> values = iterator.next();
                Row row = sheet.createRow(rowIndex++);
                for (int c = 0; c < columns.size(); c++) {
                    ReportColumn column = columns.get(c);
                    writeCell(row.createCell(c), column, values.get(column.name()));
                }
                count++;
                rowsInMemoryProbe.accept(sheet.getLastRowNum() - sheet.getLastFlushedRowNum());
            }
            workbook.write(out);
            return count;
        } finally {
            workbook.close();
            // The temporary files the window flushed to. close() does not remove them.
            workbook.dispose();
        }
    }

    private static void writeHeader(SXSSFWorkbook workbook, SXSSFSheet sheet, List<ReportColumn> columns) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle header = workbook.createCellStyle();
        header.setFont(bold);
        Row row = sheet.createRow(0);
        for (int c = 0; c < columns.size(); c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(columns.get(c).label());
            cell.setCellStyle(header);
        }
    }

    private static void writeCell(Cell cell, ReportColumn column, Object value) {
        if (value == null) {
            return;
        }
        if (column.type() == ReportColumn.Type.NUMBER && (value instanceof Integer || value instanceof Long)) {
            cell.setCellValue(((Number) value).longValue());
            return;
        }
        cell.setCellValue(ReportWriter.text(value));
    }
}
