package com.infinevo.payroll.proof;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link ProofReviewController} (W-34.2 spec §4).
 */
class ProofReviewControllerTest {

    private static final String BASE_URL = "/api/v1/payroll/proof-of-investment";

    private ProofReviewService reviewService;
    private ProofCommentService commentService;
    private PermissionService permissionService;
    private MockMvc mvc;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        reviewService = mock(ProofReviewService.class);
        commentService = mock(ProofCommentService.class);
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

        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var converter = new MappingJackson2HttpMessageConverter(mapper);
        var controller = new ProofReviewController(reviewService, commentService);

        mvc = MockMvcBuilders.standaloneSetup(proxy(controller))
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("GET /{proofId}/review returns 200 with proof review response")
    void getReviewSuccess() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        ProofReviewItemResponse item = new ProofReviewItemResponse(
                itemId,
                ProofSourceKind.SECTION_6A,
                "PPF",
                new BigDecimal("150000.0000"),
                new BigDecimal("150000.0000"),
                null,
                ProofItemStatus.PENDING,
                "Annual deposit",
                null,
                List.of(),
                stepId,
                2L);

        ProofReviewResponse response = new ProofReviewResponse(
                proofId,
                empId,
                "2026-2027",
                ProofStatus.SUBMITTED,
                UUID.randomUUID(),
                null,
                Instant.now(),
                null,
                null,
                false,
                List.of(item));

        given(reviewService.review(proofId)).willReturn(response);

