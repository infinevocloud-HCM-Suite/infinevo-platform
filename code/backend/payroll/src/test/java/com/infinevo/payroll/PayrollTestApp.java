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
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot test application for the payroll integration tests (W-26.1, W-26.2).
 */
// The scan skips the narrower test applications under com.infinevo.payroll (PayScheduleTestApp):
// picking one up re-runs its @EnableJpaRepositories and registers its repositories twice.
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = {"com.infinevo.payroll", "com.infinevo.core.approval", "com.infinevo.core.payinput"},
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
            @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
        })
// core.lop entities and repositories only: W-29.3's loss of pay reads LopPolicy through the two
// beans below. The package is not component-scanned, so its controller and other beans stay out.
// core.job the same way: W-29.4's compute creates a job through the JobServiceImpl bean below.
// core.employee entities only (no repositories, no beans): W-34.3's chase query joins
// EmployeeInvestmentDeclaration to Employee. The real application and worker scan all of com.infinevo,
// so the entity is there in production; this test application's scan is narrower on purpose.
// core.setup entity and repository only: W-38.3's status reads the tenant's PRIOR_PAYROLL row.
@EntityScan(
        basePackages = {
            "com.infinevo.payroll",
            "com.infinevo.core.approval",
            "com.infinevo.core.payinput",
            "com.infinevo.core.lop",
            "com.infinevo.core.job",
            "com.infinevo.core.employee",
            "com.infinevo.core.org",
            "com.infinevo.core.setup"
        })
@EnableJpaRepositories(
        basePackages = {
            "com.infinevo.payroll",
            "com.infinevo.core.approval",
            "com.infinevo.core.payinput",
            "com.infinevo.core.lop",
            "com.infinevo.core.job",
            "com.infinevo.core.employee",
            "com.infinevo.core.setup"
        })
public class PayrollTestApp {

