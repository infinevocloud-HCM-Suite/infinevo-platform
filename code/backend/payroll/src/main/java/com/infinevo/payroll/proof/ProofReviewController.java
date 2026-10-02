package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for proof-of-investment review, item decisions, final decisions, and comments
 * by HR/reviewer (W-34.2 spec §4).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/proof-of-investment")
public class ProofReviewController extends ProofControllerSupport {

    private final ProofReviewService reviewService;
    private final ProofCommentService commentService;

    public ProofReviewController(ProofReviewService reviewService, ProofCommentService commentService) {
        this.reviewService = Objects.requireNonNull(reviewService, "reviewService must not be null");
        this.commentService = Objects.requireNonNull(commentService, "commentService must not be null");
    }

    @GetMapping("/{proofId}/review")
    @RequiresAction("payroll.proof.review")
    public ApiResponse<ProofReviewResponse> getReview(@PathVariable("proofId") UUID proofId) {
        return ApiResponse.ok("Proof review retrieved successfully", reviewService.review(proofId));
    }

    @PostMapping("/{proofId}/items/{itemId}/decide")
    @RequiresAction("payroll.proof.review")
    public ApiResponse<ProofReviewItemResponse> decideItem(
            @PathVariable("proofId") UUID proofId,
            @PathVariable("itemId") UUID itemId,
            @RequestBody ProofItemDecisionRequest request) {
        return ApiResponse.ok(
                "Item decision recorded successfully", reviewService.decideItem(proofId, itemId, request));
    }

    @PostMapping("/{proofId}/final")
    @RequiresAction("payroll.proof.review")
    public ApiResponse<ProofReviewResponse> decideFinal(
            @PathVariable("proofId") UUID proofId, @RequestBody ProofFinalDecisionRequest request) {
        return ApiResponse.ok("Final decision recorded successfully", reviewService.decideFinal(proofId, request));
    }

    @GetMapping("/{proofId}/items/{itemId}/comments")
    @RequiresAction("payroll.proof.review")
    public ApiResponse<List<ProofCommentResponse>> listComments(
            @PathVariable("proofId") UUID proofId, @PathVariable("itemId") UUID itemId) {
        return ApiResponse.ok(
                "Comments retrieved successfully", requireCommentService().listForReviewer(proofId, itemId));
    }

    @PostMapping("/{proofId}/items/{itemId}/comments")
    @RequiresAction("payroll.proof.review")
    public ResponseEntity<ApiResponse<ProofCommentResponse>> addComment(
            @PathVariable("proofId") UUID proofId,
            @PathVariable("itemId") UUID itemId,
            @RequestBody ProofCommentRequest request) {
        ProofCommentResponse created = requireCommentService().addForReviewer(proofId, itemId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Comment added successfully", created));
    }

    private ProofCommentService requireCommentService() {
        return Objects.requireNonNull(commentService, "commentService must not be null");
    }
}
