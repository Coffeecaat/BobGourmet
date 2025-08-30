package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.MenuDTO.MenuStatus;
import com.example.BobGourmet.DTO.MenuDTO.MenuVoteDetails;
import com.example.BobGourmet.DTO.MenuDTO.SubmitMenuRequest;
import com.example.BobGourmet.DTO.WebSocketMessage;
import com.example.BobGourmet.Exception.RoomException;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MenuService {

    private final MatchRoomRepository matchRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;


    private static final int MAX_MENU_SUBMISSIONS_PER_USER = 4;

    public MenuService(MatchRoomRepository matchRoomRepository, SimpMessagingTemplate messagingTemplate) {
        this.matchRoomRepository = matchRoomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public Map<String,Object> submitMenus(String username, String roomId, SubmitMenuRequest request) {

        Optional<String> userRoom = matchRoomRepository.findRoomIdByUser(username);
        if(userRoom.isEmpty() || !userRoom.get().equals(roomId)) {
            throw new SecurityException("User not authorized for this room");
        }
        List<String> menusToSubmit = request.getMenus();

        if (menusToSubmit == null || menusToSubmit.isEmpty() || menusToSubmit.size() > MAX_MENU_SUBMISSIONS_PER_USER) {
            throw new RoomException("메뉴는 1개 이상, 최대 " + MAX_MENU_SUBMISSIONS_PER_USER + "개까지 제출할 수 있습니다.");
        }

        List<String> distinctMenus = menusToSubmit.stream().distinct().collect(Collectors.toList());
        if(distinctMenus.size() > MAX_MENU_SUBMISSIONS_PER_USER) {
            throw new RoomException("제출 가능한 메뉴 수를 초과했습니다(중복 제외 " + MAX_MENU_SUBMISSIONS_PER_USER + "개)");
        }

        Map<String, String> roomDetailsMap = matchRoomRepository.getRoomDetailsMap(roomId);
        if(roomDetailsMap.isEmpty()) {
            throw new RoomException("방 '" + roomId + "'을(를) 찾을 수 없습니다.");
        }
        String currentRoomState = roomDetailsMap.getOrDefault("state", "unknown");

        if(!"waiting".equals(currentRoomState) && !"inputting".equals(currentRoomState)) {
            throw new RoomException("현재 메뉴를 제출할 수 있는 상태가 아닙니다. 현재 상태:" + currentRoomState);
        }

        matchRoomRepository.saveSubmittedMenus(roomId,username,distinctMenus);
        matchRoomRepository.updateUserSubmitStatus(roomId,username, true);
        log.info("User '{}' submitted menus for room '{}': {}", username, roomId, distinctMenus);

        String nextState =null;
        if(matchRoomRepository.haveAllUsersSubmitted(roomId)) {
            nextState = "submitted";
            log.info("All users in room '{}' have submitted menus. State changed to submitted.", roomId);
        }

        MenuStatus currentMenuStatus = buildMenuStatus(roomId);
        broadcastMenuStatusUpdate(roomId, currentMenuStatus);

        Map<String,Object> result = new HashMap<>();
        result.put("menuStatus", currentMenuStatus);
        result.put("nextState", nextState);
        return result;
        }

        public MenuStatus recommendMenu(String username, String roomId, String menuKey){

        Optional<String> userRoom = matchRoomRepository.findRoomIdByUser(username);
        if(userRoom.isEmpty() || !userRoom.get().equals(roomId)) {
            throw new SecurityException("User not authorized for this room");
        }


        int currentQuota = matchRoomRepository.getUserMenuQuota(roomId, username);
        if(currentQuota <=0) {
            throw new RoomException("더 이상 메뉴를 추천하거나 제출할 수 없습니다.");
        }
        long newQuota = matchRoomRepository.decrementUserMenuQuota(roomId, username);

        matchRoomRepository.updateMenuVoteInfo(roomId,menuKey,"recommenders",username,true);
        log.info("User '{}' recommended menu for room '{}': {}. Quota left: {}", username, roomId, menuKey,newQuota);

        MenuStatus currentMenuStatus = buildMenuStatus(roomId);
        broadcastMenuStatusUpdate(roomId, currentMenuStatus);

        return currentMenuStatus;

        }

        public MenuStatus dislikeMenu(String username, String roomId, String menuKey){

        Optional<String> userRoom = matchRoomRepository.findRoomIdByUser(username);
        if(userRoom.isEmpty() || !userRoom.get().equals(roomId)) {
            throw new SecurityException("User not authorized for this room");
        }


        matchRoomRepository.updateMenuVoteInfo(roomId,menuKey,"dislikedBy", username, true);
        matchRoomRepository.updateMenuDetailsField(roomId,menuKey,"isExcluded", true);
        log.info("User '{}' disliked menu '{}' in room '{}'. Menu is now excluded from draw.", username,menuKey, roomId);

        MenuStatus currentMenuStatus = buildMenuStatus(roomId);
        broadcastMenuStatusUpdate(roomId, currentMenuStatus);

        return currentMenuStatus;
        }


        public Map<String,Object> startDraw(String username, String roomId){

        Optional<String> userRoom = matchRoomRepository.findRoomIdByUser(username);
        if(userRoom.isEmpty() || !userRoom.get().equals(roomId)) {
            throw new SecurityException("User not authorized for this room");
        }


        Map<String,String> roomInfo = matchRoomRepository.getRoomDetailsMap(roomId);
        if(roomInfo.isEmpty()) throw new RoomException("방을 찾을 수 없습니다.");
        if(!username.equals(roomInfo.get("hostUsername"))) throw new RoomException("호스트만 추첨을 시작할 수 있습니다.");

        if(!matchRoomRepository.haveAllUsersSubmitted(roomId)) {
            throw new RoomException("모든 참여자가 메뉴를 제출해야 추첨을 시작할 수 있습니다.");
        }

        String currentState = roomInfo.getOrDefault("state", "unknown");
        if(!"submitted".equals(currentState) && !"inputting".equals(currentState)) {
            throw new RoomException("현재 추첨을 시작할 수 있는 상태가 아닙니다. 현재 상태: " + currentState);
        }

        Map<String, MenuVoteDetails> allMenusWithDetails = matchRoomRepository.getAllSubmittedMenusWithDetails(roomId);
        List<String> drawableMenus = new ArrayList<>();
        allMenusWithDetails.forEach((menuName, menuDetails) -> {
            if(!menuDetails.isExcluded()){
                drawableMenus.add(menuName);
            }
            });

        if(drawableMenus.isEmpty()) {
            throw new RoomException("추첨할 메뉴가 없습니다. 메뉴를 다시 제출해주세요.");
        }

        Random random = new Random();
        String selectedMenu = drawableMenus.get(random.nextInt(drawableMenus.size()));
        long drawTimestamp = Instant.now().toEpochMilli();
        log.info("Draw completed in room '{}'. Selected menu: {}", roomId, selectedMenu);

        broadcastDrawResult(roomId, selectedMenu);

        Map<String,Object> result = new HashMap<>();
        result.put("selectedMenu", selectedMenu);
        result.put("timestamp", drawTimestamp);
        return result;
        }

        @Transactional
        public void resetDraw(String roomId,String username){

        Optional<String> userRoom = matchRoomRepository.findRoomIdByUser(username);
        if(userRoom.isEmpty() || !userRoom.get().equals(roomId)) {
            throw new SecurityException("User not authorized for this room");
        }


        Map<String,String> roomInfo = matchRoomRepository.getRoomDetailsMap(roomId);
        if(roomInfo.isEmpty()) {
            throw new RoomException("방을 찾을 수 없습니다.");
        }

        if(username != null && !username.equals(roomInfo.get("hostUsername"))) {
            throw new RoomException("호스트만 재추첨을 요청할 수 있습니다.");
        }

        matchRoomRepository.clearSubmittedMenus(roomId);
        matchRoomRepository.clearLastDrawResult(roomId);

        Set<String> usersInRoom = matchRoomRepository.getRoomUsers(roomId);
        if(usersInRoom !=null){
            for(String user: usersInRoom){
                matchRoomRepository.updateUserSubmitStatus(roomId,user,false);
                matchRoomRepository.initUserMenuQuota(roomId,user, MAX_MENU_SUBMISSIONS_PER_USER);
            }
        }

        log.info("Room '{}' has been reset by host '{}'.", roomId, username);

        MenuStatus resetMenuStatus = buildMenuStatus(roomId);
        broadcastMenuStatusUpdate(roomId, resetMenuStatus);
        }


        private void broadcastMenuStatusUpdate(String roomId, MenuStatus menuStatus) {
        WebSocketMessage<MenuStatus> message = new WebSocketMessage<>("MENU_STATUS_UPDATE", menuStatus);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/menuStatus", message);
        log.debug("Broadcast menu status update for room {}", roomId);
        }

        private void broadcastDrawResult(String roomId, String selectedMenu) {

            WebSocketMessage<Map<String,String>> message=
                    new WebSocketMessage<>("draw_result", Collections.singletonMap("selectedMenu", selectedMenu));
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", message);
            log.info("Broadcast draw_result for room {}:{}", roomId, selectedMenu);
        }

        public MenuStatus buildMenuStatus(String roomId) {
        Map<String,MenuVoteDetails> submittedMenusRaw = matchRoomRepository.getAllSubmittedMenusWithDetails(roomId);
        Map<String, List<String>> submittedMenusByUsers = new HashMap<>();
        Map<String, MenuVoteDetails> menuVotes = new HashMap<>();
        Set<String> dislikedAndExcludedMenuKeys = new HashSet<>();

        Set<String> allUsersInRoom = matchRoomRepository.getRoomUsers(roomId);
        if(allUsersInRoom != null) {
            for(String user : allUsersInRoom){
                submittedMenusByUsers.put(user, matchRoomRepository.getSubmittedMenus(roomId, user));
            }
        }

        submittedMenusRaw.forEach((menuName,menuDetails) -> {
            Set<String> recommenders = menuDetails.getRecommenders();
            Set<String> dislikers = menuDetails.getDislikedBy();

            MenuVoteDetails voteDetailsForStatus = new MenuVoteDetails();
            voteDetailsForStatus.setRecommenders(recommenders);
            voteDetailsForStatus.setDislikedBy(dislikers);

            menuVotes.put(menuName, voteDetailsForStatus);

        if(menuDetails.isExcluded()) {
                    dislikedAndExcludedMenuKeys.add(menuName);
                }
        });
        Map<String,Boolean> rawSubmitStatus = matchRoomRepository.getRoomSubmitStatus(roomId);
        Map<String,Boolean> userSubmitStatus = new HashMap<>();
        
        // Ensure all current room users have a submit status entry (defaulting to false for new users)
        if(allUsersInRoom != null) {
            for(String user : allUsersInRoom){
                userSubmitStatus.put(user, rawSubmitStatus.getOrDefault(user, false));
            }
        }
        
        return new MenuStatus(submittedMenusByUsers, menuVotes,dislikedAndExcludedMenuKeys,userSubmitStatus);

        }

    }

