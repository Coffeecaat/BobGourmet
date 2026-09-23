package com.example.BobGourmet;

import com.example.BobGourmet.config.IsolatedRedisTestConfiguration;
import com.example.BobGourmet.utils.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IsolatedRedisTestConfiguration.class)
class BobGourmetApplicationTests {

    @Autowired
    private Environment environment;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private GenericContainer<?> isolatedRedis;

    @Test
    void contextLoads() throws Exception {
        assertThat(environment.getActiveProfiles()).containsExactly("test");
        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:h2:mem:bobgourmet-test-");
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:bobgourmet-test-");
        }
        assertThat(jwtProvider.validateToken(jwtProvider.generateToken("test-user"))).isTrue();
        assertThat(environment.getProperty("spring.redis.clear-on-startup", Boolean.class)).isFalse();

        assertThat(redisTemplate.getConnectionFactory()).isInstanceOf(LettuceConnectionFactory.class);
        LettuceConnectionFactory factory = (LettuceConnectionFactory) redisTemplate.getConnectionFactory();
        assertThat(factory.getHostName()).isEqualTo(isolatedRedis.getHost());
        assertThat(factory.getPort()).isEqualTo(isolatedRedis.getMappedPort(6379));
        String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
        assertThat(pong).isEqualTo("PONG");
    }

}
