package com.infinevo.hrms.project;

import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLineRepository;
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
 * Spring Boot test application for the HRMS project integration tests (W-41).
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
            "com.infinevo.hrms.timesheet",
            "com.infinevo.hrms.portal",
            "com.infinevo.hrms.navigation",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz"
        },
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
@EntityScan(
        basePackages = {
            "com.infinevo.hrms.project",
            "com.infinevo.hrms.timesheet",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.shared.identity"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.hrms.project",
            "com.infinevo.hrms.timesheet",
            "com.infinevo.core.approval",
            "com.infinevo.core.authz",
            "com.infinevo.shared.identity"
        })
@Import({RedisConfig.class, UserProfileSyncService.class})
public class HrmsTestApp {

    public static final ThreadLocal<EmployeeResponse> CURRENT_EMPLOYEE = new ThreadLocal<>();
    public static final Map<UUID, EmployeeResponse> EXTRA_EMPLOYEES = new ConcurrentHashMap<>();

    /** Modules each test tenant holds; a tenant absent from the map holds none, as in production. */
    public static final Map<UUID, Set<PlatformModule>> ENTITLED = new ConcurrentHashMap<>();

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
