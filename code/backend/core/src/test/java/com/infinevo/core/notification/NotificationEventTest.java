package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * W-20.1 spec section 7 — the enum holds exactly the contracts' list, and every event has a seeded
 * default template on both channels. Read from the shipped migrations themselves, so the seed and the
 * code cannot drift apart unnoticed.
 *
 * <p>A new event is a migration that replaces the seed function and widens the {@code CHECK} (V038 did
 * the first sixteen, V096 added {@code SCHEDULED_REPORT}), so the test reads whichever core script
 * defined each of them last, by version.
 */
class NotificationEventTest {

    private static final String CORE_SCRIPTS = "classpath*:db/migration/core/V*__*.sql";
    private static final Pattern VERSION = Pattern.compile("V(\\d+)__");

    /** One seeded row: ('EVENT', 'CHANNEL', NULL | 'subject', 'body'). SQL doubles a quote inside a string. */
    private static final Pattern ROW =
            Pattern.compile("\\('([A-Z_]+)', '(IN_APP|EMAIL)', (NULL|'(?:[^']|'')*'), '((?:[^']|'')*)'\\)");

    /** The latest script that defines core.seed_notification_templates. */
    private static String seedSql;
    /** The latest script that sets the event CHECK on core.notification_template. */
    private static String checkSql;

    private static List<String[]> rows;

    @BeforeAll
    static void readSeed() throws Exception {
        List<Resource> scripts = Arrays.stream(new PathMatchingResourcePatternResolver().getResources(CORE_SCRIPTS))
                .sorted(Comparator.comparingInt(NotificationEventTest::version))
                .toList();
        for (Resource script : scripts) {
            String text = new String(script.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (text.contains("FUNCTION core.seed_notification_templates")) {
                seedSql = text;
            }
            if (text.contains("notification_template") && text.contains("event IN (")) {
                checkSql = text;
            }
        }
        assertThat(seedSql)
                .as("a core script defining core.seed_notification_templates")
                .isNotNull();
        assertThat(checkSql).as("a core script setting the event CHECK").isNotNull();

        rows = new ArrayList<>();
        Matcher matcher = ROW.matcher(seedSql);
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
                        "TIMESHEET_REMINDER",
                        // Not in contracts section 5 row 15: W-23.2 adds it for scheduled reports (V096).
                        "SCHEDULED_REPORT");
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

    private static int version(Resource script) {
        Matcher m = VERSION.matcher(String.valueOf(script.getFilename()));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    @Test
    @DisplayName("The table's CHECK lists the same events as the enum")
    void checkConstraintMatchesTheEnum() {
        Matcher check =
                Pattern.compile("event IN \\(([^)]*)\\)", Pattern.DOTALL).matcher(checkSql);
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
