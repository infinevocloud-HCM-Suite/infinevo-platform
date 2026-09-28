package com.infinevo.payroll;

import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot test application for the payroll integration tests (W-26.1, W-26.2).
 */
@SpringBootApplication(scanBasePackages = {"com.infinevo.payroll"})
@EntityScan(basePackages = {"com.infinevo.payroll"})
@EnableJpaRepositories(basePackages = {"com.infinevo.payroll"})
public class PayrollTestApp {

    public static final ThreadLocal<EmployeeResponse> CURRENT_EMPLOYEE = new ThreadLocal<>();

    @Bean
    public EmployeeService employeeService(DataSource dataSource) {
        return new EmployeeService() {
            @Override
            public EmployeeResponse get(UUID id) {
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
                                    null,
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
        };
    }

    @Bean
    public com.infinevo.core.org.WorkLocationService workLocationService(DataSource dataSource) {
        return new com.infinevo.core.org.WorkLocationService() {
            @Override
            public java.util.List<com.infinevo.core.org.WorkLocationResponse> list(boolean activeOnly) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        String sql =
                                "SELECT id, tenant_id, code, name, address_line1, address_line2, city, state, state_code, zip_code, country_code, is_filing_address, is_active, created_at, updated_at "
                                        + "FROM core.work_location WHERE tenant_id = ? "
                                        + (activeOnly ? "AND is_active = true " : "")
                                        + "ORDER BY code ASC";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setObject(1, tenantId);
                            try (ResultSet rs = ps.executeQuery()) {
                                java.util.List<com.infinevo.core.org.WorkLocationResponse> list =
                                        new java.util.ArrayList<>();
                                while (rs.next()) {
                                    list.add(new com.infinevo.core.org.WorkLocationResponse(
                                            (UUID) rs.getObject("id"),
                                            (UUID) rs.getObject("tenant_id"),
                                            rs.getString("code"),
                                            rs.getString("name"),
                                            rs.getString("address_line1"),
                                            rs.getString("address_line2"),
                                            rs.getString("city"),
                                            rs.getString("state"),
                                            rs.getString("state_code"),
                                            rs.getString("zip_code"),
                                            rs.getString("country_code"),
                                            rs.getBoolean("is_filing_address"),
                                            rs.getBoolean("is_active"),
                                            rs.getTimestamp("created_at").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant()));
                                }
                                conn.commit();
                                return list;
                            }
                        }
                    } finally {
                        conn.setAutoCommit(origAutoCommit);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public com.infinevo.core.org.WorkLocationResponse create(
                    com.infinevo.core.org.WorkLocationRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.infinevo.core.org.WorkLocationResponse update(
                    UUID id, com.infinevo.core.org.WorkLocationRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(UUID id) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
