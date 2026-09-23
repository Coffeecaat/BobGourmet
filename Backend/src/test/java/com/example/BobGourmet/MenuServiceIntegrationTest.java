package com.example.BobGourmet;

import com.example.BobGourmet.DTO.MenuDTO.SubmitMenuRequest;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import com.example.BobGourmet.Repository.RedisRoomRepository;
import com.example.BobGourmet.Service.MenuService;
import com.example.BobGourmet.config.IsolatedRedisTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(IsolatedRedisTestConfiguration.class)
class MenuServiceIntegrationTest {

    @Autowired
    private GenericContainer<?> isolatedRedis;

    @Autowired
    private MenuService menuService;

    @Autowired
    private MatchRoomRepository matchRoomRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    @AfterEach
    void clearDedicatedRedisDatabase() {
        // Check the actual connection before deleting data; never clear local/application Redis.
        assertInstanceOf(LettuceConnectionFactory.class, stringRedisTemplate.getConnectionFactory());
        LettuceConnectionFactory factory = (LettuceConnectionFactory) stringRedisTemplate.getConnectionFactory();
        assertEquals(isolatedRedis.getHost(), factory.getHostName());
        assertEquals(isolatedRedis.getMappedPort(6379).intValue(), factory.getPort());
        assertEquals(0, factory.getDatabase());
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("통합 테스트: 메뉴 제출부터 추첨까지 전체 흐름")
    void submitAndDraw_IntegrationTest() {
        // given: 테스트를 위한 방 및 유저 생성
        String testRoomId = "integ-test-room";
        String hostUser = "host1";
        String user2 = "user2";

        long createResult = matchRoomRepository.createRoomAtomically(
                testRoomId, "테스트방", hostUser, "127.0.0.1", 8080,
                4, false, null, "호스트닉네임"
        );
        assertEquals(RedisRoomRepository.JOIN_SUCCESS, createResult);
        long joinResult = matchRoomRepository.tryJoinRoomAtomically(testRoomId, user2, "127.0.0.1", 8081);
        assertEquals(RedisRoomRepository.JOIN_SUCCESS, joinResult);

        // when:
        // 1. 메뉴 제출 (두 명 모두)
        SubmitMenuRequest hostRequest = new SubmitMenuRequest();
        hostRequest.setMenus(List.of("피자"));
        menuService.submitMenus(hostUser, testRoomId, hostRequest);

        SubmitMenuRequest user2Request = new SubmitMenuRequest();
        user2Request.setMenus(List.of("치킨"));
        Map<String, Object> finalSubmitResult = menuService.submitMenus(user2, testRoomId, user2Request);

        // 2. 메뉴 비추천
        menuService.dislikeMenu(hostUser, testRoomId, "치킨");

        // 3. 추첨 시작
        Map<String, Object> drawResult = menuService.startDraw(hostUser, testRoomId);

        // then:
        // 마지막 제출 후 상태 변경 확인
        assertEquals("submitted", finalSubmitResult.get("nextState"));

        // Redis에 비추천 정보 저장 확인
        assertTrue(matchRoomRepository.isMenuExcluded(testRoomId, "치킨"));
        assertFalse(matchRoomRepository.isMenuExcluded(testRoomId, "피자"));

        // 추첨 결과 검증: 제외된 '치킨'이 아닌 '피자'가 선택되어야 함
        assertEquals("피자", drawResult.get("selectedMenu"));

        // 참고: startDraw가 추첨 결과를 Redis에 저장한다면, 아래 검증 추가
        // assertTrue(matchRoomRepository.getLastDrawResult(testRoomId).isPresent());
        // assertEquals("피자", matchRoomRepository.getLastDrawResult(testRoomId).get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exclusionLookupMatchesPersistedMenuDetails(boolean excluded) throws Exception {
        String roomId = "exclusion-round-trip";
        String menuKey = "피자";
        matchRoomRepository.saveSubmittedMenus(roomId, "host", List.of(menuKey));
        matchRoomRepository.markMenuAsExcluded(roomId, menuKey, excluded);

        String key = "room:" + roomId + ":submitted_menus";
        String stored = stringRedisTemplate.<String, String>opsForHash().get(key, menuKey);
        assertNotNull(stored);
        JsonNode json = new ObjectMapper().readTree(stored);
        assertTrue(json.has("excluded"));
        assertEquals(excluded, json.get("excluded").booleanValue());
        assertEquals(excluded, matchRoomRepository.getAllSubmittedMenusWithDetails(roomId).get(menuKey).isExcluded());
        assertEquals(excluded, matchRoomRepository.isMenuExcluded(roomId, menuKey));
        assertEquals(List.of(menuKey), matchRoomRepository.getSubmittedMenus(roomId, "host"));

        matchRoomRepository.markMenuAsExcluded(roomId, menuKey, !excluded);
        assertEquals(!excluded, matchRoomRepository.isMenuExcluded(roomId, menuKey));
    }

    @Test
    void missingMenuIsNotExcluded() {
        assertFalse(matchRoomRepository.isMenuExcluded("missing-room", "missing-menu"));
    }
}
