package com.infinevo.hrms.project;

import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.cache.RedisConfig;
import com.infinevo.shared.entitlement.EntitlementSource;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.identity.UserProfileSyncService;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot test application for the HRMS integration tests (W-41, W-40.1).
 *
 * <p>Following the pattern of {@code PayInputTestApp} and {@code PayrollTestApp}, this context
 * provides an {@link EmployeeService} test stand-in rather than depending on {@code core.employee}'s
 * full JPA stack, enforcing strict cross-module isolation.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {
            "com.infinevo.hrms.project",
            "com.infinevo.hrms.attendance",
            "com.infinevo.hrms.timesheet",
            "com.infinevo.hrms.portal",
            "com.infinevo.hrms.navigation",
            "com.infinevo.hrms.overtime",
            "com.infinevo.hrms.dashboard",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.overtime",
            "com.infinevo.core.payinput"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
// core.employee and core.org entities only, no repositories and no beans (W-43.2): the late-timesheet query joins
// Employee and ReportingLine to hrms's own tables, and a JPQL query can name only entities the context knows.
// The real application scans all of com.infinevo; this test application's scan is narrower on purpose.
// core.overtime and core.payinput are real (W-40.6): the overtime outcome handler calls the real OvertimeService, which
// posts to the real pay input ledger, so OvertimeRequestFlowIT counts real core.pay_input rows. OvertimeServiceImpl
// checks the employee through the EmployeeRepository stand-in below, which those tests stub.
@EntityScan(
        basePackages = {
            "com.infinevo.hrms.project",
            "com.infinevo.hrms.attendance",
            "com.infinevo.hrms.timesheet",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.overtime",
            "com.infinevo.core.payinput",
            "com.infinevo.shared.audit",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.hrms.project",
            "com.infinevo.hrms.attendance",
            "com.infinevo.hrms.timesheet",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.core.overtime",
            "com.infinevo.core.payinput",
            "com.infinevo.shared.audit",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class, AuditWriter.class, AuditIntegratorConfig.class})
public class HrmsTestApp {

    public static final ThreadLocal<EmployeeResponse> CURRENT_EMPLOYEE = new ThreadLocal<>();
    public static final Map<UUID, EmployeeResponse> EXTRA_EMPLOYEES = new ConcurrentHashMap<>();

    /** Modules each test tenant holds; a tenant absent from the map holds none, as in production. */
    public static final Map<UUID, Set<PlatformModule>> ENTITLED = new ConcurrentHashMap<>();

    public static final ThreadLocal<java.time.Clock> CUSTOM_CLOCK = new ThreadLocal<>();

    @Bean
    public java.time.Clock clock() {
        return new java.time.Clock() {
            @Override
            public java.time.ZoneId getZone() {
                java.time.Clock c = CUSTOM_CLOCK.get();
                return c != null ? c.getZone() : java.time.ZoneOffset.UTC;
            }

            @Override
            public java.time.Clock withZone(java.time.ZoneId zone) {
                java.time.Clock c = CUSTOM_CLOCK.get();
                return (c != null ? c : java.time.Clock.systemUTC()).withZone(zone);
            }

            @Override
            public Instant instant() {
                java.time.Clock c = CUSTOM_CLOCK.get();
                return c != null ? c.instant() : Instant.now();
            }
        };
    }

    @Bean
    public com.infinevo.core.tenant.TenantClock tenantClock(DataSource dataSource, java.time.Clock clock) {
        return new com.infinevo.core.tenant.TenantClock(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource), clock);
    }

    @Bean
    public com.infinevo.core.attendance.AttendanceService attendanceService(
            DataSource dataSource, com.infinevo.core.tenant.TenantClock tenantClock) {
        return new com.infinevo.core.attendance.AttendanceService() {
            @Override
            public java.util.List<com.infinevo.core.attendance.AttendanceResponse> upsert(
                    java.util.List<com.infinevo.core.attendance.AttendanceEntry> entries) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.List<com.infinevo.core.attendance.AttendanceResponse> list(
                    LocalDate from, LocalDate to, UUID employeeId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(UUID id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.infinevo.core.attendance.ClockDayResult recordFromClock(
                    UUID employeeId, LocalDate date, com.infinevo.core.attendance.AttendanceStatus status) {
                UUID tenantId = TenantContext.require();
                if (employeeId == null) {
                    throw new IllegalArgumentException("employeeId is required");
                }
                if (date == null) {
                    throw new IllegalArgumentException("date is required");
                }
                if (status == null) {
                    throw new IllegalArgumentException("status is required");
                }
                LocalDate today = tenantClock != null ? tenantClock.today() : LocalDate.now();
                if (date.isAfter(today)) {
                    throw new IllegalArgumentException("Attendance date cannot be in the future: " + date);
                }

                org.springframework.jdbc.core.JdbcTemplate jdbc =
                        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
                List<Map<String, Object>> existing = jdbc.queryForList(
                        "SELECT status, source FROM core.attendance WHERE tenant_id = ? AND employee_id = ? AND attendance_date = ?",
                        tenantId,
                        employeeId,
                        date);

                if (existing.isEmpty()) {
                    jdbc.update(
                            "INSERT INTO core.attendance (tenant_id, employee_id, attendance_date, status, source, created_by, updated_by) "
                                    + "VALUES (?, ?, ?, ?, 'CLOCK', 'system', 'system')",
                            tenantId,
                            employeeId,
                            date,
                            status.name());
                    return new com.infinevo.core.attendance.ClockDayResult(
                            true, status, com.infinevo.core.attendance.AttendanceSource.CLOCK);
                }

                Map<String, Object> row = existing.get(0);
                String source = (String) row.get("source");
                String currentStatus = (String) row.get("status");

                if (com.infinevo.core.attendance.AttendanceSource.ADMIN.name().equals(source)) {
                    return new com.infinevo.core.attendance.ClockDayResult(
                            false,
                            com.infinevo.core.attendance.AttendanceStatus.valueOf(currentStatus),
                            com.infinevo.core.attendance.AttendanceSource.ADMIN);
                }

                jdbc.update(
                        "UPDATE core.attendance SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND employee_id = ? AND attendance_date = ?",
                        status.name(),
                        tenantId,
                        employeeId,
                        date);
                return new com.infinevo.core.attendance.ClockDayResult(
                        true, status, com.infinevo.core.attendance.AttendanceSource.CLOCK);
            }
        };
    }

    /**
     * Stand-in for {@code core}'s {@code AttendanceQuery} (W-40.4): reads {@code core.attendance} under the bound
     * tenant, as the real {@code AttendanceServiceImpl} does.
     */
    @Bean
    public com.infinevo.core.attendance.AttendanceQuery attendanceQuery(DataSource dataSource) {
        return (employeeId, from, to) -> {
            UUID tenantId = TenantContext.require();
            return new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                    .query(
                            "SELECT attendance_date, status, source, remarks FROM core.attendance "
                                    + "WHERE tenant_id = ? AND employee_id = ? AND attendance_date BETWEEN ? AND ? "
                                    + "ORDER BY attendance_date",
                            (rs, i) -> new com.infinevo.core.attendance.AttendanceDay(
                                    rs.getDate("attendance_date").toLocalDate(),
                                    com.infinevo.core.attendance.AttendanceStatus.valueOf(rs.getString("status")),
                                    com.infinevo.core.attendance.AttendanceSource.valueOf(rs.getString("source")),
                                    rs.getString("remarks")),
                            tenantId,
                            employeeId,
                            from,
                            to);
        };
    }

    /**
     * Test stand-in for {@code core}'s {@code EntitlementReadService}: the controllers are guarded by
     * {@code @RequiresModule(HRMS)}, which is real production behaviour, so the tests entitle their
     * tenant explicitly instead of the guard being relaxed.
     */
    @Bean
    public EntitlementSource entitlementSource() {
        return tenantId -> ENTITLED.getOrDefault(tenantId, Set.of());
    }

    /**
     * Stand-in for core's reporting lines, which this context does not load: the review tests (W-42.4) say who a
     * manager's direct reports are by stubbing {@code findDirectReports}. With nothing stubbed a manager has none.
     */
    @Bean
    public ReportingLineRepository reportingLineRepository() {
        return org.mockito.Mockito.mock(ReportingLineRepository.class);
    }

    /**
     * The approval engine's collaborators that live in core packages this context does not load (W-42.3): the employee
     * and reporting-line lookups its resolver and escalation read, and the notification service the timesheet flow
     * composes through. The approval beans themselves are the real ones, so a submit starts real instances and a
     * decision runs the real outcome handler. Tests stub what they need: with nothing stubbed the chain above an
     * employee is empty, and a notification composes nothing.
     */
    @Bean
    public com.infinevo.core.employee.EmployeeRepository employeeRepository() {
        return org.mockito.Mockito.mock(com.infinevo.core.employee.EmployeeRepository.class);
    }

    /**
     * Core's real employee search (W-48.1): the picker's tenant, status and text filters are core's query, so the
     * test runs that query. The repository is built directly from the shared entity manager rather than registered
     * as a bean, so the mocked {@code EmployeeRepository} above stays the one other tests stub.
     */
    @Bean
    public com.infinevo.core.employee.EmployeeQueryService employeeQueryService(
            jakarta.persistence.EntityManager entityManager,
            com.infinevo.shared.authz.PermissionService permissionService) {
        com.infinevo.core.employee.EmployeeRepository real =
                new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(entityManager)
                        .getRepository(com.infinevo.core.employee.EmployeeRepository.class);
        return new com.infinevo.core.employee.EmployeeQueryService(real, permissionService);
    }

    @Bean
    public com.infinevo.core.org.ReportingLineService reportingLineService() {
        return org.mockito.Mockito.mock(com.infinevo.core.org.ReportingLineService.class);
    }

    @Bean
    public com.infinevo.core.notification.NotificationService notificationService() {
        return org.mockito.Mockito.mock(com.infinevo.core.notification.NotificationService.class);
    }

    @Bean
    public EmployeeService employeeService(DataSource dataSource) {
        return new EmployeeService() {
            @Override
            public EmployeeResponse get(UUID id) {
                EmployeeResponse extra = EXTRA_EMPLOYEES.get(id);
                if (extra != null) {
                    return extra;
                }
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, employee_number, first_name, last_name, work_email FROM core.employee WHERE id = ? AND tenant_id = ? AND is_deleted = false")) {
                        ps.setObject(1, id);
                        ps.setObject(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new EmployeeService.NotFoundException(id);
                            }
                            EmployeeResponse response = new EmployeeResponse(
                                    id,
                                    tenantId,
                                    rs.getString("employee_number"),
                                    rs.getString("first_name"),
                                    null,
                                    rs.getString("last_name"),
                                    "MALE",
                                    LocalDate.now(),
                                    null,
                                    EmploymentStatus.ACTIVE,
                                    rs.getString("work_email"),
                                    null,
                                    false,
                                    null,
                                    null,
                                    null,
                                    null,
                                    Instant.now(),
                                    Instant.now());
                            conn.commit();
                            return response;
                        }
                    } catch (SQLException | RuntimeException e) {
                        try {
                            conn.rollback();
                        } catch (SQLException ignored) {
                        }
                        throw e;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public EmployeeResponse create(EmployeeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EmployeeResponse update(UUID id, EmployeeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(UUID id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<EmployeeResponse> currentEmployee() {
                return Optional.ofNullable(CURRENT_EMPLOYEE.get());
            }

            @Override
            public EmployeeResponse linkLogin(UUID id, UUID userAccountId) {
                throw new UnsupportedOperationException();
            }

            /** Names straight from {@code core.employee}, as {@code EmployeeServiceImpl.displayNames} reads them. */
            @Override
            public Map<UUID, String> displayNames(java.util.Collection<UUID> ids) {
                if (ids == null || ids.isEmpty()) {
                    return Map.of();
                }
                UUID tenantId = TenantContext.require();
                Map<UUID, String> names = new java.util.HashMap<>();
                org.springframework.jdbc.core.JdbcTemplate jdbc =
                        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
                for (UUID id : ids) {
                    jdbc.query(
                            "SELECT first_name, last_name, employee_number FROM core.employee "
                                    + "WHERE tenant_id = ? AND id = ?",
                            rs -> {
                                String name = java.util.stream.Stream.of(
                                                rs.getString("first_name"), rs.getString("last_name"))
                                        .filter(part -> part != null && !part.isBlank())
                                        .collect(java.util.stream.Collectors.joining(" "));
                                names.put(id, name.isBlank() ? rs.getString("employee_number") : name);
                            },
                            tenantId,
                            id);
                }
                return names;
            }

            // W-29.1 added this to EmployeeService for the pay run; nothing in the project code reads it,
            // so this stand-in refuses it like its other unused methods rather than return a wrong list.
            @Override
            public java.util.List<EmployeeResponse> listEmployedBetween(
                    java.time.LocalDate start, java.time.LocalDate end) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
