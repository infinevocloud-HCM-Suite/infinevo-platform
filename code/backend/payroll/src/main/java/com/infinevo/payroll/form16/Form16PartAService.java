package com.infinevo.payroll.form16;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for Form 16 Part A operations (W-36.5 §4):
 * upload TRACES ZIP, list uploaded certificates for a year, and employee self-service signed download link.
 */
public interface Form16PartAService {

    /**
     * Uploads the TRACES Form 16 Part A ZIP for a financial year, matches each PDF to an employee by PAN,
     * stores the file as a system document, and links it in payroll.form16_part_a (superseding any previous upload).
     *
     * @param financialYear the financial year (e.g. "2026-2027")
     * @param file the multipart ZIP file
     * @return the upload result containing counts and filenames of matched, unmatched, and skipped entries
     */
    PartAUploadResult upload(String financialYear, MultipartFile file);

    /**
     * Lists active Form 16 Part A certificates for a financial year in the bound tenant (officer view).
     *
     * @param financialYear the financial year
     * @return list of active certificate metadata items
     */
    List<PartAOfficerItem> list(String financialYear);

    /**
     * Resolves the authenticated employee's active Form 16 Part A certificate for the financial year
     * and generates an expiring signed link.
     *
     * @param financialYear the financial year
     * @return response with document ID, signed link URL, and expiry
     */
    PartAEmployeeResponse own(String financialYear);
}
