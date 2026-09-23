package com.infinevo.core.employee;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Spring context the employee integration tests run in (W-13.1).
 *
 * <p>Component scanning starts at the one package the feature adds, so the entity, repository,
 * service and controller under test are the shipped ones. The tenant binding filter and the
 * DataSource proxy arrive through {@code TenantBindingAutoConfiguration}, exactly as they do in the
 * real applications, so the tenant reaches the connection the shipped way rather than a test one.
 *
 * <p>One class shared by both integration tests, for the reason {@code IdentityTestApp} is: the
 * context is then built once, and two nested {@code @SpringBootApplication} classes in the same
 * package cannot start finding each other.
 */
@SpringBootApplication(scanBasePackages = "com.infinevo.core.employee")
public class EmployeeTestApp {}
