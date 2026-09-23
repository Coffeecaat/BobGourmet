package com.example.BobGourmet.config;

import com.example.BobGourmet.Repository.RedisRoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RoomSubscriptionRepositoryTest {
    @Test
    @SuppressWarnings("unchecked")
    void executesReadOnlyScriptWithExactKeysAndAcceptsOnlyExplicitSuccess() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisRoomRepository repository = new RedisRoomRepository(redis);
        when(redis.execute(any(RedisScript.class), anyList(), eq("alice"), eq("room-abc123")))
                .thenAnswer(invocation -> {
                    RedisScript<Long> script = invocation.getArgument(0);
                    assertThat(script.getScriptAsString()).contains("SISMEMBER").doesNotContain("HDEL", "HSET", "SADD");
                    assertThat(invocation.<List<String>>getArgument(1)).containsExactly(
                            "room:room-abc123:details", "room:room-abc123:users", "user:locations");
                    return 1L;
                });
        assertThat(repository.hasRoomSubscriptionAccess("alice", "room-abc123")).isTrue();
        when(redis.execute(any(RedisScript.class), anyList(), eq("alice"), eq("room-abc123")))
                .thenReturn(0L, null, 2L);
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(repository.hasRoomSubscriptionAccess("alice", "room-abc123")).isFalse();
        }
    }
}
