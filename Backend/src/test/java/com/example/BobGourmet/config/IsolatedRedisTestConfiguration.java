package com.example.BobGourmet.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class IsolatedRedisTestConfiguration {
    // Spring owns the container for the entire cached application-context lifetime.
    // Missing Docker fails context startup; no skip or localhost fallback is used.
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> isolatedRedis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379);
    }
}
