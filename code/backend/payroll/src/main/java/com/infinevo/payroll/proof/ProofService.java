package com.infinevo.payroll.proof;

import com.infinevo.core.document.DocumentService;
import java.io.InputStream;
import java.util.UUID;

/**
 * Employee proof of investment: fill in, attach files, submit; and the officer's read (W-34.1).
 *
 * <p>The {@code *Own} methods act for the logged-in employee, resolved through
 * {@code EmployeeService.currentEmployee()}: no method takes the caller's employee id, so there is
 * no id to swap. Every method is tenant-bound.
 */
public interface ProofService {

    /** The caller's proof for the year, created (as {@code DRAFT}) with its items on first read. */
    ProofResponse readOwn(String financialYear);

    /** Sets the claimed amount and note on one item. */
    ProofItemResponse updateItemOwn(String financialYear, UUID itemId, ProofItemUpdateRequest request);

    /** Stores a file through core and links it to the item. */
    ProofDocumentResponse attachOwn(String financialYear, UUID itemId, String fileName, InputStream content);

    /** Unlinks a file from the item and soft-deletes it. */
    void detachOwn(String financialYear, UUID itemId, UUID documentId);

    /** The bytes of a file linked to one of the caller's items; otherwise not found. */
    DocumentService.DocumentContent openDocumentOwn(String financialYear, UUID itemId, UUID documentId);

    /** {@code DRAFT} or {@code REJECTED} to {@code SUBMITTED}; publishes {@link ProofSubmittedEvent}. */
    ProofResponse submitOwn(String financialYear);

    /** Any employee's proof, for an officer. Never creates or changes anything. */
    ProofResponse read(UUID employeeId, String financialYear);

    /** A file of any employee's proof, for an officer. */
    DocumentService.DocumentContent openDocument(UUID employeeId, String financialYear, UUID itemId, UUID documentId);

    /** Whether the proof window for the year is open today. */
    boolean proofOpen(String financialYear);
}
