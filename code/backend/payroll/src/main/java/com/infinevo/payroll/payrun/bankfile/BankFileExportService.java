package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.document.DocumentService.DocumentContent;
import java.util.List;
import java.util.UUID;

/** Bank files made from a template for a pay run, and their history (W-88.2 spec section 4.4). */
public interface BankFileExportService {

    BankFilePreviewResponse preview(UUID payrunId, BankFileExportRequest request);

    /** Builds the file, stores it as a tenant EXPORT document and records it. */
    BankFileExportResponse generate(UUID payrunId, BankFileExportRequest request);

    List<BankFileExportResponse> list(UUID payrunId);

    /** The stored file, with the export row that names it. */
    Download download(UUID payrunId, UUID exportId);

    record Download(BankFileExportResponse export, DocumentContent content) {}
}