        mvc.perform(get(BASE_URL + "/{proofId}/review", proofId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(proofId.toString()))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.items[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.data.items[0].open_step_id").value(stepId.toString()))
                .andExpect(jsonPath("$.data.items[0].comment_count").value(2));

        verify(reviewService).review(proofId);
    }

    @Test
    @DisplayName("GET /{proofId}/review returns 403 when permission denied")
    void getReviewForbidden() throws Exception {
        UUID proofId = UUID.randomUUID();
        given(permissionService.holds("payroll.proof.review")).willReturn(false);

        mvc.perform(get(BASE_URL + "/{proofId}/review", proofId)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /{proofId}/review returns 404 when proof not found")
    void getReviewNotFound() throws Exception {
        UUID proofId = UUID.randomUUID();
        given(reviewService.review(proofId)).willThrow(new ProofNotFoundException("Proof not found"));

        mvc.perform(get(BASE_URL + "/{proofId}/review", proofId)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/decide returns 200 on APPROVE")
    void decideItemApprove() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofItemDecisionRequest req = new ProofItemDecisionRequest(
                ProofItemDecisionAction.APPROVE, new BigDecimal("100000.0000"), "Verified with receipt");

        ProofReviewItemResponse itemResp = new ProofReviewItemResponse(
                itemId,
                ProofSourceKind.SECTION_6A,
                "PPF",
                new BigDecimal("150000.0000"),
                new BigDecimal("150000.0000"),
                new BigDecimal("100000.0000"),
                ProofItemStatus.APPROVED,
                null,
                "Verified with receipt",
                List.of(),
                null,
                0L);

        given(reviewService.decideItem(eq(proofId), eq(itemId), any())).willReturn(itemResp);

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(itemId.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approved_amount").value(100000.0000));
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/decide returns 200 on DISALLOW")
    void decideItemDisallow() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofItemDecisionRequest req =
                new ProofItemDecisionRequest(ProofItemDecisionAction.DISALLOW, null, "Ineligible receipt");

        ProofReviewItemResponse itemResp = new ProofReviewItemResponse(
                itemId,
                ProofSourceKind.SECTION_6A,
                "PPF",
                new BigDecimal("150000.0000"),
                new BigDecimal("150000.0000"),
                BigDecimal.ZERO,
                ProofItemStatus.DISALLOWED,
                null,
                "Ineligible receipt",
                List.of(),
                null,
                1L);

        given(reviewService.decideItem(eq(proofId), eq(itemId), any())).willReturn(itemResp);

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("DISALLOWED"))
                .andExpect(jsonPath("$.data.approved_amount").value(0));
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/decide returns 200 on RETURN")
    void decideItemReturn() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofItemDecisionRequest req =
                new ProofItemDecisionRequest(ProofItemDecisionAction.RETURN, null, "Document blurry, please re-upload");

        ProofReviewItemResponse itemResp = new ProofReviewItemResponse(
                itemId,
                ProofSourceKind.SECTION_6A,
                "PPF",
                new BigDecimal("150000.0000"),
                new BigDecimal("150000.0000"),
                null,
                ProofItemStatus.RETURNED,
                null,
                "Document blurry, please re-upload",
                List.of(),
                null,
                1L);

        given(reviewService.decideItem(eq(proofId), eq(itemId), any())).willReturn(itemResp);

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.reviewer_note").value("Document blurry, please re-upload"));
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/decide returns 400 when validation fails")
    void decideItemValidationFailure() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofItemDecisionRequest req =
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("200000.0000"), null);

        given(reviewService.decideItem(eq(proofId), eq(itemId), any()))
                .willThrow(new ProofValidationException("Approved amount cannot exceed claimed amount"));

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Approved amount cannot exceed claimed amount"));
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/decide returns 409 NOT_UNDER_REVIEW when item not open")
    void decideItemNotUnderReview() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofItemDecisionRequest req =
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("10000.0000"), null);

        given(reviewService.decideItem(eq(proofId), eq(itemId), any()))
                .willThrow(new ProofConflictException(
                        ProofConflictException.NOT_UNDER_REVIEW, "Item is not currently under review"));

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_UNDER_REVIEW"));
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/decide returns 403 when not the assigned approver")
    void decideItemNotAssignee() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofItemDecisionRequest req =
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("10000.0000"), null);

        given(reviewService.decideItem(eq(proofId), eq(itemId), any()))
                .willThrow(new AccessDeniedException("Caller is not the assigned approver"));

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /{proofId}/final returns 200 on APPROVE")
    void decideFinalApprove() throws Exception {
        UUID proofId = UUID.randomUUID();
        ProofFinalDecisionRequest req = new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "All verified");

        ProofReviewResponse resp = new ProofReviewResponse(
                proofId,
                UUID.randomUUID(),
                "2026-2027",
                ProofStatus.APPROVED,
                UUID.randomUUID(),
                null,
                Instant.now(),
                Instant.now(),
                "All verified",
                false,
                List.of());

        given(reviewService.decideFinal(eq(proofId), any())).willReturn(resp);

        mvc.perform(post(BASE_URL + "/{proofId}/final", proofId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /{proofId}/final returns 409 ITEMS_UNDECIDED when items undecided")
    void decideFinalItemsUndecided() throws Exception {
        UUID proofId = UUID.randomUUID();
        ProofFinalDecisionRequest req = new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, null);

        given(reviewService.decideFinal(eq(proofId), any()))
                .willThrow(new ProofConflictException(
                        ProofConflictException.ITEMS_UNDECIDED, "Cannot decide until all items are decided"));

        mvc.perform(post(BASE_URL + "/{proofId}/final", proofId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEMS_UNDECIDED"));
    }

    @Test
    @DisplayName("POST /{proofId}/final returns 409 NOT_UNDER_REVIEW when final step not open")
    void decideFinalNotUnderReview() throws Exception {
        UUID proofId = UUID.randomUUID();
        ProofFinalDecisionRequest req = new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, null);

        given(reviewService.decideFinal(eq(proofId), any()))
                .willThrow(new ProofConflictException(
                        ProofConflictException.NOT_UNDER_REVIEW, "No open final approval step found"));

        mvc.perform(post(BASE_URL + "/{proofId}/final", proofId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_UNDER_REVIEW"));
    }

    @Test
    @DisplayName("GET /{proofId}/items/{itemId}/comments returns 200 with comments list")
    void listCommentsSuccess() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofCommentResponse comment = new ProofCommentResponse(
                UUID.randomUUID(),
                itemId,
                UUID.randomUUID(),
                ProofCommentRole.REVIEWER,
                "Please verify amount",
                Instant.now());

        given(commentService.listForReviewer(proofId, itemId)).willReturn(List.of(comment));

        mvc.perform(get(BASE_URL + "/{proofId}/items/{itemId}/comments", proofId, itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].body").value("Please verify amount"))
                .andExpect(jsonPath("$.data[0].author_role").value("REVIEWER"));
    }

    @Test
    @DisplayName("An employee, who does not hold payroll.proof.review, is refused 403 on every review endpoint")
    void everyReviewEndpointRefusesACallerWithoutTheAction() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        given(permissionService.holds("payroll.proof.review")).willReturn(false);
        String decide = mapper.writeValueAsString(
                new ProofItemDecisionRequest(ProofItemDecisionAction.APPROVE, new BigDecimal("10000.0000"), null));
        String finalDecision =
                mapper.writeValueAsString(new ProofFinalDecisionRequest(ProofFinalDecisionAction.APPROVE, "ok"));
        String comment = mapper.writeValueAsString(new ProofCommentRequest("hello"));

        mvc.perform(get(BASE_URL + "/{proofId}/review", proofId)).andExpect(status().isForbidden());
        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/decide", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decide))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE_URL + "/{proofId}/final", proofId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalDecision))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE_URL + "/{proofId}/items/{itemId}/comments", proofId, itemId))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/comments", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(comment))
                .andExpect(status().isForbidden());

        org.mockito.Mockito.verifyNoInteractions(reviewService, commentService);
    }

    @Test
    @DisplayName("POST /{proofId}/items/{itemId}/comments returns 201 with created comment")
    void addCommentSuccess() throws Exception {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ProofCommentRequest req = new ProofCommentRequest("Receipt re-uploaded");

        ProofCommentResponse comment = new ProofCommentResponse(
                UUID.randomUUID(),
                itemId,
                UUID.randomUUID(),
                ProofCommentRole.REVIEWER,
                "Receipt re-uploaded",
                Instant.now());

        given(commentService.addForReviewer(eq(proofId), eq(itemId), any())).willReturn(comment);

        mvc.perform(post(BASE_URL + "/{proofId}/items/{itemId}/comments", proofId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.body").value("Receipt re-uploaded"));
    }
}
