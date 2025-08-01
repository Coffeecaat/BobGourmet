package com.example.BobGourmet;

import com.example.BobGourmet.DTO.MenuDTO.SubmitMenuRequest;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import com.example.BobGourmet.Service.MenuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers // 1. Testcontainers 활성화
@SpringBootTest
class MenuServiceIntegrationTest {

    // 2. 재사용 가능한 Redis 컨테이너 정의
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // 3. 동적으로 Redis 연결 정보를 스프링에 주입
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private MenuService menuService;

    @Autowired
    private MatchRoomRepository matchRoomRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // BeforeEach/AfterEach 로직은 크게 변경할 필요 없음
    // 다만, 매 테스트마다 데이터를 확실히 초기화하기 위해 flushAll을 사용
    @AfterEach
    void tearDown() {
        // 각 테스트 후 Redis 데이터 정리
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @DisplayName("통합 테스트: 메뉴 제출부터 추첨까지 전체 흐름")
    void submitAndDraw_IntegrationTest() {
        // given: 테스트를 위한 방 및 유저 생성
        String testRoomId = "integ-test-room";
        String hostUser = "host1";
        String user2 = "user2";

        matchRoomRepository.createRoomAtomically(
                testRoomId, "테스트방", hostUser, "127.0.0.1", 8080,
                4, false, null, "호스트닉네임"
        );
        matchRoomRepository.tryJoinRoomAtomically(testRoomId, user2, "127.0.0.1", 8081);

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
}