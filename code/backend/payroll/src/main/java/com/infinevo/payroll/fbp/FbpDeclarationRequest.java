package com.infinevo.payroll.fbp;

import java.util.List;

/**
 * Request payload for submitting or updating an FBP declaration (W-27.2).
 */
public record FbpDeclarationRequest(List<FbpDeclarationLineRequest> lines) {}
