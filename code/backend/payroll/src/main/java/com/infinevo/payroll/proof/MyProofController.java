package com.infinevo.payroll.proof;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The employee's own proof of investment (W-34.1 spec section 4).
 *
 * <p>Nothing here names an employee: the service resolves the caller. A document id that is not on the
 * named item of the caller's own proof is {@code 404}, so no one downloads another employee's file by
 * guessing an id.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/me/proof-of-investment")
public class MyProofController extends ProofControllerSupport {

    private final ProofService proofService;
    private final ProofCommentService commentService;

    public MyProofController(ProofService proofService, ProofCommentService commentService) {
        this.proofService = Objects.requireNonNull(proofService, "proofService must not be null");
        this.commentService = Objects.requireNonNull(commentService, "commentService must not be null");
    }

    @GetMapping("/{fy}")
    @RequiresAction("payroll.proof.read_own")
    public ApiResponse<ProofResponse> get(@PathVariable("fy") String financialYear) {
        return ApiResponse.ok("Proof of investment retrieved successfully", proofService.readOwn(financialYear));
    }

    @PutMapping("/{fy}/items/{itemId}")
    @RequiresAction("payroll.proof.submit_own")
    public ApiResponse<ProofItemResponse> updateItem(
            @PathVariable("fy") String financialYear,
            @PathVariable("itemId") UUID itemId,
            @RequestBody ProofItemUpdateRequest request) {
        return ApiResponse.ok(
                "Proof item updated successfully", proofService.updateItemOwn(financialYear, itemId, request));
    }

    @PostMapping(value = "/{fy}/items/{itemId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAction("payroll.proof.submit_own")
    public ResponseEntity<ApiResponse<ProofDocumentResponse>> attach(
            @PathVariable("fy") String financialYear,
            @PathVariable("itemId") UUID itemId,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        ProofDocumentResponse created;
        try (InputStream content = file.getInputStream()) {
            created = proofService.attachOwn(financialYear, itemId, file.getOriginalFilename(), content);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("File attached successfully", created));
    }

    @DeleteMapping("/{fy}/items/{itemId}/documents/{documentId}")
    @RequiresAction("payroll.proof.submit_own")
    public ResponseEntity<Void> detach(
            @PathVariable("fy") String financialYear,
            @PathVariable("itemId") UUID itemId,
            @PathVariable("documentId") UUID documentId) {
        proofService.detachOwn(financialYear, itemId, documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{fy}/items/{itemId}/documents/{documentId}")
    @RequiresAction("payroll.proof.read_own")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable("fy") String financialYear,
            @PathVariable("itemId") UUID itemId,
            @PathVariable("documentId") UUID documentId) {
        return file(proofService.openDocumentOwn(financialYear, itemId, documentId));
    }

    @PostMapping("/{fy}/submit")
    @RequiresAction("payroll.proof.submit_own")
    public ApiResponse<ProofResponse> submit(@PathVariable("fy") String financialYear) {
        return ApiResponse.ok("Proof of investment submitted successfully", proofService.submitOwn(financialYear));
    }

    @GetMapping("/{fy}/items/{itemId}/comments")
    @RequiresAction("payroll.proof.read_own")
    public ApiResponse<List<ProofCommentResponse>> listComments(
            @PathVariable("fy") String financialYear, @PathVariable("itemId") UUID itemId) {
        return ApiResponse.ok(
                "Comments retrieved successfully", requireCommentService().listForOwn(financialYear, itemId));
    }

    @PostMapping("/{fy}/items/{itemId}/comments")
    @RequiresAction("payroll.proof.submit_own")
    public ResponseEntity<ApiResponse<ProofCommentResponse>> addComment(
            @PathVariable("fy") String financialYear,
            @PathVariable("itemId") UUID itemId,
            @RequestBody ProofCommentRequest request) {
        ProofCommentResponse created = requireCommentService().addForOwn(financialYear, itemId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Comment added successfully", created));
    }

    private ProofCommentService requireCommentService() {
        return Objects.requireNonNull(commentService, "commentService must not be null");
    }
}
