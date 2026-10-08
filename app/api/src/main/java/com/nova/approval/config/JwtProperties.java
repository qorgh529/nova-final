package com.nova.approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nova.jwt")
public record JwtProperties(String secret, String issuer, long ttlSeconds) {
}
