package com.example.BobGourmet;

import com.example.BobGourmet.Repository.RedisRoomRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

// Part of the normal Gradle test/check lifecycle. Missing Docker must fail, not skip.
@Testcontainers(disabledWithoutDocker = false)
@Execution(ExecutionMode.SAME_THREAD)
class RoomSubscriptionAccessIntegrationTest {
    private static final String ROOM_ID = "room-abc123";
    private static final String USERNAME = "alice";
    private static final String DETAILS = "room:" + ROOM_ID + ":details";
    private static final String USERS = "room:" + ROOM_ID + ":users";
    private static final String LOCATIONS = "user:locations";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisRoomRepository repository;

    @BeforeAll
    static void connectToDedicatedContainer() {
        // Never use application Redis properties or a fixed localhost port.
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        repository = new RedisRoomRepository(redis);
    }

    @AfterAll
    static void closeClient() {
        if (connectionFactory != null) connectionFactory.destroy();
        // The Testcontainers extension owns and stops REDIS.
    }

    @BeforeEach
    void seedIndependentState() {
        // This connection points exclusively at this class's disposable container.
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        redis.opsForHash().putAll(DETAILS, Map.of("name", "private room", "isPrivate", "true"));
        redis.opsForSet().add(USERS, USERNAME);
        redis.opsForHash().put(LOCATIONS, USERNAME, ROOM_ID);
        redis.opsForValue().set("unrelated:marker", "must not change");
    }

    @ParameterizedTest(name = "{0}: decision and all Redis data remain consistent")
    @EnumSource(Scenario.class)
    void checksMembershipWithoutMutatingRedis(Scenario scenario) {
        switch (scenario) {
            case MEMBER -> { }
            case OTHER_ROOM -> redis.opsForHash().put(LOCATIONS, USERNAME, "room-def456");
            case MISSING_LOCATION -> redis.delete(LOCATIONS);
            case MISSING_USER -> redis.opsForSet().remove(USERS, USERNAME);
            case MISSING_DETAILS -> redis.delete(DETAILS);
            case DETAILS_WRONG_TYPE -> redis.opsForValue().set(DETAILS, "invalid");
            case USERS_WRONG_TYPE -> redis.opsForValue().set(USERS, "invalid");
            case LOCATIONS_WRONG_TYPE -> redis.opsForValue().set(LOCATIONS, "invalid");
            case ALL_ABSENT -> redis.delete(List.of(DETAILS, USERS, LOCATIONS));
            case USER_ABSENT_FROM_EXISTING_SET -> {
                redis.opsForSet().add(USERS, "bob");
                redis.opsForSet().remove(USERS, USERNAME);
            }
            case LOCATION_FIELD_MISSING -> {
                redis.opsForHash().put(LOCATIONS, "bob", ROOM_ID);
                redis.opsForHash().delete(LOCATIONS, USERNAME);
            }
        }
        Map<String, String> before = snapshot();

        // Execute the production repository and its classpath Lua against real Redis.
        boolean allowed = repository.hasRoomSubscriptionAccess(USERNAME, ROOM_ID);

        assertThat(allowed).as("access for %s", scenario).isEqualTo(scenario == Scenario.MEMBER);
        assertThat(snapshot()).as("read-only access check for %s", scenario).isEqualTo(before);
    }

    private Map<String, String> snapshot() {
        Map<String, String> state = new TreeMap<>();
        Set<String> keys = redis.keys("*");
        assertThat(keys).isNotNull();
        for (String key : keys) {
            byte[] dump = redis.dump(key);
            assertThat(dump).as("serialized value for %s", key).isNotNull();
            state.put(key, Base64.getEncoder().encodeToString(dump));
        }
        return state;
    }

    enum Scenario {
        MEMBER,
        OTHER_ROOM,
        MISSING_LOCATION,
        MISSING_USER,
        MISSING_DETAILS,
        DETAILS_WRONG_TYPE,
        USERS_WRONG_TYPE,
        LOCATIONS_WRONG_TYPE,
        ALL_ABSENT,
        USER_ABSENT_FROM_EXISTING_SET,
        LOCATION_FIELD_MISSING
    }
}
