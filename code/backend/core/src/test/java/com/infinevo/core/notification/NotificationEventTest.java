package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-20.1 spec section 7 — the enum holds exactly the contracts' list, and every event has a seeded
 * default template on both channels. Read from the shipped migration itself, so the seed and the code
 * cannot drift apart unnoticed.
 */
class NotificationEventTest {

    private static final String SEED = "db/migration/core/V038__notification_template.sql";

    /** One seeded row: ('EVENT', 'CHANNEL', NULL | 'subject', 'body'). SQL doubles a quote inside a string. */
    private static final Pattern ROW =
            Pattern.compile("\\('([A-Z_]+)', '(IN_APP|EMAIL)', (NULL|'(?:[^']|'')*'), '((?:[^']|'')*)'\\)");

    private static String sql;
    private static List<String[]> rows;

    @BeforeAll
    static void readSeed() throws Exception {
        try (InputStream in = NotificationEventTest.class.getClassLoader().getResourceAsStream(SEED)) {
            assertThat(in).as(SEED + " on the test classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        rows = new java.util.ArrayList<>();
        Matcher matcher = ROW.matcher(sql);
        while (matcher.find()) {
            rows.add(new String[] {matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)});
        }
    }

    @Test
    @DisplayName("The enum is exactly the events contracts section 5 row 15 names")
    void enumIsTheContractList() {
        assertThat(Arrays.stream(NotificationEvent.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        "POI_REMINDER",
                        "POI_SUBMITTED",
                        "IT_DECLARATION_REMINDER",
                        "IT_DECLARATION_LOCK",
                        "IT_DECLARATION_RELEASE",
                        "PAYSLIP_READY",
                        "USER_INVITATION",
                        "EMPLOYEE_INVITATION",
                        "CREDENTIALS",
                        "LEAVE_APPLIED",
                        "LEAVE_APPROVED",
                        "LEAVE_REJECTED",
                        "LEAVE_CANCELLED",
                        "APPROVAL_PENDING",
                        "APPROVAL_DECIDED",
                        "TIMESHEET_REMINDER");
    }

    @Test
    @DisplayName("Every event has exactly one seeded default on each channel")
    void everyEventIsSeededOnBothChannels() {
        Map<String, Integer> seen = new HashMap<>();
        for (String[] row : rows) {
            seen.merge(row[0] + "/" + row[1], 1, Integer::sum);
        }
        Set<String> expected = new HashSet<>();
        for (NotificationEvent event : NotificationEvent.values()) {
            for (Channel channel : Channel.values()) {
                expected.add(event + "/" + channel);
            }
        }
        assertThat(seen.keySet()).isEqualTo(expected);
        assertThat(seen.values()).allMatch(count -> count == 1);
    }

    @Test
    @DisplayName("A seeded template uses only placeholders its event supplies, and every email has a subject")
    void seededTemplatesUseOnlyTheirEventsPlaceholders() {
        for (String[] row : rows) {
            NotificationEvent event = NotificationEvent.valueOf(row[0]);
            Set<String> used = new HashSet<>(TemplateRenderer.placeholdersIn(row[3]));
            if (!"NULL".equals(row[2])) {
                used.addAll(TemplateRenderer.placeholdersIn(row[2]));
            }
            assertThat(event.placeholders())
                    .as("%s on %s uses %s", row[0], row[1], used)
                    .containsAll(used);
            assertThat(TemplateRenderer.hasMalformedPlaceholder(row[3])).isFalse();
            if ("EMAIL".equals(row[1])) {
                assertThat(row[2]).as("%s email subject", row[0]).isNotEqualTo("NULL");
            }
        }
    }

    @Test
    @DisplayName("The table's CHECK lists the same events as the enum")
    void checkConstraintMatchesTheEnum() {
        Matcher check =
                Pattern.compile("event IN \\(([^)]*)\\)", Pattern.DOTALL).matcher(sql);
        assertThat(check.find()).isTrue();
        Set<String> listed = Arrays.stream(check.group(1).split(","))
                .map(s -> s.trim().replace("'", ""))
                .collect(Collectors.toSet());
        assertThat(listed)
                .isEqualTo(Arrays.stream(NotificationEvent.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
    }
}
