package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end acceptance test for the officer chase list and summary (W-34.3 spec section 7).
 *
 * <p>Verifies:
 * <ul>
 *   <li>The list displays {@code NOT_STARTED}, {@code DRAFT}, {@code SUBMITTED}, {@code APPROVED},
 *       {@code REJECTED} rows with correct totals.
 *   <li>The status filter and search prefix narrow the list accurately.
 *   <li>Summary counts match real row counts, with due date and open status.
 *   <li>Page size of 500 is clamped to 100, and negative page clamped to 0.
 *   <li>A caller lacking {@code payroll.proof.read} receives 403 Forbidden.
 * </ul>
 */
class ProofChaseListIT extends ProofIntegrationTestBase {

    private static final String BASE_URL = "/api/v1/payroll/proof-of-investment";

    @Autowired
    private ProofChaseService proofChaseService;

    @Autowired
    private EmployeeProofOfInvestmentRepository proofRepository;

    @Autowired
    private EmployeeProofItemRepository proofItemRepository;

    @Autowired
    private PermissionService permissionService;

    private MockMvc mvc;

    private UUID emp1;
    private UUID emp2;
    private UUID emp3;
    private UUID emp4;
    private UUID emp5;

    @BeforeEach
    void setUpControllerMvc() {
        given(permissionService.holds("payroll.proof.read")).willReturn(true);
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        AspectJProxyFactory factory = new AspectJProxyFactory(new ProofChaseController(proofChaseService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        ProofChaseController proxied = factory.getProxy();

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(proxied)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private void seedScenarioEmployees() throws Exception {
        openWindows();

        // 1. Submitted declaration, no proof row (NOT_STARTED)
        emp1 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-101", "alice@acme.com", "Alice", "Archer");
        declare(emp1);

        // 2. Submitted declaration, proof in DRAFT status
        emp2 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-102", "bob@acme.com", "Bob", "Baker");
        declare(emp2);
        actAs(emp2);
        ProofResponse p2 = proofService.readOwn(fy);
        inTransaction(() -> {
            for (ProofItemResponse item : p2.items()) {
                EmployeeProofItem itemEntity =
                        proofItemRepository.findById(item.id()).orElseThrow();
                itemEntity.setClaimedAmount(itemEntity.getDeclaredAmount());
                proofItemRepository.save(itemEntity);
            }
            return null;
        });

        // 3. Submitted declaration, proof in SUBMITTED status
        emp3 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-103", "charlie@acme.com", "Charlie", "Clark");
        declare(emp3);
        actAs(emp3);
        ProofResponse p3 = proofService.readOwn(fy);
        inTransaction(() -> {
            EmployeeProofOfInvestment proof =
                    proofRepository.findByTenantIdAndId(TENANT_A, p3.id()).orElseThrow();
            proof.setStatus(ProofStatus.SUBMITTED);
            proof.setSubmittedAt(Instant.now());
            proofRepository.save(proof);
            for (ProofItemResponse item : p3.items()) {
                EmployeeProofItem itemEntity =
                        proofItemRepository.findById(item.id()).orElseThrow();
                itemEntity.setClaimedAmount(itemEntity.getDeclaredAmount());
                proofItemRepository.save(itemEntity);
            }
            return null;
        });

        // 4. Submitted declaration, proof APPROVED with approved amounts populated
        emp4 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-104", "diana@acme.com", "Diana", "Davis");
        declare(emp4);
        actAs(emp4);
        ProofResponse p4 = proofService.readOwn(fy);
        inTransaction(() -> {
            EmployeeProofOfInvestment proof =
                    proofRepository.findByTenantIdAndId(TENANT_A, p4.id()).orElseThrow();
            proof.setStatus(ProofStatus.APPROVED);
            proofRepository.save(proof);
            for (ProofItemResponse item : p4.items()) {
                EmployeeProofItem itemEntity =
                        proofItemRepository.findById(item.id()).orElseThrow();
                itemEntity.setStatus(ProofItemStatus.APPROVED);
                itemEntity.setClaimedAmount(itemEntity.getDeclaredAmount());
                itemEntity.setApprovedAmount(itemEntity.getDeclaredAmount());
                proofItemRepository.save(itemEntity);
            }
            return null;
        });

        // 5. Submitted declaration, proof REJECTED
        emp5 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-105", "evan@acme.com", "Evan", "Evans");
        declare(emp5);
        actAs(emp5);
        ProofResponse p5 = proofService.readOwn(fy);
        inTransaction(() -> {
            EmployeeProofOfInvestment proof =
                    proofRepository.findByTenantIdAndId(TENANT_A, p5.id()).orElseThrow();
            proof.setStatus(ProofStatus.REJECTED);
            proofRepository.save(proof);
            for (ProofItemResponse item : p5.items()) {
                EmployeeProofItem itemEntity =
                        proofItemRepository.findById(item.id()).orElseThrow();
                itemEntity.setClaimedAmount(itemEntity.getDeclaredAmount());
                proofItemRepository.save(itemEntity);
            }
            return null;
        });
    }

    @Test
    @DisplayName(
            "Acceptance: chase list displays NOT_STARTED, DRAFT, SUBMITTED, APPROVED, REJECTED with correct totals")
    void chaseListDisplaysAllStatusesWithTotals() throws Exception {
        seedScenarioEmployees();

        Page<ProofChaseRow> rows = proofChaseService.list(fy, null, null, PageRequest.of(0, 25));
        assertThat(rows.getTotalElements()).isEqualTo(5);

        // Verify each status and total via service
        ProofChaseRow r1 = rows.stream()
                .filter(r -> r.employeeId().equals(emp1))
                .findFirst()
                .orElseThrow();
        assertThat(r1.proofStatus()).isEqualTo("NOT_STARTED");
        assertThat(r1.proofId()).isNull();
        assertThat(r1.claimedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r1.approvedTotal()).isNull();

        ProofChaseRow r2 = rows.stream()
                .filter(r -> r.employeeId().equals(emp2))
                .findFirst()
                .orElseThrow();
        assertThat(r2.proofStatus()).isEqualTo("DRAFT");
        assertThat(r2.claimedTotal()).isEqualByComparingTo(new BigDecimal("350000.0000"));
        assertThat(r2.approvedTotal()).isNull();

        ProofChaseRow r3 = rows.stream()
                .filter(r -> r.employeeId().equals(emp3))
                .findFirst()
                .orElseThrow();
        assertThat(r3.proofStatus()).isEqualTo("SUBMITTED");
        assertThat(r3.claimedTotal()).isEqualByComparingTo(new BigDecimal("350000.0000"));
        assertThat(r3.approvedTotal()).isNull();

        ProofChaseRow r4 = rows.stream()
                .filter(r -> r.employeeId().equals(emp4))
                .findFirst()
                .orElseThrow();
        assertThat(r4.proofStatus()).isEqualTo("APPROVED");
        assertThat(r4.claimedTotal()).isEqualByComparingTo(new BigDecimal("350000.0000"));
        assertThat(r4.approvedTotal()).isEqualByComparingTo(new BigDecimal("350000.0000"));

        ProofChaseRow r5 = rows.stream()
                .filter(r -> r.employeeId().equals(emp5))
                .findFirst()
                .orElseThrow();
        assertThat(r5.proofStatus()).isEqualTo("REJECTED");
        assertThat(r5.claimedTotal()).isEqualByComparingTo(new BigDecimal("350000.0000"));
        assertThat(r5.approvedTotal()).isNull();

        // Verify via HTTP MockMvc
        mvc.perform(get(BASE_URL).param("fy", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.total_elements").value(5))
                .andExpect(jsonPath("$.data.content", hasSize(5)));
    }

    @Test
    @DisplayName("Acceptance: status filter narrows list accurately")
    void statusFilterNarrowsResults() throws Exception {
        seedScenarioEmployees();

        // Filter NOT_STARTED
        mvc.perform(get(BASE_URL).param("fy", fy).param("status", "NOT_STARTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp1.toString()))
                .andExpect(jsonPath("$.data.content[0].proof_status").value("NOT_STARTED"));

        // Filter DRAFT
        mvc.perform(get(BASE_URL).param("fy", fy).param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp2.toString()))
                .andExpect(jsonPath("$.data.content[0].proof_status").value("DRAFT"));

        // Filter SUBMITTED
        mvc.perform(get(BASE_URL).param("fy", fy).param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp3.toString()))
                .andExpect(jsonPath("$.data.content[0].proof_status").value("SUBMITTED"));

        // Filter APPROVED
        mvc.perform(get(BASE_URL).param("fy", fy).param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp4.toString()))
                .andExpect(jsonPath("$.data.content[0].proof_status").value("APPROVED"));

        // Filter REJECTED
        mvc.perform(get(BASE_URL).param("fy", fy).param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp5.toString()))
                .andExpect(jsonPath("$.data.content[0].proof_status").value("REJECTED"));
    }

    @Test
    @DisplayName("Acceptance: search prefix narrows results by employee number and name")
    void searchPrefixNarrowsResults() throws Exception {
        seedScenarioEmployees();

        // Search by number prefix EMP-101
        mvc.perform(get(BASE_URL).param("fy", fy).param("search", "EMP-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp1.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("Alice Archer"));

        // Search by name prefix Diana
        mvc.perform(get(BASE_URL).param("fy", fy).param("search", "Diana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp4.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("Diana Davis"));

        // Search by last name prefix Baker
        mvc.perform(get(BASE_URL).param("fy", fy).param("search", "Baker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(emp2.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("Bob Baker"));
    }

    @Test
    @DisplayName("Acceptance: summary counts match real status counts with due date and open flag")
    void summaryCountsMatchRealData() throws Exception {
        seedScenarioEmployees();

        ProofChaseSummary summary = proofChaseService.summary(fy);
        assertThat(summary.notStarted()).isEqualTo(1);
        assertThat(summary.draft()).isEqualTo(1);
        assertThat(summary.submitted()).isEqualTo(1);
        assertThat(summary.approved()).isEqualTo(1);
        assertThat(summary.rejected()).isEqualTo(1);
        assertThat(summary.proofOpen()).isTrue();
        assertThat(summary.dueDate()).isEqualTo(today().plusDays(10));

        mvc.perform(get(BASE_URL + "/summary").param("fy", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.not_started").value(1))
                .andExpect(jsonPath("$.data.draft").value(1))
                .andExpect(jsonPath("$.data.submitted").value(1))
                .andExpect(jsonPath("$.data.approved").value(1))
                .andExpect(jsonPath("$.data.rejected").value(1))
                .andExpect(jsonPath("$.data.proof_open").value(true))
                .andExpect(
                        jsonPath("$.data.due_date").value(today().plusDays(10).toString()));
    }

    @Test
    @DisplayName("Acceptance: paging clamps size 500 to 100 and negative page to 0")
    void pagingClamping() throws Exception {
        seedScenarioEmployees();

        mvc.perform(get(BASE_URL).param("fy", fy).param("page", "-3").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageable.page_size").value(100))
                .andExpect(jsonPath("$.data.pageable.page_number").value(0));
    }

    @Test
    @DisplayName("Acceptance: caller without payroll.proof.read gets 403 Forbidden")
    void forbiddenWithoutPermission() throws Exception {
        seedScenarioEmployees();

        given(permissionService.holds("payroll.proof.read")).willReturn(false);

        mvc.perform(get(BASE_URL).param("fy", fy))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(get(BASE_URL + "/summary").param("fy", fy))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Acceptance: invalid financial year format returns 400 BAD_REQUEST")
    void invalidFinancialYearReturnsBadRequest() throws Exception {
        seedScenarioEmployees();

        mvc.perform(get(BASE_URL).param("fy", "invalid-fy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(get(BASE_URL + "/summary").param("fy", "invalid-fy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
