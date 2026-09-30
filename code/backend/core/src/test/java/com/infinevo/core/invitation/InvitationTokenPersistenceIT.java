package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.notification.Notification;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The plaintext invitation token is never persisted and never logged (W-24.2, spec §6 and §7; review B-5).
 *
 * <p>Runs the real notification composer, so the email row is what {@code W-20.2} would send. The email
 * row is the outbox: it is the only way the text reaches the delivery worker, so it holds the link —
 * token included — until the email is sent or given up on, and is redacted then. Every invitation column,
 * the role join, and the delivered notification row are searched for the token; so is every log line.
 */
@SpringBootTest(
        classes = InvitationTokenPersistenceIT.App.class,
        properties = "invitation.link.base-url=" + InvitationServiceTest.LINK_BASE)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class InvitationTokenPersistenceIT extends AbstractIntegrationTest {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([0-9a-f]{64})");

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenant;
    private UUID adminUserId;
    private ListAppender<ILoggingEvent> logs;
    private Logger infinevoLogger;
    private Level previousLevel;

    /** The authz test database has no notification tables; add the shipped scripts once. */
    @BeforeAll
    static void notificationTables() throws Exception {
        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            if (!exists(conn, "core.notification")) {
                for (String script : new String[] {
                    "core/V038__notification_template.sql",
                    "core/V039__notification.sql",
                    // W-20.2's delivery columns (attempt_count, sent_at ...) ride on the reminder script
                    "core/V093__reminder_rule.sql",
                    "core/V096__scheduled_report_notification.sql"
                }) {
                    try (InputStream is = InvitationTokenPersistenceIT.class
                                    .getClassLoader()
                                    .getResourceAsStream("db/migration/" + script);
                            Statement st = conn.createStatement()) {
                        st.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("TokenPersistence " + UUID.randomUUID());
        adminUserId = UUID.randomUUID();

        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        infinevoLogger = (Logger) LoggerFactory.getLogger("com.infinevo");
        previousLevel = infinevoLogger.getLevel();
        infinevoLogger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);

        TenantContext.set(tenant);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        infinevoLogger.setLevel(previousLevel);
        TenantContext.clear();
    }

    @Test
    @DisplayName("user invitation: token in no invitation column, not in the delivered email row, not in any log")
    void userInvitationTokenIsNeverPersistedOrLogged() throws SQLException {
        UUID roleId = AuthzTestSchema.roleId(tenant, "hr");
        UserInvitationResponse created = invitationService.createUserInvitation(
                new UserInvitationRequest("persist-" + UUID.randomUUID() + "@example.com", Set.of(roleId)),
                adminUserId);

        UUID notificationId = notificationFor("USER_INVITATION");
        String token = emailedToken(notificationId);
        assertThat(InvitationTokenUtils.hashToken(token))
                .isEqualTo(ownerString("SELECT token_hash FROM core.user_invitation WHERE id = ?", created.id()));

        assertThat(rowsContaining("user_invitation", token)).isZero();
        assertThat(rowsContaining("user_invitation_role", token)).isZero();

        deliver(notificationId);
        assertThat(rowsContaining("notification", token)).isZero();
        assertThat(ownerString("SELECT body FROM core.notification WHERE id = ?", notificationId))
                .contains(InvitationServiceTest.LINK_BASE + "?token=[redacted]");

        assertNotLogged(token);
    }

    @Test
    @DisplayName("employee invitation: token in no invitation column, not in the delivered email row, not in any log")
    void employeeInvitationTokenIsNeverPersistedOrLogged() throws SQLException {
        UUID employeeId = AuthzTestSchema.insertEmployee(tenant, "TOK-" + UUID.randomUUID(), "Tara");
        ownerUpdate(
                "UPDATE core.employee SET work_email = 'tara-" + UUID.randomUUID() + "@example.com' WHERE id = ?",
                employeeId);

        EmployeeInvitationResponse created =
                invitationService.createEmployeeInvitation(new EmployeeInvitationRequest(employeeId), adminUserId);

        UUID notificationId = notificationFor("EMPLOYEE_INVITATION");
        String token = emailedToken(notificationId);
        assertThat(InvitationTokenUtils.hashToken(token))
                .isEqualTo(ownerString("SELECT token_hash FROM core.employee_invitation WHERE id = ?", created.id()));

        assertThat(rowsContaining("employee_invitation", token)).isZero();

        deliver(notificationId);
        assertThat(rowsContaining("notification", token)).isZero();

        assertNotLogged(token);
    }

    /** What the delivery worker does once the provider accepts the email. */
    private void deliver(UUID notificationId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Notification n = notificationRepository
                    .findByIdAndTenantId(notificationId, tenant)
                    .orElseThrow();
            n.markSent(Instant.now(), "worker");
            notificationRepository.save(n);
        });
    }

    private UUID notificationFor(String event) throws SQLException {
        return UUID.fromString(ownerString(
                "SELECT id::text FROM core.notification WHERE tenant_id = ? AND event = '" + event
                        + "' AND channel = 'EMAIL'",
                tenant));
    }

    private static String emailedToken(UUID notificationId) throws SQLException {
        String body = ownerString("SELECT body FROM core.notification WHERE id = ?", notificationId);
        assertThat(body).contains(InvitationServiceTest.LINK_BASE + "?token=").doesNotContain("/api/v1/");
        Matcher m = TOKEN_IN_LINK.matcher(body);
        assertThat(m.find()).as("the queued email carries the link").isTrue();
        return m.group(1);
    }

    private void assertNotLogged(String token) {
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list)
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(token));
    }

    /** Rows of a core table, any column, whose text contains {@code needle}. Read as the owner, past RLS. */
    private static long rowsContaining(String table, String needle) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core." + table + " t WHERE row_to_json(t)::text LIKE ?")) {
            ps.setString(1, "%" + needle + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String ownerString(String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void ownerUpdate(String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            ps.executeUpdate();
        }
    }

    private static boolean exists(Connection conn, String relation) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, relation);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    /** {@link InvitationTestApp} plus the notification composer. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.shared.authz"
            },
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
            })
    @EntityScan(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.shared.identity"
            })
    @EnableJpaRepositories(
            basePackages = {
                "com.infinevo.core.invitation",
                "com.infinevo.core.notification",
                "com.infinevo.core.authz",
                "com.infinevo.core.employee",
                "com.infinevo.core.org",
                "com.infinevo.core.tenant",
                "com.infinevo.shared.identity"
            })
    @Import(UserProfileSyncService.class)
    static class App {}
}
