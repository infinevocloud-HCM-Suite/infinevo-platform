package com.infinevo.core.employeeimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-73.7 §7: header check, date formats, roles split, Y/N — and the file-level refusals. */
class EmployeeImportParserTest {

    private static final String HEADER = String.join(",", EmployeeImportParser.COLUMNS) + "\n";

    @Test
    @DisplayName("the template parses into its one example row")
    void templateParses() {
        List<EmployeeImportRow> rows = EmployeeImportParser.parse(EmployeeImportParser.TEMPLATE);

        assertThat(rows).hasSize(1);
        EmployeeImportRow row = rows.get(0);
        assertThat(row.rowNumber()).isEqualTo(1);
        assertThat(row.employeeNumber()).isEqualTo("EMP-1001");
        assertThat(row.giveAccess()).isEqualTo("Y");
        assertThat(row.roles()).isEqualTo("hr;manager");
    }

    @Test
    @DisplayName("header: case and spaces are forgiven, a missing or reordered column is not")
    void headerCheck() {
        String loose = "Employee_Number, FIRST_NAME,last_name,work_email,mobile,date_of_joining,department,"
                + "designation,location,give_access,roles\r\nE1,A,,,,2026-04-01,,,,,\r\n";
        assertThat(EmployeeImportParser.parse(loose)).hasSize(1);

        String reordered = "first_name,employee_number,last_name,work_email,mobile,date_of_joining,department,"
                + "designation,location,give_access,roles\nA,E1,,,,2026-04-01,,,,,\n";
        assertThatThrownBy(() -> EmployeeImportParser.parse(reordered))
                .isInstanceOf(EmployeeImportFileException.class)
                .hasMessageContaining("header must be exactly");
        assertThatThrownBy(() -> EmployeeImportParser.parse("employee_number,first_name\nE1,A\n"))
                .isInstanceOf(EmployeeImportFileException.class);
    }

    @Test
    @DisplayName("a byte-order mark, blank lines and quoted commas are read as a spreadsheet writes them")
    void quotingAndBlankLines() {
        String csv = "﻿" + HEADER + "\n" + "E1,\"Rao, Jr.\",\"say \"\"hi\"\"\",,,2026-04-01,,,,N,\n\n";

        List<EmployeeImportRow> rows = EmployeeImportParser.parse(csv);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).firstName()).isEqualTo("Rao, Jr.");
        assertThat(rows.get(0).lastName()).isEqualTo("say \"hi\"");
        assertThat(rows.get(0).workEmail()).isNull();
    }

    @Test
    @DisplayName("an empty file, a header alone, a short row, an open quote and 1,001 rows are refused")
    void fileRefusals() {
        assertThatThrownBy(() -> EmployeeImportParser.parse(" ")).hasMessageContaining("empty");
        assertThatThrownBy(() -> EmployeeImportParser.parse(HEADER)).hasMessageContaining("no rows");
        assertThatThrownBy(() -> EmployeeImportParser.parse(HEADER + "E1,A\n"))
                .hasMessageContaining("Row 1 has 2 columns");
        assertThatThrownBy(() -> EmployeeImportParser.parse(HEADER + "E1,\"A,,,,2026-04-01,,,,,\n"))
                .hasMessageContaining("never closed");

        StringBuilder big = new StringBuilder(HEADER);
        for (int i = 0; i <= EmployeeImportParser.MAX_ROWS; i++) {
            big.append("E").append(i).append(",A,,,,2026-04-01,,,,,\n");
        }
        assertThatThrownBy(() -> EmployeeImportParser.parse(big.toString())).hasMessageContaining("more than 1000");
    }

    @Test
    @DisplayName("dates: ISO, dd/MM/yyyy and dd-MM-yyyy; impossible dates and other shapes are not dates")
    void dateFormats() {
        assertThat(EmployeeImportParser.parseDate("2026-04-01")).contains(LocalDate.of(2026, 4, 1));
        assertThat(EmployeeImportParser.parseDate("01/04/2026")).contains(LocalDate.of(2026, 4, 1));
        assertThat(EmployeeImportParser.parseDate("01-04-2026")).contains(LocalDate.of(2026, 4, 1));
        assertThat(EmployeeImportParser.parseDate("31/02/2026")).isEmpty();
        assertThat(EmployeeImportParser.parseDate("04/01/26")).isEmpty();
        assertThat(EmployeeImportParser.parseDate("April 1")).isEmpty();
        assertThat(EmployeeImportParser.parseDate(null)).isEmpty();
    }

    @Test
    @DisplayName("Y/N: Y, yes, N, no and blank; anything else is not an answer")
    void yesNo() {
        assertThat(EmployeeImportParser.parseYesNo("Y")).contains(true);
        assertThat(EmployeeImportParser.parseYesNo("yes")).contains(true);
        assertThat(EmployeeImportParser.parseYesNo("n")).contains(false);
        assertThat(EmployeeImportParser.parseYesNo("No")).contains(false);
        assertThat(EmployeeImportParser.parseYesNo(null)).contains(false);
        assertThat(EmployeeImportParser.parseYesNo("maybe")).isEmpty();
    }

    @Test
    @DisplayName("roles: split on semicolons, trimmed, lower-cased, repeats dropped; employee alone grants none")
    void rolesSplit() {
        assertThat(EmployeeImportParser.splitRoles(" HR ; manager;;hr ")).containsExactly("hr", "manager");
        assertThat(EmployeeImportParser.splitRoles(null)).isEmpty();
        assertThat(EmployeeImportServiceImpl.grantedRoleCodes("employee;hr")).containsExactly("hr");
        assertThat(EmployeeImportServiceImpl.grantedRoleCodes("employee")).isEmpty();
    }
}