    public static final ThreadLocal<EmployeeResponse> CURRENT_EMPLOYEE = new ThreadLocal<>();
    public static final ThreadLocal<UUID> APPROVER_ID = new ThreadLocal<>();
    public static final java.util.Map<UUID, com.infinevo.core.document.DocumentResponse> TEST_DOCUMENTS =
            new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<UUID, byte[]> TEST_DOCUMENT_CONTENTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Bean("taxRecalc")
    @org.springframework.context.annotation.Primary
    public java.util.concurrent.Executor taxRecalcExecutor() {
        return new org.springframework.core.task.SyncTaskExecutor();
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
    public com.infinevo.core.lop.LopPolicyService lopPolicyService(
            com.infinevo.core.lop.LopPolicyRepository lopPolicyRepository) {
        return new com.infinevo.core.lop.LopPolicyService(lopPolicyRepository);
    }

    /**
     * Employees the calculator's employee lookup reports without a work location (W-18.2 §7). This
     * context has no core.employee org columns (V014) or holiday calendar; the location rule itself is
     * WorkingDayBasisCalculatorTest's, the pay run's reaction to it NoPolicyFailsEmployeeIT's.
     */
    public static final java.util.Set<UUID> EMPLOYEES_WITHOUT_LOCATION =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * W-18.1's calculator for W-29.3 and W-18.2. The working week comes from W-28's
     * PayScheduleWorkingWeekSource, which this context scans. Holidays are none, from any calendar; the
     * employee lookup answers every employee with a location except those in
     * {@link #EMPLOYEES_WITHOUT_LOCATION}.
     */
    @Bean
    public com.infinevo.core.lop.WorkingDayBasisCalculator workingDayBasisCalculator(
            com.infinevo.core.lop.LopPolicyService lopPolicyService) {
        com.infinevo.core.employee.EmployeeRepository employees =
                org.mockito.Mockito.mock(com.infinevo.core.employee.EmployeeRepository.class);
        UUID location = UUID.fromString("44444444-4444-4444-4444-444444444444");
        org.mockito.Mockito.when(employees.findByIdAndTenantIdAndDeletedFalse(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    com.infinevo.core.employee.Employee employee =
                            org.mockito.Mockito.mock(com.infinevo.core.employee.Employee.class);
                    if (!EMPLOYEES_WITHOUT_LOCATION.contains(id)) {
                        com.infinevo.core.org.WorkLocation workLocation =
                                org.mockito.Mockito.mock(com.infinevo.core.org.WorkLocation.class);
                        org.mockito.Mockito.when(workLocation.getId()).thenReturn(location);
                        org.mockito.Mockito.when(employee.getWorkLocation()).thenReturn(workLocation);
                    }
                    return Optional.of(employee);
                });
        com.infinevo.core.holiday.HolidayQueryService holidays =
                org.mockito.Mockito.mock(com.infinevo.core.holiday.HolidayQueryService.class);
        org.mockito.Mockito.when(holidays.holidaysBetween(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        return new com.infinevo.core.lop.WorkingDayBasisCalculator(lopPolicyService, employees, holidays);
    }

    @Bean
    public com.infinevo.core.job.service.JobService jobService(
            com.infinevo.core.job.repository.JobStatusRepository jobStatusRepository) {
        return new com.infinevo.core.job.serviceimpl.JobServiceImpl(jobStatusRepository);
    }

    /** W-29.4's queue: keeps what compute sends, for the tests to deliver. */
    @Bean
    public com.infinevo.payroll.payrun.RecordingQueueProducer recordingQueueProducer() {
        return new com.infinevo.payroll.payrun.RecordingQueueProducer();
    }

    /** W-29.4's worker, in the test's thread. */
    @Bean
    public com.infinevo.payroll.payrun.InProcessPayRunWorker inProcessPayRunWorker(
            com.infinevo.payroll.payrun.PayRunService payRunService,
            com.infinevo.payroll.payrun.PayRunComputationService computationService,
            com.infinevo.core.job.service.JobService jobService,
            com.infinevo.payroll.payrun.RecordingQueueProducer producer) {
        return new com.infinevo.payroll.payrun.InProcessPayRunWorker(
                payRunService, computationService, jobService, producer);
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
                UUID tenantId = TenantContext.current().orElse(PayrollTestSchema.TENANT_A);
                byte[] bytes = new byte[0];
                if (content != null) {
                    try {
                        bytes = content.readAllBytes();
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                TEST_DOCUMENT_CONTENTS.put(id, bytes);
                com.infinevo.core.document.DocumentResponse doc = new com.infinevo.core.document.DocumentResponse(
                        id,
                        employeeId,
                        kind,
                        null,
                        fileName,
                        "text/csv",
                        (long) bytes.length,
                        "test_checksum",
                        Instant.now(),
                        "system");
                TEST_DOCUMENTS.put(id, doc);
                try (Connection conn = PayrollTestSchema.migrationConnection();
                        PreparedStatement ps = conn.prepareStatement(
                                """
                                INSERT INTO core.document
                                    (id, tenant_id, employee_id, kind, file_name, content_type, size_bytes,
                                     blob_container, blob_path, checksum_sha256)
                                VALUES (?, ?, ?, ?, ?, 'text/csv', ?, 'documents', ?, ?)
                                ON CONFLICT (id) DO NOTHING
                                """)) {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, employeeId);
                    ps.setString(4, kind.name());
                    ps.setString(5, fileName);
                    ps.setLong(6, bytes.length);
                    ps.setString(7, tenantId + "/test/" + kind.name() + "/" + id);
                    ps.setString(8, "0".repeat(64));
                    ps.executeUpdate();
                } catch (Exception ignored) {
                    // Ignored if core.document table doesn't exist yet in tests
                }
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
                byte[] bytes = TEST_DOCUMENT_CONTENTS.getOrDefault(id, new byte[0]);
                return new com.infinevo.core.document.DocumentService.DocumentContent(
                        get(id), new java.io.ByteArrayInputStream(bytes));
            }

            @Override
            public void delete(UUID id) {
                TEST_DOCUMENTS.remove(id);
                TEST_DOCUMENT_CONTENTS.remove(id);
            }
        };
    }

    @Bean
    public EmployeePersonalService employeePersonalService(DataSource dataSource) {
        return new EmployeePersonalService() {
            @Override
            public Optional<EmployeePersonalResponse> find(UUID employeeId) {
                try {
                    return Optional.of(get(employeeId));
                } catch (EmployeeDetailService.NotFoundException e) {
                    return Optional.empty();
                }
            }

            @Override
            public EmployeePersonalResponse get(UUID employeeId) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, date_of_birth, marital_status, nationality, ethnicity, father_name, differently_abled_type, is_eligible_for_full_tax_exemption, created_at, updated_at "
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
                                    rs.getBoolean("is_eligible_for_full_tax_exemption"),
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
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
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
                        conn.commit();
                        return get(employeeId);
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
                            "SELECT id, employee_number, first_name, last_name, gender, work_email, date_of_joining, "
                                    + "termination_date, status, work_location_id FROM core.employee "
                                    + "WHERE id = ? AND tenant_id = ? AND is_deleted = false")) {
                        ps.setObject(1, id);
                        ps.setObject(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new EmployeeService.NotFoundException(id);
                            }
                            java.sql.Date dojSql = rs.getDate("date_of_joining");
                            LocalDate doj = dojSql != null ? dojSql.toLocalDate() : LocalDate.of(2024, 1, 1);
                            UUID workLocationId = null;
                            try {
                                workLocationId = rs.getObject("work_location_id", UUID.class);
                            } catch (SQLException ignored) {
                            }
                            String gender = "MALE";
                            try {
                                String g = rs.getString("gender");
                                if (g != null && !g.isBlank()) {
                                    gender = g;
                                }
                            } catch (SQLException ignored) {
                            }
                            EmployeeResponse response = new EmployeeResponse(
                                    id,
                                    tenantId,
                                    rs.getString("employee_number"),
                                    rs.getString("first_name"),
                                    null,
                                    rs.getString("last_name"),
                                    gender,
                                    // The real dates and status, as EmployeeServiceImpl.get returns them:
                                    // an off-cycle run (W-30.2) tests employment against the period.
                                    doj,
                                    rs.getObject("termination_date", LocalDate.class),
                                    rs.getString("status") == null
                                            ? null
                                            : com.infinevo.core.employee.EmploymentStatus.valueOf(
                                                    rs.getString("status")),
                                    rs.getString("work_email"),
                                    null,
                                    false,
                                    null,
                                    null,
                                    null,
                                    workLocationId,
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

            // The same rule as EmployeeServiceImpl.displayNames: first and last name, else the employee
            // number; the bound tenant's rows only (W-47.4 §4).
            @Override
            public java.util.Map<UUID, String> displayNames(java.util.Collection<UUID> ids) {
                if (ids == null || ids.isEmpty()) {
                    return java.util.Map.of();
                }
                UUID tenantId = TenantContext.require();
                java.util.Map<UUID, String> names = new java.util.LinkedHashMap<>();
                try (Connection conn = dataSource.getConnection()) {
                    // Auto-commit off before the first statement, so the tenant binds (D-57).
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id, employee_number, first_name, last_name FROM core.employee "
                                    + "WHERE tenant_id = ? AND id = ANY (?)")) {
                        ps.setObject(1, tenantId);
                        ps.setArray(2, conn.createArrayOf("uuid", ids.toArray()));
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String name = java.util.stream.Stream.of(
                                                rs.getString("first_name"), rs.getString("last_name"))
                                        .filter(part -> part != null && !part.isBlank())
                                        .collect(java.util.stream.Collectors.joining(" "));
                                names.put(
                                        rs.getObject("id", UUID.class),
                                        name.isBlank() ? rs.getString("employee_number") : name);
                            }
                        }
                        conn.commit();
                    } catch (SQLException | RuntimeException e) {
                        conn.rollback();
                        throw e;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return names;
            }

            @Override
            public EmployeeResponse linkLogin(UUID id, UUID userAccountId) {
                throw new UnsupportedOperationException();
            }

            // The same predicate as EmployeeRepository.findEmployedBetween (W-29.1 §3), which
            // EmployeeListEmployedBetweenIT covers against the real JPQL in core.
            @Override
            public java.util.List<EmployeeResponse> listEmployedBetween(LocalDate start, LocalDate end) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, employee_number, first_name, last_name, gender, work_email, date_of_joining, "
                                        + "termination_date, status, work_location_id FROM core.employee "
                                        + "WHERE tenant_id = ? AND is_deleted = false AND date_of_joining <= ? "
                                        + "AND (status = 'ACTIVE' OR (status = 'TERMINATED' AND termination_date >= ?)) "
                                        + "ORDER BY employee_number")) {
                            ps.setObject(1, tenantId);
                            ps.setObject(2, end);
                            ps.setObject(3, start);
                            java.util.List<EmployeeResponse> list = new java.util.ArrayList<>();
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    UUID workLocationId = null;
                                    try {
                                        workLocationId = rs.getObject("work_location_id", UUID.class);
                                    } catch (SQLException ignored) {
                                    }
                                    String gender = "MALE";
                                    try {
                                        String g = rs.getString("gender");
                                        if (g != null && !g.isBlank()) {
                                            gender = g;
                                        }
                                    } catch (SQLException ignored) {
                                    }
                                    list.add(new EmployeeResponse(
                                            (UUID) rs.getObject("id"),
                                            tenantId,
                                            rs.getString("employee_number"),
                                            rs.getString("first_name"),
                                            null,
                                            rs.getString("last_name"),
                                            gender,
                                            rs.getObject("date_of_joining", LocalDate.class),
                                            rs.getObject("termination_date", LocalDate.class),
                                            com.infinevo.core.employee.EmploymentStatus.valueOf(rs.getString("status")),
                                            rs.getString("work_email"),
                                            null,
                                            false,
                                            null,
                                            null,
                                            null,
                                            workLocationId,
                                            Instant.now(),
                                            Instant.now()));
                                }
                            }
                            conn.commit();
                            return list;
                        }
                    } finally {
                        conn.setAutoCommit(origAutoCommit);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @Bean
    public com.infinevo.core.employee.detail.EmployeeBankService employeeBankService(DataSource dataSource) {
        return new com.infinevo.core.employee.detail.EmployeeBankService() {
            @Override
            public Optional<com.infinevo.core.employee.detail.EmployeeBankResponse> find(UUID employeeId) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, payment_mode FROM core.employee_bank WHERE employee_id = ? AND tenant_id = ?")) {
                            ps.setObject(1, employeeId);
                            ps.setObject(2, tenantId);
                            try (ResultSet rs = ps.executeQuery()) {
                                Optional<com.infinevo.core.employee.detail.EmployeeBankResponse> result =
                                        Optional.empty();
                                if (rs.next()) {
                                    result = Optional.of(new com.infinevo.core.employee.detail.EmployeeBankResponse(
                                            (UUID) rs.getObject("id"),
                                            tenantId,
                                            employeeId,
                                            com.infinevo.core.employee.detail.PaymentMode.valueOf(
                                                    rs.getString("payment_mode")),
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            Instant.now(),
                                            Instant.now()));
                                }
                                conn.commit();
                                return result;
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
            public com.infinevo.core.employee.detail.EmployeeBankResponse get(UUID employeeId) {
                return find(employeeId)
                        .orElseThrow(
                                () -> new com.infinevo.core.employee.detail.EmployeeDetailService.NotFoundException(
                                        "bank", employeeId));
            }

            @Override
            public com.infinevo.core.employee.detail.EmployeeBankResponse put(
                    UUID employeeId, com.infinevo.core.employee.detail.EmployeeBankRequest request) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Bean
    public com.infinevo.core.employee.detail.EmployeeIdentificationService employeeIdentificationService(
            DataSource dataSource) {
        return new com.infinevo.core.employee.detail.EmployeeIdentificationService() {
            @Override
            public Optional<com.infinevo.core.employee.detail.EmployeeIdentificationResponse> find(UUID employeeId) {
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT id, pan_number, aadhaar_number FROM core.employee_identification WHERE employee_id = ? AND tenant_id = ?")) {
                            ps.setObject(1, employeeId);
                            ps.setObject(2, tenantId);
                            try (ResultSet rs = ps.executeQuery()) {
                                Optional<com.infinevo.core.employee.detail.EmployeeIdentificationResponse> result =
                                        Optional.empty();
                                if (rs.next()) {
                                    result = Optional.of(
                                            new com.infinevo.core.employee.detail.EmployeeIdentificationResponse(
                                                    (UUID) rs.getObject("id"),
                                                    tenantId,
                                                    employeeId,
                                                    null,
                                                    rs.getString("aadhaar_number"),
                                                    rs.getString("pan_number"),
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    Instant.now(),
                                                    Instant.now()));
                                }
                                conn.commit();
                                return result;
                            }
                        }
                    } finally {
                        conn.setAutoCommit(origAutoCommit);
                    }
                } catch (SQLException e) {
                    return Optional.empty();
                }
            }

            @Override
            public com.infinevo.core.employee.detail.EmployeeIdentificationResponse get(UUID employeeId) {
                return find(employeeId)
                        .orElseThrow(
                                () -> new com.infinevo.core.employee.detail.EmployeeDetailService.NotFoundException(
                                        "identification", employeeId));
            }

            @Override
            public com.infinevo.core.employee.detail.EmployeeIdentificationResponse put(
                    UUID employeeId, com.infinevo.core.employee.detail.EmployeeIdentificationRequest request) {
                throw new UnsupportedOperationException();
            }

            /**
             * W-36.5: the same predicate as EmployeeIdentificationRepository.findByTenantIdAndPanNumberIn —
             * bound tenant on both tables, live employees only, upper-cased PANs, a PAN on two employees
             * reported ambiguous. EmployeeIdentificationPanLookupIT covers the real JPQL in core.
             */
            @Override
            public com.infinevo.core.employee.detail.PanLookup lookupByPan(java.util.Set<String> pans) {
                UUID tenantId = TenantContext.require();
                java.util.Set<String> normalised = new java.util.HashSet<>();
                if (pans != null) {
                    for (String pan : pans) {
                        if (pan != null && !pan.isBlank()) {
                            normalised.add(pan.trim().toUpperCase(java.util.Locale.ROOT));
                        }
                    }
                }
                if (normalised.isEmpty()) {
                    return com.infinevo.core.employee.detail.PanLookup.EMPTY;
                }
                java.util.Map<String, UUID> byPan = new java.util.HashMap<>();
                java.util.Set<String> ambiguous = new java.util.HashSet<>();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps =
                                conn.prepareStatement("SELECT i.pan_number, e.id FROM core.employee_identification i "
                                        + "JOIN core.employee e ON e.id = i.employee_id "
                                        + "WHERE i.tenant_id = ? AND e.tenant_id = ? AND e.is_deleted = false "
                                        + "AND i.pan_number = ANY (?)")) {
                            ps.setObject(1, tenantId);
                            ps.setObject(2, tenantId);
                            ps.setArray(3, conn.createArrayOf("varchar", normalised.toArray()));
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    String pan = rs.getString(1);
                                    UUID id = rs.getObject(2, UUID.class);
                                    UUID previous = byPan.putIfAbsent(pan, id);
                                    if (previous != null && !previous.equals(id)) {
                                        ambiguous.add(pan);
                                    }
                                }
                            }
                        }
                        conn.commit();
                    } finally {
                        conn.setAutoCommit(origAutoCommit);
                    }
                } catch (SQLException e) {
                    return com.infinevo.core.employee.detail.PanLookup.EMPTY;
                }
                byPan.keySet().removeAll(ambiguous);
                return new com.infinevo.core.employee.detail.PanLookup(byPan, ambiguous);
            }
        };
    }

    /**
     * W-36.5: core's real link signer. Its repository is a stand-in that answers from {@code core.document}
     * — a live row in the bound tenant — because this context does not scan core.document.
     */
    @Bean
    public com.infinevo.core.document.DocumentLinkService documentLinkService() {
        com.infinevo.core.document.DocumentRepository documents =
                org.mockito.Mockito.mock(com.infinevo.core.document.DocumentRepository.class);
        org.mockito.Mockito.when(documents.findByIdAndTenantIdAndDeletedFalse(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    UUID tenantId = invocation.getArgument(1);
                    try (Connection conn = PayrollTestSchema.migrationConnection();
                            PreparedStatement ps = conn.prepareStatement(
                                    "SELECT 1 FROM core.document WHERE id = ? AND tenant_id = ? AND NOT is_deleted")) {
                        ps.setObject(1, id);
                        ps.setObject(2, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next()
                                    ? Optional.of(org.mockito.Mockito.mock(com.infinevo.core.document.Document.class))
                                    : Optional.empty();
                        }
                    }
                });
        return new com.infinevo.core.document.DocumentLinkServiceImpl(
                documents,
                "integration-test-document-link-secret",
                com.infinevo.shared.security.PublicEndpoints.DOCUMENT_DOWNLOAD);
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

            public static final java.util.concurrent.atomic.AtomicInteger WORK_LOCATION_GET_COUNT =
                    new java.util.concurrent.atomic.AtomicInteger();

            @Override
            public com.infinevo.core.org.WorkLocationResponse get(UUID id) {
                WORK_LOCATION_GET_COUNT.incrementAndGet();
                UUID tenantId = TenantContext.require();
                try (Connection conn = dataSource.getConnection()) {
                    boolean origAutoCommit = conn.getAutoCommit();
                    try {
                        conn.setAutoCommit(false);
                        String sql =
                                "SELECT id, tenant_id, code, name, address_line1, address_line2, city, state, state_code, zip_code, country_code, is_filing_address, is_active, created_at, updated_at "
                                        + "FROM core.work_location WHERE tenant_id = ? AND id = ?";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setObject(1, tenantId);
                            ps.setObject(2, id);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    com.infinevo.core.org.WorkLocationResponse resp =
                                            new com.infinevo.core.org.WorkLocationResponse(
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
                                                    rs.getTimestamp("created_at")
                                                            .toInstant(),
                                                    rs.getTimestamp("updated_at")
                                                            .toInstant());
                                    conn.commit();
                                    return resp;
                                }
                                throw new com.infinevo.core.org.OrgMasterService.NotFoundException("work location", id);
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
    public static org.springframework.context.support.PropertySourcesPlaceholderConfigurer
            propertySourcesPlaceholderConfigurer() {
        org.springframework.context.support.PropertySourcesPlaceholderConfigurer configurer =
                new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
        java.util.Properties props = new java.util.Properties();
        props.setProperty("document.link.secret", "integration-test-document-link-secret");
        props.setProperty("payslip.link.base-url", "http://localhost:5173/public/payslips");
        configurer.setProperties(props);
        return configurer;
    }

    /**
     * Writes the queued PAYSLIP_READY row (W-36.2's payslip tests read it back) and is a Mockito mock
     * delegating to that writer, so W-34's proof tests can {@code verify} and clear its invocations.
     */
    @Bean
    public com.infinevo.core.notification.NotificationService notificationService(DataSource dataSource) {
        return org.mockito.Mockito.mock(
                com.infinevo.core.notification.NotificationService.class,
                org.mockito.AdditionalAnswers.delegatesTo(new com.infinevo.core.notification.NotificationService() {
                    @Override
                    public java.util.List<UUID> compose(
                            com.infinevo.core.notification.NotificationEvent event,
                            UUID recipientEmployeeId,
                            java.util.Map<String, Object> data) {
                        // Only the payslip tests read the row back; W-34's proof events stay row-free, as their
                        // cleanup deletes employees a notification row would still reference.
                        if (event != com.infinevo.core.notification.NotificationEvent.PAYSLIP_READY) {
                            return java.util.List.of();
                        }
                        UUID tenantId = TenantContext.require();
                        UUID id = UUID.randomUUID();
                        String link = data != null && data.containsKey("link") ? String.valueOf(data.get("link")) : "";
                        String body = "<p>Your payslip is ready. <a href=\"" + link + "\">Download it</a>.</p>";
                        try (Connection conn = PayrollTestSchema.migrationConnection();
                                PreparedStatement ps = conn.prepareStatement(
                                        "INSERT INTO core.notification (id, tenant_id, recipient_employee_id, event, channel, body, status, queued_at, created_by, updated_by) "
                                                + "VALUES (?, ?, ?, ?, 'EMAIL', ?, 'QUEUED', CURRENT_TIMESTAMP, 'system', 'system')")) {
                            ps.setObject(1, id);
                            ps.setObject(2, tenantId);
                            ps.setObject(3, recipientEmployeeId);
                            ps.setString(4, event.name());
                            ps.setString(5, body);
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                        return java.util.List.of(id);
                    }

                    @Override
                    public org.springframework.data.domain.Page<com.infinevo.core.notification.NotificationResponse>
                            mine(boolean unreadOnly, org.springframework.data.domain.Pageable pageable) {
                        return org.springframework.data.domain.Page.empty();
                    }

                    @Override
                    public com.infinevo.core.notification.NotificationResponse markRead(UUID id) {
                        throw new UnsupportedOperationException();
                    }
                }));
    }
}
