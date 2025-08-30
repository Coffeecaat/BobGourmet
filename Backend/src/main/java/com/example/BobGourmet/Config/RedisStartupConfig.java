package com.example.BobGourmet.Config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
public class RedisStartupConfig {

    @Value("${spring.redis.clear-on-startup:false}")
    private boolean clearOnStartup;

    @Bean
    public ApplicationRunner clearRedisOnStartup(StringRedisTemplate redisTemplate) {
        return args -> {
            if (clearOnStartup) {
                try {
                    log.info("Clearing Redis cache on startup...");
                    redisTemplate.getConnectionFactory().getConnection().flushAll();
                    log.info("Redis cache cleared successfully");
                } catch (Exception e) {
                    log.warn("Failed to clear Redis cache on startup: {}", e.getMessage());
                }
            }
        };
    }
}