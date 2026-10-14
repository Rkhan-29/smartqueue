package com.smartqueue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Typed binding of the "smartqueue.*" configuration tree. */
@ConfigurationProperties(prefix = "smartqueue")
public record AppProperties(Jwt jwt, Cors cors, String publicBaseUrl, Security security, Seed seed) {

    public record Jwt(String secret, int accessTokenTtlMinutes, int refreshTokenTtlDays, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Security(int loginMaxAttempts, int loginLockoutMinutes,
                           int rateLimitPerMinute, int joinRateLimitPerMinute) {
    }

    public record Seed(boolean enabled, String demoPassword) {
    }
}
