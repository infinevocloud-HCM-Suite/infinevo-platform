package com.infinevo.shared.audit;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring context the audit integration tests run in.
 *
 * <p>Shared by all three so the context is built once. Component scanning starts at {@code
 * com.infinevo.shared.audit}, which is where the listener, the writer, the query service and the
 * controller live; the tenant binding filter and the DataSource proxy arrive through {@code
 * TenantBindingAutoConfiguration}, exactly as they do in the real applications.
 */
@SpringBootApplication
public class AuditTestApp {}
