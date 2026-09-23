package com.infinevo.shared.identity;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring context the identity integration tests run in.
 *
 * <p>Component scanning starts at the two packages W-10 adds — {@code identity}, which holds the
 * entity, repository, service, filter and {@code /api/v1/me}, and {@code security}, which holds the
 * one filter chain. The tenant binding filter and the DataSource proxy arrive through
 * {@code TenantBindingAutoConfiguration}, exactly as they do in the real applications, so the filter
 * order under test is the shipped one.
 *
 * <p>Shared by both integration tests so the context is built once.
 */
@SpringBootApplication(scanBasePackages = {"com.infinevo.shared.identity", "com.infinevo.shared.security"})
public class IdentityTestApp {}
