package com.infinevo.payroll.form16;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.form16.PartATestFixtures.FY;
import static com.infinevo.payroll.form16.PartATestFixtures.count;
import static com.infinevo.payroll.form16.PartATestFixtures.insertPan;
import static com.infinevo.payroll.form16.PartATestFixtures.pdf;
import static com.infinevo.payroll.form16.PartATestFixtures.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.document.DocumentController;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.proof.ProofTestDocuments;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-36.5 §7 — the acceptance test. The Part A ZIP is uploaded through the controller; certificates are
 * filed as real {@code core.document} and {@code payroll.form16_part_a} rows; the employee's link is
 * signed by core's real {@code DocumentLinkServiceImpl} and resolves to the bytes that were uploaded.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@Import(ProofTestDocuments.class)
@EnabledIfDockerAvailable
class Form16PartAIT extends AbstractIntegrationTest {

    private static final Path WORK_ROOT;

    static {
        try {
            WORK_ROOT = Files.createTempDirectory("form16-part-a-it-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void workDir(DynamicPropertyRegistry registry) {
        registry.add("payroll.form16.part-a.work-dir", WORK_ROOT::toString);
    }

    private static final String PAN_A = "ABCDE1234F";
    private static final String PAN_B = "PQRST5678K";

    @Autowired
    private Form16PartAService service;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentLinkService documentLinkService;

    @Autowired
    private Form16PartARepository partARepository;

    @Autowired
    private EmployeeIdentificationService identificationService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mvc;
    private UUID employeeA;
    private UUID employeeB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayrollTestSchema.apply();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();
        employeeA = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-PA-01", "asha@acme.com", "Asha", "Rao");
        employeeB = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-PA-02", "bala@acme.com", "Bala", "Iyer");
        insertPan(TENANT_A, employeeA, PAN_A);
        insertPan(TENANT_A, employeeB, PAN_B);
        TenantContext.set(TENANT_A);
        mvc = MockMvcBuilders.standaloneSetup(new Form16PartAController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("three PDFs, two PANs known: matched 2, unmatched the third; each /me link opens the same bytes")
    void acceptance() throws Exception {
        byte[] pdfA = pdf("asha");
        byte[] pdfB = pdf("bala");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PAN_A + "_2026-27.pdf", pdfA);
        entries.put("form16_" + PAN_B.toLowerCase() + ".pdf", pdfB);
        entries.put("ZZZZZ9999Z.pdf", pdf("nobody"));

        JsonNode data = upload(zip(entries));

        assertThat(data.get("matched").asInt()).isEqualTo(2);
        assertThat(data.get("unmatched")).hasSize(1);
        assertThat(data.get("unmatched").get(0).asText()).isEqualTo("ZZZZZ9999Z.pdf");
        assertThat(data.get("skipped")).isEmpty();
        assertWorkDirEmpty();

        assertOwnLinkOpens(employeeA, pdfA);
        assertOwnLinkOpens(employeeB, pdfB);

        mvc.perform(get("/api/v1/payroll/form16/{fy}/part-a", FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].employee_id").exists())
                .andExpect(jsonPath("$.data[0].document_id").exists())
                .andExpect(jsonPath("$.data[0].uploaded_at").exists());

        assertThat(count(
                        "SELECT count(*) FROM core.document WHERE tenant_id = ? AND kind = 'FORM16_PART_A'"
                                + " AND file_name = 'Form16-PartA-2026-2027.pdf'",
                        TENANT_A))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("a re-upload leaves one active row and soft-deletes the old document")
    void reUploadSupersedes() throws Exception {
        upload(zip(Map.of(PAN_A + ".pdf", pdf("first"))));
        UUID firstDocument = service.list(FY).get(0).documentId();

        byte[] second = pdf("second");
        JsonNode data = upload(zip(Map.of("again_" + PAN_A + ".pdf", second)));
        assertThat(data.get("matched").asInt()).isEqualTo(1);

        assertThat(count(
                        "SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ? AND employee_id = ?",
                        TENANT_A,
                        employeeA))
                .isEqualTo(2);
        assertThat(count(
                        "SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ? AND employee_id = ?"
                                + " AND is_active",
                        TENANT_A,
                        employeeA))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM payroll.form16_part_a WHERE document_id = ? AND NOT is_active"
                                + " AND superseded_at IS NOT NULL",
                        firstDocument))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM core.document WHERE id = ? AND is_deleted", firstDocument))
                .isEqualTo(1);

        assertThat(service.list(FY)).hasSize(1);
        assertThat(service.list(FY).get(0).sourceFileName()).isEqualTo("again_" + PAN_A + ".pdf");
        assertOwnLinkOpens(employeeA, second);
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName("an encrypted ZIP is 400 ZIP_ENCRYPTED and files nothing")
    void encryptedZip() throws Exception {
        mvc.perform(multipart("/api/v1/payroll/form16/{fy}/part-a", FY).file(zipPart(PartATestFixtures.encryptedZip())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ZIP_ENCRYPTED"));
        assertThat(count("SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ?", TENANT_A))
                .isZero();
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName("2,001 entries is 400 and files nothing; 2,000 is accepted")
    void tooManyEntries() throws Exception {
        mvc.perform(multipart("/api/v1/payroll/form16/{fy}/part-a", FY)
                        .file(zipPart(PartATestFixtures.zipOfEntries(2_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ZIP_LIMIT_EXCEEDED"));
        assertWorkDirEmpty();

        JsonNode data = upload(PartATestFixtures.zipOfEntries(2_000));
        assertThat(data.get("matched").asInt()).isZero();
        assertThat(data.get("skipped")).hasSize(2_000);
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName("a file that is not a ZIP is 400 ZIP_INVALID; one over 50 MB is 413")
    void notAZipAndTooLarge() throws Exception {
        mvc.perform(multipart("/api/v1/payroll/form16/{fy}/part-a", FY).file(zipPart(pdf("not a zip"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ZIP_INVALID"));

        MockMultipartFile huge = new MockMultipartFile("file", "partA.zip", "application/zip", new byte[] {1}) {
            @Override
            public long getSize() {
                return 50L * 1024 * 1024 + 1;
            }
        };
        mvc.perform(multipart("/api/v1/payroll/form16/{fy}/part-a", FY).file(huge))
                .andExpect(status().isPayloadTooLarge());
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName("readme, __MACOSX, traversal and a PAN named twice are skipped; a PDF with no PAN is unmatched")
    void skipsAndUnmatched() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("readme.txt", "hello".getBytes());
        entries.put("__MACOSX/._" + PAN_A + ".pdf", pdf("mac"));
        entries.put("../../" + PAN_A + ".pdf", pdf("evil"));
        entries.put(PAN_B + "_one.pdf", pdf("b1"));
        entries.put(PAN_B + "_two.pdf", pdf("b2"));
        entries.put("certificate.pdf", pdf("no pan"));

        JsonNode data = upload(zip(entries));

        assertThat(data.get("matched").asInt()).isZero();
        assertThat(data.get("unmatched")).hasSize(1);
        assertThat(data.get("unmatched").get(0).asText()).isEqualTo("certificate.pdf");
        assertThat(data.get("skipped")).hasSize(5);
        assertThat(count("SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ?", TENANT_A))
                .isZero();
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName("a PAN held by two live employees is skipped, not unmatched, and files nothing for either")
    void ambiguousPanIsSkipped() throws Exception {
        UUID employeeC =
                TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-PA-03", "chitra@acme.com", "Chitra", "Das");
        insertPan(TENANT_A, employeeC, PAN_B);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PAN_A + ".pdf", pdf("asha"));
        entries.put(PAN_B + ".pdf", pdf("bala or chitra"));

        JsonNode data = upload(zip(entries));

        assertThat(data.get("matched").asInt()).isEqualTo(1);
        assertThat(data.get("unmatched")).isEmpty();
        assertThat(data.get("skipped")).hasSize(1);
        assertThat(data.get("skipped").get(0).asText()).isEqualTo(PAN_B + ".pdf");
        assertThat(count(
                        "SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ? AND employee_id IN (?, ?)",
                        TENANT_A,
                        employeeB,
                        employeeC))
                .isZero();
        assertThat(count(
                        "SELECT count(*) FROM core.document WHERE tenant_id = ? AND employee_id IN (?, ?)",
                        TENANT_A,
                        employeeB,
                        employeeC))
                .isZero();
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName(
            "a ZIP that inflates past the unpacked limit is 400 ZIP_LIMIT_EXCEEDED, files nothing, leaves no temp file")
    void unpackedSizeLimit() throws Exception {
        long limit = 64 * 1024;
        Form16PartAServiceImpl lowLimit = new Form16PartAServiceImpl(
                partARepository,
                documentService,
                documentLinkService,
                identificationService,
                employeeService,
                transactionManager,
                WORK_ROOT.toString(),
                Clock.systemUTC(),
                limit);
        MockMvc guarded = MockMvcBuilders.standaloneSetup(new Form16PartAController(lowLimit))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        // Two candidates of 40 KB each: neither alone crosses 64 KB, together they do — the count is
        // cumulative and taken from inflated bytes. Zeros deflate to a few hundred bytes on the wire.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PAN_A + ".pdf", new byte[40 * 1024]);
        entries.put(PAN_B + ".pdf", new byte[40 * 1024]);
        byte[] bomb = zip(entries);
        assertThat(bomb.length).as("highly compressible").isLessThan((int) limit / 4);

        guarded.perform(multipart("/api/v1/payroll/form16/{fy}/part-a", FY).file(zipPart(bomb)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ZIP_LIMIT_EXCEEDED"));

        assertThat(count("SELECT count(*) FROM payroll.form16_part_a WHERE tenant_id = ?", TENANT_A))
                .isZero();
        assertThat(count("SELECT count(*) FROM core.document WHERE tenant_id = ?", TENANT_A))
                .isZero();
        assertWorkDirEmpty();
    }

    @Test
    @DisplayName("GET /me with nothing on file is 404")
    void ownNotFound() throws Exception {
        PayrollTestApp.CURRENT_EMPLOYEE.set(
                PayrollTestSchema.createTestEmployee(employeeA, TENANT_A, "EMP-PA-01", "Asha", "Rao", null));
        mvc.perform(get("/api/v1/me/form16/{fy}/part-a", FY)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/documents with kind FORM16_PART_A is 400")
    void documentsEndpointRefusesPartA() throws Exception {
        MockMvc documents = MockMvcBuilders.standaloneSetup(new DocumentController(documentService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        documents
                .perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", PAN_A + ".pdf", "application/pdf", pdf("forged")))
                        .param("kind", "FORM16_PART_A")
                        .param("employeeId", employeeA.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.kind").exists());
        assertThat(count("SELECT count(*) FROM core.document WHERE tenant_id = ?", TENANT_A))
                .isZero();
    }

    private JsonNode upload(byte[] zipBytes) throws Exception {
        MvcResult result = mvc.perform(
                        multipart("/api/v1/payroll/form16/{fy}/part-a", FY).file(zipPart(zipBytes)))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    private static MockMultipartFile zipPart(byte[] bytes) {
        return new MockMultipartFile("file", "partA.zip", "application/zip", bytes);
    }

    /** The employee's /me read returns a link core signed for this tenant and document, whose bytes match. */
    private void assertOwnLinkOpens(UUID employeeId, byte[] expected) throws Exception {
        PayrollTestApp.CURRENT_EMPLOYEE.set(
                PayrollTestSchema.createTestEmployee(employeeId, TENANT_A, "EMP", "Test", "Employee", null));
        try {
            MvcResult result = mvc.perform(get("/api/v1/me/form16/{fy}/part-a", FY))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode data = mapper.readTree(result.getResponse().getContentAsByteArray())
                    .get("data");
            UUID documentId = UUID.fromString(data.get("document_id").asText());
            String link = data.get("link").asText();
            assertThat(link).startsWith("/api/v1/documents/download?t=");

            DocumentLinkService.LinkClaims claims = documentLinkService
                    .verify(link.substring(link.indexOf("?t=") + 3))
                    .orElseThrow();
            assertThat(claims.documentId()).isEqualTo(documentId);
            assertThat(claims.tenantId()).isEqualTo(TENANT_A);

            try (InputStream content = documentService.open(claims.documentId()).content()) {
                assertThat(content.readAllBytes()).isEqualTo(expected);
            }
            assertThat(documentService.get(documentId).employeeId()).isEqualTo(employeeId);
        } finally {
            PayrollTestApp.CURRENT_EMPLOYEE.remove();
        }
    }

    private static void assertWorkDirEmpty() throws IOException {
        try (Stream<Path> children = Files.list(WORK_ROOT)) {
            assertThat(children).isEmpty();
        }
    }
}
