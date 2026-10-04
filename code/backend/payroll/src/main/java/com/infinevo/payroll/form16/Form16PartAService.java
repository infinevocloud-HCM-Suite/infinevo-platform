package com.infinevo.payroll.form16;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Form 16 Part A: the officer's TRACES ZIP upload, filed per employee by PAN, and the two reads
 * (W-36.5 §3, §4). The tenant comes from {@code TenantContext}; no method takes one.
 */
public interface Form16PartAService {

    /**
     * Unpacks the ZIP and files each PDF against the live employee whose PAN its name carries. Each
     * certificate is filed in its own transaction, so one refused file does not undo the others.
     *
     * @throws IllegalArgumentException when {@code financialYear} is not {@code YYYY-YYYY}
     * @throws com.infinevo.payroll.form16.exception.PartAZipException not a ZIP, encrypted, or over the limits
     * @throws com.infinevo.payroll.form16.exception.PartATooLargeException over 50 MB
     */
    PartAUploadResult upload(String financialYear, MultipartFile file);

    /** The active certificates for a year in the bound tenant. */
    List<PartARow> list(String financialYear);

    /**
     * The caller's own active certificate for a year, with a signed link.
     *
     * @throws com.infinevo.payroll.form16.exception.PartANotFoundException none on file, or no employee
     */
    PartAOwn own(String financialYear);
}
