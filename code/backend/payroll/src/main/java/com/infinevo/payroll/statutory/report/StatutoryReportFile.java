package com.infinevo.payroll.statutory.report;

import org.springframework.http.MediaType;

/**
 * Encapsulates generated report bytes and metadata for HTTP response headers (W-95).
 *
 * @param content raw report bytes
 * @param filename download attachment filename
 * @param mediaType response content type (XLSX or CSV)
 */
public record StatutoryReportFile(byte[] content, String filename, MediaType mediaType) {}
