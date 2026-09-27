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

    @Bean
    public EmployeeService employeeService(DataSource dataSource) {
        return new EmployeeService() {
            @Override
            public EmployeeResponse get(UUID id) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection();
                        PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, employee_number, first_name, last_name, work_email FROM core.employee WHERE id = ? AND tenant_id = ? AND is_deleted = false")) {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new EmployeeService.NotFoundException(id);
                        }
                        return new EmployeeResponse(
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
                return Optional.empty();
            }

            @Override
            public EmployeeResponse linkLogin(UUID id, UUID userAccountId) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
