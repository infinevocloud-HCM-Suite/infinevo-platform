package com.infinevo.payroll.proof;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-34.1 — the HTTP contract of the two proof controllers: paths, status codes, the action each
 * endpoint demands, and the error body (spec section 4).
 */
class ProofControllerTest {

    private static final String FY = "2026-2027";
    private static final String ME = "/api/v1/me/proof-of-investment/" + FY;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    private ProofService proofService;
    private PermissionService permissionService;
    private MockMvc myMvc;
    private MockMvc officerMvc;

    @BeforeEach
    void setUp() {
        proofService = mock(ProofService.class);
        permissionService = mock(PermissionService.class);
        given(permissionService.holds(any())).willReturn(true);
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        // As Spring Boot configures it: dates are ISO strings, not timestamp arrays.
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var converter = new MappingJackson2HttpMessageConverter(mapper);
        var resources = new org.springframework.http.converter.ResourceHttpMessageConverter();
        myMvc = MockMvcBuilders.standaloneSetup(
                        proxy(new MyProofController(proofService, mock(ProofCommentService.class))))
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter, resources)
                .build();
        officerMvc = MockMvcBuilders.standaloneSetup(proxy(new ProofController(proofService)))
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter, resources)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return (T) factory.getProxy();
    }

    private ProofResponse sample() {
        ProofItemResponse item = new ProofItemResponse(
                itemId,
                ProofSourceKind.HOUSE_RENT,
                "House rent paid to A. Landlord",
                new BigDecimal("180000.0000"),
                null,
                null,
                ProofItemStatus.PENDING,
                null,
                null,
                List.of());
        return new ProofResponse(
                UUID.randomUUID(),
                employeeId,
                FY,
                ProofStatus.DRAFT,
                true,
                true,
                LocalDate.of(2027, 1, 31),
                true,
                null,
                null,
                null,
                List.of(item));
    }

    @Test
    @DisplayName("GET /me/proof-of-investment/{fy} answers 200 in the status/message/data envelope, snake_case")
    void getOwn() throws Exception {
        given(proofService.readOwn(FY)).willReturn(sample());

        myMvc.perform(get(ME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.financial_year").value(FY))
                .andExpect(jsonPath("$.data.proof_open").value(true))
                .andExpect(jsonPath("$.data.due_date").value("2027-01-31"))
                .andExpect(jsonPath("$.data.items[0].source_kind").value("HOUSE_RENT"))
                .andExpect(jsonPath("$.data.items[0].declared_amount").value(180000.0))
                .andExpect(jsonPath("$.data.items[0].documents").isEmpty());
    }

    @Test
    @DisplayName("PUT an item reads claimed_amount and employee_note, and answers 200")
    void updateItem() throws Exception {
        given(proofService.updateItemOwn(eq(FY), eq(itemId), any()))
                .willReturn(sample().items().get(0));

        myMvc.perform(put(ME + "/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimed_amount\": 1500.50, \"employee_note\": \"paid\"}"))
                .andExpect(status().isOk());

        verify(proofService)
                .updateItemOwn(eq(FY), eq(itemId), eq(new ProofItemUpdateRequest(new BigDecimal("1500.50"), "paid")));
    }

    @Test
    @DisplayName("POST a file answers 201 with metadata only")
    void upload() throws Exception {
        given(proofService.attachOwn(eq(FY), eq(itemId), eq("rent.pdf"), any()))
                .willReturn(new ProofDocumentResponse(documentId, "rent.pdf", "application/pdf", 12, Instant.now()));

        myMvc.perform(multipart(ME + "/items/" + itemId + "/documents")
                        .file(new MockMultipartFile("file", "rent.pdf", "application/pdf", "%PDF-1.4 x".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.document_id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.file_name").value("rent.pdf"))
                .andExpect(jsonPath("$.data.size_bytes").value(12));
    }

    @Test
    @DisplayName("Upload without a file part is 400; over the size limit is 413; a wrong type is 415")
    void uploadFailures() throws Exception {
        myMvc.perform(multipart(ME + "/items/" + itemId + "/documents")).andExpect(status().isBadRequest());
        verify(proofService, never()).attachOwn(any(), any(), any(), any());

        given(proofService.attachOwn(any(), any(), any(), any()))
                .willThrow(new DocumentService.TooLargeException(10L))
                .willThrow(new DocumentService.UnsupportedTypeException("Only PDF and images"));
        MockMultipartFile file = new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[] {1});

        myMvc.perform(multipart(ME + "/items/" + itemId + "/documents").file(file))
                .andExpect(status().isPayloadTooLarge());
        myMvc.perform(multipart(ME + "/items/" + itemId + "/documents").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("DELETE a file answers 204 with no body")
    void detach() throws Exception {
        myMvc.perform(delete(ME + "/items/" + itemId + "/documents/" + documentId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(proofService).detachOwn(FY, itemId, documentId);
    }

    @Test
    @DisplayName("A download is an attachment, never cached, never sniffed, with the stored content type")
    void download() throws Exception {
        DocumentResponse meta = new DocumentResponse(
                documentId,
                employeeId,
                DocumentKind.INVESTMENT_PROOF,
                null,
                "rent receipt.pdf",
                "application/pdf",
                5,
                "0".repeat(64),
                Instant.now(),
                "test");
        given(proofService.openDocumentOwn(FY, itemId, documentId))
                .willReturn(new DocumentService.DocumentContent(meta, new ByteArrayInputStream("hello".getBytes())));

        myMvc.perform(get(ME + "/items/" + itemId + "/documents/" + documentId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string("hello"));
    }

    @Test
    @DisplayName("POST submit answers 200")
    void submit() throws Exception {
        given(proofService.submitOwn(FY)).willReturn(sample());

        myMvc.perform(post(ME + "/submit")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A conflict is 409 with the reason as the code, and the missing items named")
    void conflictBody() throws Exception {
        UUID missing = UUID.randomUUID();
        given(proofService.submitOwn(FY))
                .willThrow(new ProofConflictException(
                        ProofConflictException.ATTACHMENT_REQUIRED, "Attach a file", List.of(missing)))
                .willThrow(new ProofConflictException(ProofConflictException.ALREADY_SUBMITTED, "Already"));

        myMvc.perform(post(ME + "/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.item_ids").value(missing.toString()));
        myMvc.perform(post(ME + "/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    @DisplayName("Not found is 404, bad values are 400, and a bad financial year or body is 400 too")
    void errorMapping() throws Exception {
        given(proofService.readOwn(FY)).willThrow(new ProofNotFoundException("No such item"));
        myMvc.perform(get(ME)).andExpect(status().isNotFound());

        given(proofService.updateItemOwn(any(), any(), any())).willThrow(new ProofValidationException("bad amount"));
        myMvc.perform(put(ME + "/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad amount"));

        given(proofService.readOwn("nonsense")).willThrow(new IllegalArgumentException("Invalid financial year"));
        myMvc.perform(get("/api/v1/me/proof-of-investment/nonsense")).andExpect(status().isBadRequest());

        myMvc.perform(put(ME + "/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
        myMvc.perform(get(ME + "/items/not-a-uuid/documents/" + documentId)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Each employee endpoint demands its own action; without it the answer is 403 and nothing runs")
    void employeeEndpointsAreGuarded() throws Exception {
        given(permissionService.holds(any())).willReturn(false);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1});

        myMvc.perform(get(ME)).andExpect(status().isForbidden());
        myMvc.perform(put(ME + "/items/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        myMvc.perform(multipart(ME + "/items/" + itemId + "/documents").file(file))
                .andExpect(status().isForbidden());
        myMvc.perform(delete(ME + "/items/" + itemId + "/documents/" + documentId))
                .andExpect(status().isForbidden());
        myMvc.perform(get(ME + "/items/" + itemId + "/documents/" + documentId)).andExpect(status().isForbidden());
        myMvc.perform(post(ME + "/submit")).andExpect(status().isForbidden());

        verify(proofService, never()).readOwn(any());
        verify(proofService, never()).submitOwn(any());
        verify(proofService, never()).attachOwn(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Reading needs read_own, changing needs submit_own: the two are separate grants")
    void readAndSubmitAreSeparate() throws Exception {
        given(permissionService.holds(any())).willReturn(false);
        given(permissionService.holds("payroll.proof.read_own")).willReturn(true);
        given(proofService.readOwn(FY)).willReturn(sample());

        myMvc.perform(get(ME)).andExpect(status().isOk());
        myMvc.perform(post(ME + "/submit")).andExpect(status().isForbidden());
        myMvc.perform(delete(ME + "/items/" + itemId + "/documents/" + documentId))
                .andExpect(status().isForbidden());

        given(permissionService.holds(any())).willReturn(false);
        given(permissionService.holds("payroll.proof.submit_own")).willReturn(true);
        myMvc.perform(get(ME)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The officer's endpoints demand payroll.proof.read and only read")
    void officerEndpoints() throws Exception {
        String base = "/api/v1/payroll/employees/" + employeeId + "/proof-of-investment/" + FY;
        given(proofService.read(employeeId, FY)).willReturn(sample());

        officerMvc.perform(get(base)).andExpect(status().isOk());
        officerMvc.perform(post(base + "/submit")).andExpect(status().is4xxClientError());
        officerMvc.perform(put(base + "/items/" + itemId)).andExpect(status().is4xxClientError());

        given(permissionService.holds(any())).willReturn(false);
        officerMvc.perform(get(base)).andExpect(status().isForbidden());
        officerMvc
                .perform(get(base + "/items/" + itemId + "/documents/" + documentId))
                .andExpect(status().isForbidden());

        // The employee's own grants do not open the officer's endpoints.
        given(permissionService.holds("payroll.proof.read_own")).willReturn(true);
        given(permissionService.holds("payroll.proof.submit_own")).willReturn(true);
        officerMvc.perform(get(base)).andExpect(status().isForbidden());
        verify(proofService, never()).openDocument(any(), any(), any(), any());

        doThrow(new ProofNotFoundException("none")).when(proofService).read(any(), any());
        given(permissionService.holds("payroll.proof.read")).willReturn(true);
        officerMvc.perform(get(base)).andExpect(status().isNotFound());
    }
}
