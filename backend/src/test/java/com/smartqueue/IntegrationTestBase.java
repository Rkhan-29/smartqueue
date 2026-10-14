package com.smartqueue;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the real schema and real row locking. They run against PostgreSQL in Docker
 * (Testcontainers) because locking behaviour cannot be trusted on an in-memory substitute.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("smartqueue.jwt.secret", () -> "test-only-secret-test-only-secret-test-only-secret-0123");
        registry.add("smartqueue.seed.enabled", () -> "false");
        registry.add("smartqueue.security.rate-limit-per-minute", () -> "10000");
        registry.add("smartqueue.security.join-rate-limit-per-minute", () -> "10000");
    }
}
