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
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

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
            public DocumentContent open(UUID id) {
                return new DocumentContent(get(id), new java.io.ByteArrayInputStream(new byte[0]));
            }

            @Override
            public void delete(UUID id) {
                TEST_DOCUMENTS.remove(id);
            }
        };
    }

    @Bean
    public EmployeePersonalService employeePersonalService(DataSource dataSource) {
        return new EmployeePersonalService() {
            @Override
            public EmployeePersonalResponse get(UUID employeeId) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, date_of_birth, marital_status, nationality, ethnicity, father_name, differently_abled_type, eligible_for_full_tax_exemption, created_at, updated_at "
                                    + "FROM core.employee_personal WHERE employee_id = ? AND tenant_id = ?")) {
                        ps.setObject(1, employeeId);
                        ps.setObject(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new EmployeeDetailService.NotFoundException("employee_personal", employeeId);
                            }
                            java.sql.Date dobSql = rs.getDate("date_of_birth");
                            LocalDate dob = dobSql != null ? dobSql.toLocalDate() : null;
                            EmployeePersonalResponse response = new EmployeePersonalResponse(
                                    (UUID) rs.getObject("id"),
                                    tenantId,
                                    employeeId,
                                    dob,
                                    rs.getString("marital_status"),
                                    rs.getString("nationality"),
                                    rs.getString("ethnicity"),
                                    rs.getString("father_name"),
                                    rs.getString("differently_abled_type"),
                                    rs.getBoolean("eligible_for_full_tax_exemption"),
                                    rs.getTimestamp("created_at").toInstant(),
                                    rs.getTimestamp("updated_at").toInstant());
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
            public EmployeePersonalResponse put(UUID employeeId, EmployeePersonalRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

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

    @Bean
    public com.infinevo.core.employee.detail.EmployeePersonalService employeePersonalService(DataSource dataSource) {
        return new com.infinevo.core.employee.detail.EmployeePersonalService() {
            @Override
            public Optional<com.infinevo.core.employee.detail.EmployeePersonalResponse> find(UUID employeeId) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        String sql =
                                "SELECT id, date_of_birth, marital_status, nationality, ethnicity, father_name, differently_abled_type, eligible_for_full_tax_exemption, created_at, updated_at "
                                        + "FROM core.employee_personal WHERE employee_id = ? AND tenant_id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setObject(1, employeeId);
                            ps.setObject(2, tenantId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    java.sql.Date dob = rs.getDate("date_of_birth");
                                    conn.commit();
                                    return Optional.of(new com.infinevo.core.employee.detail.EmployeePersonalResponse(
                                            (UUID) rs.getObject("id"),
                                            tenantId,
                                            employeeId,
                                            dob != null ? dob.toLocalDate() : null,
                                            rs.getString("marital_status"),
                                            rs.getString("nationality"),
                                            rs.getString("ethnicity"),
                                            rs.getString("father_name"),
                                            rs.getString("differently_abled_type"),
                                            rs.getBoolean("eligible_for_full_tax_exemption"),
                                            rs.getTimestamp("created_at").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant()));
                                }
                                conn.commit();
                                return Optional.empty();
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
            public com.infinevo.core.employee.detail.EmployeePersonalResponse get(UUID employeeId) {
                return find(employeeId)
                        .orElseThrow(
                                () -> new com.infinevo.core.employee.detail.EmployeeDetailService.NotFoundException(
                                        "personal", employeeId));
            }

            @Override
            public com.infinevo.core.employee.detail.EmployeePersonalResponse put(
                    UUID employeeId, com.infinevo.core.employee.detail.EmployeePersonalRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
