package com.infinevo.payroll.payrun.bankfile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Generates bank salary payment export files from finalized pay runs (W-88).
 */
public interface BankFileService {

    /**
     * Extracts and validates the bank payment rows for an APPROVED or PAID pay run.
     * Excludes employees with non-bank payment mode, missing bank details, blank IFSC/account, or zero net pay.
     *
     * @param payrunId the pay run identifier
     * @return sorted list of bank payment rows
     * @throws BankFileInvalidStatusException if pay run is not APPROVED or PAID
     * @throws com.infinevo.payroll.payrun.PayRunNotFoundException if pay run not found
     */
    List<BankFileRow> getRows(UUID payrunId);

    /**
     * Generates an RFC 4180 CSV byte array and omission metadata for the pay run.
     *
     * @param payrunId the pay run identifier
     * @return BankFileResult with UTF-8 BOM CSV bytes and omission stats
     * @throws BankFileInvalidStatusException if pay run is not APPROVED or PAID
     * @throws com.infinevo.payroll.payrun.PayRunNotFoundException if pay run not found
     */
    BankFileResult generate(UUID payrunId);

    /**
     * One pass over an APPROVED or PAID run with W-88's omission rules (W-88.2): the file rows sorted by employee
     * number, the employees they name, who was left out and why, and the net pay of the run and of the selection.
     *
     * @param selection the employees to consider, or {@code null} / empty for everyone included in the run; an id
     *     that is not included in the run is an {@link IllegalArgumentException}
     */
    BankFileRun run(UUID payrunId, Set<UUID> selection);
}
