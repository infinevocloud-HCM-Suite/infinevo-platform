package com.infinevo.hrms.performance;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReassignManagerRequest(@NotNull(message = "managerId is required") UUID managerId) {}
