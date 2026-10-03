package com.infinevo.payroll;

import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeDetailService;
import com.infinevo.core.employee.detail.EmployeePersonalRequest;
import com.infinevo.core.employee.detail.EmployeePersonalResponse;
import com.infinevo.core.employee.detail.EmployeePersonalService;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Boot test application for the payroll integration tests (W-26.1, W-26.2).
 */
@SpringBootApplication(
        scanBasePackages = {"com.infinevo.payroll", "com.infinevo.core.approval", "com.infinevo.core.payinput"})
@EntityScan(basePackages = {"com.infinevo.payroll", "com.infinevo.core.approval", "com.infinevo.core.payinput"})
@EnableJpaRepositories(
        basePackages = {"com.infinevo.payroll", "com.infinevo.core.approval", "com.infinevo.core.payinput"})
public class PayrollTestApp {

    public static final ThreadLocal<EmployeeResponse> CURRENT_EMPLOYEE = new ThreadLocal<>();
    public static final ThreadLocal<UUID> APPROVER_ID = new ThreadLocal<>();
    public static final java.util.Map<UUID, com.infinevo.core.document.DocumentResponse> TEST_DOCUMENTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Bean("taxRecalc")
    @Primary
    public Executor taxRecalcExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    public com.infinevo.core.approval.CoreApproverResolver coreApproverResolver() {
        com.infinevo.core.approval.CoreApproverResolver resolver =
                org.mockito.Mockito.mock(com.infinevo.core.approval.CoreApproverResolver.class);
        org.mockito.Mockito.when(resolver.resolve(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> Optional.ofNullable(APPROVER_ID.get()));
        return resolver;
    }

    @Bean
    public com.infinevo.shared.authz.PermissionService permissionService() {
        com.infinevo.shared.authz.PermissionService service =
                org.mockito.Mockito.mock(com.infinevo.shared.authz.PermissionService.class);
        org.mockito.Mockito.when(service.holds(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        return service;
    }

    @Bean
    public com.infinevo.core.employee.EmployeeRepository employeeRepository() {
        return org.mockito.Mockito.mock(com.infinevo.core.employee.EmployeeRepository.class);
    }

    @Bean
    public com.infinevo.core.org.ReportingLineService reportingLineService() {
        return org.mockito.Mockito.mock(com.infinevo.core.org.ReportingLineService.class);
    }

    @Bean
    public com.infinevo.core.document.DocumentService documentService() {
        return new com.infinevo.core.document.DocumentService() {
            @Override
            public UUID store(
                    com.infinevo.core.document.DocumentKind kind,
                    UUID employeeId,
                    String fileName,
                    java.io.InputStream content) {
                UUID id = UUID.randomUUID();
                com.infinevo.core.document.DocumentResponse doc = new com.infinevo.core.document.DocumentResponse(
                        id,
                        employeeId,
                        kind,
                        fileName,
                        "application/pdf",
                        1024L,
                        "test_checksum",
                        Instant.now(),
                        "system");
                TEST_DOCUMENTS.put(id, doc);
                return id;
            }

            @Override
            public UUID storeFile(
                    com.infinevo.core.document.DocumentKind kind,
                    UUID employeeId,
                    String fileName,
                    java.nio.file.Path file) {
                return store(kind, employeeId, fileName, null);
            }

            @Override
            public com.infinevo.core.document.DocumentResponse get(UUID id) {
                com.infinevo.core.document.DocumentResponse doc = TEST_DOCUMENTS.get(id);
                if (doc == null) {
                    throw new com.infinevo.core.document.DocumentService.NotFoundException(id);
                }
                return doc;
            }

            @Override
            public com.infinevo.core.document.DocumentService.DocumentContent open(UUID id) {
                return new com.infinevo.core.document.DocumentService.DocumentContent(
                        get(id), new java.io.ByteArrayInputStream(new byte[0]));
            }

            @Override
            public void delete(UUID id) {
                TEST_DOCUMENTS.remove(id);
            }
        };
    }

    @Bean
    public EmployeePersonalService employeePersonalService(
            DataSource dataSource, PlatformTransactionManager txManager) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return new EmployeePersonalService() {
            private EmployeePersonalResponse fetchPersonal(UUID employeeId) {
                UUID tenantId = TenantContext.require();
                Connection conn = DataSourceUtils.getConnection(dataSource);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, date_of_birth, marital_status, nationality, ethnicity, father_name, differently_abled_type, is_eligible_for_full_tax_exemption, created_at, updated_at "
                                + "FROM core.employee_personal WHERE employee_id = ? AND tenant_id = ?")) {
                    ps.setObject(1, employeeId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        java.sql.Date dobSql = rs.getDate("date_of_birth");
                        LocalDate dob = dobSql != null ? dobSql.toLocalDate() : null;
                        return new EmployeePersonalResponse(
                                (UUID) rs.getObject("id"),
                                tenantId,
                                employeeId,
                                dob,
                                rs.getString("marital_status"),
                                rs.getString("nationality"),
                                rs.getString("ethnicity"),
                                rs.getString("father_name"),
                                rs.getString("differently_abled_type"),
                                rs.getBoolean("is_eligible_for_full_tax_exemption"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant());
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                } finally {
                    DataSourceUtils.releaseConnection(conn, dataSource);
                }
            }

            @Override
            public Optional<EmployeePersonalResponse> find(UUID employeeId) {
                return Optional.ofNullable(fetchPersonal(employeeId));
            }

            @Override
            public EmployeePersonalResponse get(UUID employeeId) {
                EmployeePersonalResponse resp = fetchPersonal(employeeId);
                if (resp == null) {
                    throw new EmployeeDetailService.NotFoundException("employee_personal", employeeId);
                }
                return resp;
            }

            @Override
            public EmployeePersonalResponse put(UUID employeeId, EmployeePersonalRequest request) {
                return tx.execute(status -> {
                    UUID tenantId = TenantContext.require();
                    Connection conn = DataSourceUtils.getConnection(dataSource);
                    try (PreparedStatement ps = conn.prepareStatement(
                            """
                            INSERT INTO core.employee_personal (
                                tenant_id, employee_id, date_of_birth, marital_status, nationality,
                                ethnicity, father_name, differently_abled_type, is_eligible_for_full_tax_exemption,
                                created_by, updated_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', 'system')
                            ON CONFLICT (tenant_id, employee_id) DO UPDATE
                            SET date_of_birth = EXCLUDED.date_of_birth,
                                marital_status = EXCLUDED.marital_status,
                                nationality = EXCLUDED.nationality,
                                ethnicity = EXCLUDED.ethnicity,
                                father_name = EXCLUDED.father_name,
                                differently_abled_type = EXCLUDED.differently_abled_type,
                                is_eligible_for_full_tax_exemption = EXCLUDED.is_eligible_for_full_tax_exemption,
                                updated_at = NOW(),
                                updated_by = 'system'
                            """)) {
                        ps.setObject(1, tenantId);
                        ps.setObject(2, employeeId);
                        ps.setObject(
                                3, request.dateOfBirth() != null ? java.sql.Date.valueOf(request.dateOfBirth()) : null);
                        ps.setString(4, request.maritalStatus());
                        ps.setString(5, request.nationality());
                        ps.setString(6, request.ethnicity());
                        ps.setString(7, request.fatherName());
                        ps.setString(8, request.differentlyAbledType());
                        ps.setBoolean(9, Boolean.TRUE.equals(request.eligibleForFullTaxExemption()));
                        ps.executeUpdate();
                        return get(employeeId);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    } finally {
                        DataSourceUtils.releaseConnection(conn, dataSource);
                    }
                });
            }
        };
    }

    @Bean
    public EmployeeService employeeService(DataSource dataSource, PlatformTransactionManager txManager) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return new EmployeeService() {
            @Override
            public EmployeeResponse get(UUID id) {
                return tx.execute(status -> {
                    UUID tenantId = TenantContext.require();
                    Connection conn = DataSourceUtils.getConnection(dataSource);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, employee_number, first_name, last_name, work_email, date_of_joining FROM core.employee WHERE id = ? AND tenant_id = ? AND is_deleted = false")) {
                        ps.setObject(1, id);
                        ps.setObject(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new EmployeeService.NotFoundException(id);
                            }
                            java.sql.Date dojSql = rs.getDate("date_of_joining");
                            LocalDate doj = dojSql != null ? dojSql.toLocalDate() : LocalDate.of(2024, 1, 1);
                            return new EmployeeResponse(
                                    id,
                                    tenantId,
                                    rs.getString("employee_number"),
                                    rs.getString("first_name"),
                                    null,
                                    rs.getString("last_name"),
                                    "MALE",
                                    doj,
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
                    } finally {
                        DataSourceUtils.releaseConnection(conn, dataSource);
                    }
                });
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
    public com.infinevo.core.org.WorkLocationService workLocationService(
            DataSource dataSource, PlatformTransactionManager txManager) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        return new com.infinevo.core.org.WorkLocationService() {
            @Override
            public java.util.List<com.infinevo.core.org.WorkLocationResponse> list(boolean activeOnly) {
                return tx.execute(status -> {
                    UUID tenantId = TenantContext.require();
                    Connection conn = DataSourceUtils.getConnection(dataSource);
                    try {
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
                                return list;
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    } finally {
                        DataSourceUtils.releaseConnection(conn, dataSource);
                    }
                });
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
