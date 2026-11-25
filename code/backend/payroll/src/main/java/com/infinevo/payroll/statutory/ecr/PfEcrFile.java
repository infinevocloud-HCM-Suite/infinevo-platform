package com.infinevo.payroll.statutory.ecr;

import org.springframework.http.MediaType;

/**
 * Exported ECR file container with filename, media type and payload bytes (W-87).
 */
public record PfEcrFile(String filename, MediaType mediaType, byte[] content) {}
