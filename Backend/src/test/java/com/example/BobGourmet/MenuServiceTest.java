package com.example.BobGourmet;

import com.example.BobGourmet.DTO.MenuDTO.MenuStatus;
import com.example.BobGourmet.DTO.MenuDTO.MenuVoteDetails;
import com.example.BobGourmet.DTO.MenuDTO.SubmitMenuRequest;
import com.example.BobGourmet.DTO.WebSocketMessage;
import com.example.BobGourmet.Exception.RoomException;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import com.example.BobGourmet.Service.MenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MenuServiceTest {

    @Mock
    private MatchRoomRepository matchRoomRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MenuService menuService;

    private String testRoomId;
    private String hostUsername;
    private String normalUsername;
    private Map<String, String> roomDetails;

    @BeforeEach
    void setUp() {
        testRoomId = "room-123";
        hostUsername = "hostUser";
        normalUsername = "normalUser";

        roomDetails = new HashMap<>();
        roomDetails.put("hostUsername", hostUsername);
        roomDetails.put("state", "inputting");
    }

    @Test
    @DisplayName("메뉴 제출 성공 - 마지막 제출자가 아닐 경우")
    void submitMenus_Success_NotLastSubmitter() {
        // given
        SubmitMenuRequest request = new SubmitMenuRequest();
        request.setMenus(Arrays.asList("피자", "치킨"));

        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);
        when(matchRoomRepository.haveAllUsersSubmitted(testRoomId)).thenReturn(false); // 마지막 제출자가 아님
        // buildMenuStatus가 호출될 때 빈 상태를 반환하도록 설정
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(new HashMap<>());
        when(matchRoomRepository.getRoomUsers(testRoomId)).thenReturn(new HashSet<>(Arrays.asList(hostUsername, normalUsername)));

        // when
        Map<String, Object> result = menuService.submitMenus(hostUsername, testRoomId, request);

        // then
        // Repository의 저장/업데이트 메서드가 호출되었는지 확인
        verify(matchRoomRepository, times(1)).saveSubmittedMenus(testRoomId, hostUsername, request.getMenus());
        verify(matchRoomRepository, times(1)).updateUserSubmitStatus(testRoomId, hostUsername, true);

        // 상태 변경은 일어나지 않아야 함
        assertNull(result.get("nextState"));
        assertNotNull(result.get("menuStatus"));

        // WebSocket 메시지가 전송되었는지 확인
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room/" + testRoomId + "/menuStatus"), any(MenuStatus.class));
    }

    @Test
    @DisplayName("메뉴 제출 성공 - 마지막 제출자일 경우")
    void submitMenus_Success_LastSubmitter() {
        // given
        SubmitMenuRequest request = new SubmitMenuRequest();
        request.setMenus(List.of("파스타"));

        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);
        when(matchRoomRepository.haveAllUsersSubmitted(testRoomId)).thenReturn(true); // 마지막 제출자임
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(new HashMap<>());
        when(matchRoomRepository.getRoomUsers(testRoomId)).thenReturn(new HashSet<>(Collections.singletonList(hostUsername)));

        // when
        Map<String, Object> result = menuService.submitMenus(hostUsername, testRoomId, request);

        // then
        assertEquals("submitted", result.get("nextState"));
        assertNotNull(result.get("menuStatus"));
        verify(matchRoomRepository, times(1)).saveSubmittedMenus(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("메뉴 제출 실패 - 메뉴 개수 초과")
    void submitMenus_Fail_TooManyMenus() {
        // given
        SubmitMenuRequest request = new SubmitMenuRequest();
        request.setMenus(Arrays.asList("1", "2", "3", "4", "5")); // 5개 제출

        // when & then
        RoomException exception = assertThrows(RoomException.class, () -> {
            menuService.submitMenus(hostUsername, testRoomId, request);
        });

        assertTrue(exception.getMessage().contains("최대 4개까지 제출할 수 있습니다."));
    }

    @Test
    @DisplayName("메뉴 제출 실패 - 잘못된 방 상태")
    void submitMenus_Fail_InvalidRoomState() {
        // given
        SubmitMenuRequest request = new SubmitMenuRequest();
        request.setMenus(List.of("김치찌개"));
        roomDetails.put("state", "ended"); // 이미 종료된 방

        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);

        // when & then
        RoomException exception = assertThrows(RoomException.class, () -> {
            menuService.submitMenus(hostUsername, testRoomId, request);
        });

        assertTrue(exception.getMessage().contains("현재 메뉴를 제출할 수 있는 상태가 아닙니다."));
    }

    @Test
    @DisplayName("추첨 시작 성공")
    void startDraw_Success() {
        // given
        // 1. 방 정보 설정
        roomDetails.put("state", "submitted");
        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);

        // 2. 모든 유저가 제출 완료했다고 설정
        when(matchRoomRepository.haveAllUsersSubmitted(testRoomId)).thenReturn(true);

        // 3. 제출된 메뉴 목록 설정
        MenuVoteDetails pizzaDetails = new MenuVoteDetails(); // 피자 (정상)
        MenuVoteDetails chickenDetails = new MenuVoteDetails(); // 치킨 (제외됨)
        chickenDetails.setExcluded(true);

        Map<String, MenuVoteDetails> submittedMenus = new HashMap<>();
        submittedMenus.put("피자", pizzaDetails);
        submittedMenus.put("치킨", chickenDetails);
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(submittedMenus);

        // when
        Map<String, Object> result = menuService.startDraw(hostUsername, testRoomId);

        // then
        // 선택된 메뉴는 제외되지 않은 '피자'여야 함
        assertEquals("피자", result.get("selectedMenu"));
        assertNotNull(result.get("timestamp"));

        ArgumentCaptor<WebSocketMessage> messageCaptor = ArgumentCaptor.forClass(WebSocketMessage.class);

        // WebSocket으로 추첨 결과가 전송되었는지 확인
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room/" + testRoomId + "/events"), messageCaptor.capture());
    }

    @Test
    @DisplayName("추첨 시작 실패 - 호스트가 아님")
    void startDraw_Fail_NotHost() {
        // given
        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);

        // when & then
        RoomException exception = assertThrows(RoomException.class, () -> {
            // 일반 유저가 추첨 시도
            menuService.startDraw(normalUsername, testRoomId);
        });

        assertEquals("호스트만 추첨을 시작할 수 있습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("추첨 시작 실패 - 추첨할 메뉴 없음")
    void startDraw_Fail_NoDrawableMenus() {
        // given
        roomDetails.put("state", "submitted");
        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);
        when(matchRoomRepository.haveAllUsersSubmitted(testRoomId)).thenReturn(true);

        // 모든 메뉴가 제외된 상태로 설정
        MenuVoteDetails pizzaDetails = new MenuVoteDetails();
        pizzaDetails.setExcluded(true);
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(Map.of("피자", pizzaDetails));

        // when & then
        RoomException exception = assertThrows(RoomException.class, () -> {
            menuService.startDraw(hostUsername, testRoomId);
        });

        assertEquals("추첨할 메뉴가 없습니다. 메뉴를 다시 제출해주세요.", exception.getMessage());
    }

    @Test
    @DisplayName("메뉴 추천 성공")
    void recommendMenu_Success() {
        // given
        String menuKey = "피자";
        when(matchRoomRepository.getUserMenuQuota(testRoomId, hostUsername)).thenReturn(1); // 쿼터가 1 남음
        when(matchRoomRepository.decrementUserMenuQuota(testRoomId, hostUsername)).thenReturn(0L);
        // buildMenuStatus mocking
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(new HashMap<>());
        when(matchRoomRepository.getRoomUsers(testRoomId)).thenReturn(new HashSet<>());

        // when
        MenuStatus result = menuService.recommendMenu(hostUsername, testRoomId, menuKey);

        // then
        assertNotNull(result);
        verify(matchRoomRepository, times(1)).updateMenuVoteInfo(
                eq(testRoomId), eq(menuKey),eq("recommenders"), eq(hostUsername), eq(true));
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(MenuStatus.class));
    }

    @Test
    @DisplayName("메뉴 추천 실패 - 쿼터 없음")
    void recommendMenu_Fail_NoQuota() {
        // given
        String menuKey = "피자";
        when(matchRoomRepository.getUserMenuQuota(testRoomId, hostUsername)).thenReturn(0); // 쿼터 없음

        // when & then
        RoomException exception = assertThrows(RoomException.class, () -> {
            menuService.recommendMenu(hostUsername, testRoomId, menuKey);
        });

        assertEquals("더 이상 메뉴를 추천하거나 제출할 수 없습니다.", exception.getMessage());
        verify(matchRoomRepository, never()).updateMenuVoteInfo(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    //---------------------------------------

    @Test
    @DisplayName("메뉴 비추천 성공")
    void dislikeMenu_Success() {
        // given
        String menuKey = "피자";
        // buildMenuStatus mocking
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(new HashMap<>());
        when(matchRoomRepository.getRoomUsers(testRoomId)).thenReturn(new HashSet<>());

        // when
        MenuStatus result = menuService.dislikeMenu(hostUsername, testRoomId, menuKey);

        // then
        assertNotNull(result);
        verify(matchRoomRepository, times(1)).updateMenuVoteInfo(
                eq(testRoomId), eq(menuKey), eq("dislikedBy"), eq(hostUsername) , eq(true));

        verify(matchRoomRepository, times(1)).updateMenuDetailsField(
                eq(testRoomId), eq(menuKey), eq("isExcluded"), eq(true));
        // WebSocket 메시지가 전송되었는지 확인
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(MenuStatus.class));
    }

    @Test
    @DisplayName("재추첨 요청 성공 - 호스트")
    void resetDraw_Success_ByHost() {
        // given
        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);

        // when
        // 이 메서드는 반환값이 없으므로, 예외가 발생하지 않는 것만으로도 성공을 의미합니다.
        assertDoesNotThrow(() -> {
            menuService.resetDraw(testRoomId, hostUsername);
        });

        // then
        // 재추첨 로직이 Repository의 특정 메서드를 호출한다면, 그 호출을 verify 해야 합니다.
        // 예를 들어, 아래와 같은 메서드가 있다고 가정. (현재는 없으므로 주석 처리)
        // verify(matchRoomRepository, times(1)).clearLastDrawResult(testRoomId);
        // verify(matchRoomRepository, times(1)).updateRoomState(testRoomId, "submitted");
    }

    @Test
    @DisplayName("재추첨 요청 실패 - 호스트가 아님")
    void resetDraw_Fail_NotHost() {
        // given
        when(matchRoomRepository.getRoomDetailsMap(testRoomId)).thenReturn(roomDetails);

        // when & then
        RoomException exception = assertThrows(RoomException.class, () -> {
            menuService.resetDraw(testRoomId, normalUsername); // 일반 유저가 요청
        });

        assertEquals("호스트만 재추첨을 요청할 수 있습니다.", exception.getMessage());
    }

    @Test
    @DisplayName("복잡한 메뉴 상태에서 buildMenuStatus가 정확히 동작하는지 검증")
    void buildMenuStatus_ComplexCase() {
        // given
        // 1. 유저 목록 설정
        Set<String> users = Set.of(hostUsername, normalUsername);
        when(matchRoomRepository.getRoomUsers(testRoomId)).thenReturn(users);

        // 2. 각 유저가 제출한 메뉴 목록 mocking
        when(matchRoomRepository.getSubmittedMenus(testRoomId, hostUsername)).thenReturn(List.of("피자", "치킨"));
        when(matchRoomRepository.getSubmittedMenus(testRoomId, normalUsername)).thenReturn(List.of("치킨", "파스타"));

        // 3. 전체 메뉴 상세 정보 mocking
        MenuVoteDetails pizzaDetails = new MenuVoteDetails(new HashSet<>(), Set.of(hostUsername), new HashSet<>(), false);
        MenuVoteDetails chickenDetails = new MenuVoteDetails(Set.of(normalUsername), Set.of(hostUsername, normalUsername), new HashSet<>(), false);
        MenuVoteDetails pastaDetails = new MenuVoteDetails(new HashSet<>(), Set.of(normalUsername), Set.of(hostUsername), true); // 비추천됨

        Map<String, MenuVoteDetails> submittedMenus = Map.of(
                "피자", pizzaDetails,
                "치킨", chickenDetails,
                "파스타", pastaDetails
        );
        when(matchRoomRepository.getAllSubmittedMenusWithDetails(testRoomId)).thenReturn(submittedMenus);

        // 4. 유저 제출 상태 mocking
        Map<String, Boolean> submitStatus = Map.of(hostUsername, true, normalUsername, true);
        when(matchRoomRepository.getRoomSubmitStatus(testRoomId)).thenReturn(submitStatus);

        // when
        MenuStatus menuStatus = menuService.buildMenuStatus(testRoomId);

        // then
        // 1. 유저별 제출 메뉴 검증
        assertEquals(2, menuStatus.getSubmittedMenusByUsers().size());
        assertEquals(List.of("피자", "치킨"), menuStatus.getSubmittedMenusByUsers().get(hostUsername));

        // 2. 메뉴별 투표 정보 검증
        assertEquals(3, menuStatus.getMenuVotes().size());
        assertEquals(1, menuStatus.getMenuVotes().get("치킨").getRecommenders().size()); // normalUser가 추천
        assertTrue(menuStatus.getMenuVotes().get("치킨").getRecommenders().contains(normalUsername));
        assertEquals(1, menuStatus.getMenuVotes().get("파스타").getDislikedBy().size()); // hostUser가 비추천

        // 3. 제외된 메뉴 검증
        assertEquals(1, menuStatus.getDislikedAndExcludedMenuKeys().size());
        assertTrue(menuStatus.getDislikedAndExcludedMenuKeys().contains("파스타"));

        // 4. 제출 상태 검증
        assertTrue(menuStatus.getUserSubmitStatus().get(normalUsername));
    }
}
