package com.infinevo.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application configuration for integration tests in the core module.
 * Enables Spring Boot test context scanning across com.infinevo packages.
 */
@SpringBootApplication(scanBasePackages = "com.infinevo")
public class CoreTestApplication {}
