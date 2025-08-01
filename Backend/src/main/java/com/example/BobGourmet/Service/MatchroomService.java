package com.example.BobGourmet.Service;

import com.example.BobGourmet.DTO.RoomDTO.CreateRoomRequest;
import com.example.BobGourmet.DTO.RoomDTO.JoinRoomRequest;
import com.example.BobGourmet.DTO.Participant;
import com.example.BobGourmet.DTO.RoomDTO.RoomDetails;
import com.example.BobGourmet.DTO.WebSocketMessage;
import com.example.BobGourmet.Entity.User;
import com.example.BobGourmet.Exception.RoomException;
import com.example.BobGourmet.Repository.MatchRoomRepository;
import com.example.BobGourmet.Repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.BobGourmet.Repository.RedisRoomRepository.JOIN_ERROR_WATCH_CONFLICT;
import static com.example.BobGourmet.Repository.RedisRoomRepository.JOIN_SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchroomService {

    private final MatchRoomRepository matchRoomRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomStateService roomStateService;

    private static final int MAX_JOIN_ATTEMPTS = 3;
    private static final int DRAW_RESULT_VIEW_DURATION_MS = 10000;


    // updating room state on Redis
    private void updateRoomState(String roomId, String state){
        matchRoomRepository.updateRoomState(roomId, state);
    }

    // saving last draw result on Redis
    private void saveLastDrawResult(String roomId, String menu, long timestamp){
        matchRoomRepository.saveLastDrawResult(roomId, menu, timestamp);
    }

    public Optional<String> findRoomIdByUser(String username){
        return matchRoomRepository.findRoomIdByUser(username);
    }

    public boolean isUserInMatchroom(String username, String roomId){
        Optional<String> userRoom = matchRoomRepository.findRoomIdByUser(username);
        return userRoom.isPresent() && userRoom.get().equals(roomId);
    }

    @Scheduled(fixedDelay = DRAW_RESULT_VIEW_DURATION_MS)
    public void autoResetExpiredDrawResults() {
        Set<String> activeRoomIds = matchRoomRepository.getActiveRoomIds();
        if(activeRoomIds.isEmpty() || activeRoomIds == null) return;

        log.trace("Scheduler: Checking for expired draw results in {} rooms.", activeRoomIds.size());
        for(String roomId : activeRoomIds){
            try{
                String state = matchRoomRepository.getRoomState(roomId);
                if("result_viewing".equals(state)) {
                    Optional<Long> lastDrawTimestampOpt = matchRoomRepository.getLastDrawTimestamp(roomId);
                    if(lastDrawTimestampOpt.isPresent()) {
                        long timeSinceDraw = Instant.now().toEpochMilli() - lastDrawTimestampOpt.get();
                        if(timeSinceDraw >= DRAW_RESULT_VIEW_DURATION_MS) {
                            log.info("Scheduler: Auto-resetting draw for room '{}' due to timeout.",roomId);
                            roomStateService.startMenuInput(roomId);
                        }
                    }else{
                        log.warn("Scheduler: Room '{}' is in result_viewing but has no lastDrawTimestamp. Resetting immediately.", roomId);
                        roomStateService.startMenuInput(roomId);
                    }
                }
            }catch(RoomException e){
                log.warn("Scheduler: RoomException while auto-resetting room {}: {}", roomId, e.getMessage());
            }catch(Exception e){
                log.error("Scheduler: Unexpected error processing room {}: {}", roomId, e.getMessage(),e);
            }
        }
    }

    public RoomDetails createRoom(String hostUsername, CreateRoomRequest request, String hostIp, int hostPort) {
        User host = userRepository.findByUsername(hostUsername)
                .orElseThrow(() -> new RoomException("호스트 정보를 찾을 수 없습니다: " + hostUsername));

        String roomId = matchRoomRepository.generateNewRoomId();
        int maxUsers = Math.max(2, Math.min(request.getMaxUsers(), 10));

        String hashedPassword = null;
        if (request.isPrivate() && request.getPassword() != null && !request.getPassword().isEmpty()) {
            hashedPassword = passwordEncoder.encode(request.getPassword());
        }

        //atomic creation method called
        long result = matchRoomRepository.createRoomAtomically(roomId, request.getRoomName(), hostUsername, hostIp, hostPort,
                maxUsers, request.isPrivate(), hashedPassword, host.getNickname());

        if (result == -5L) {
            throw new RoomException("이미 다른 방에 참가 중입니다.");
        }else if(result == JOIN_ERROR_WATCH_CONFLICT){
            throw new RoomException("방 생성 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.");
        }else if (result != JOIN_SUCCESS){
            throw new RoomException("알 수 없는 오류로 방 생성에 실패했습니다.");
        }

        log.info("Room created: id ={}, name={}, host={}, endpoint={}:{}, isPrivate={}",
                roomId, request.getRoomName(), hostUsername, hostIp, hostPort, request.isPrivate());

        //broadcasting after successfully creating room
        RoomDetails roomDetails = buildRoomDetails(roomId);
        broadcastParticipantUpdate(roomId, roomDetails.getParticipants());
        broadcastRoomStateUpdate(roomId, "inputting", roomDetails);

        return roomDetails;
    }

    public RoomDetails joinRoom(String username, String roomId, JoinRoomRequest request,String joinerIp, int joinerPort) {
        matchRoomRepository.findRoomIdByUser(username).ifPresent(existingRoomId -> {
            throw new RoomException("이미 다른 방 '" + existingRoomId + "'에 참가 중입니다.");
        });

        Map<String, String> roomDetailsMap = matchRoomRepository.getRoomDetailsMap(roomId);
        if(roomDetailsMap.isEmpty()){
            throw new RoomException("방 '" + roomId + "'을(를) 찾을 수 없습니다.");
        }

        boolean isPrivate = Boolean.parseBoolean(roomDetailsMap.getOrDefault("isPrivate","false"));
        String storedPasswordHash = roomDetailsMap.get("password");

        if(isPrivate){
            if(request.getPassword() == null || !passwordEncoder.matches(request.getPassword(), storedPasswordHash)){
                throw new RoomException("방 비밀번호가 일치하지 않습니다.");
            }
        }
        User joiner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RoomException("참여자 정보를 찾을 수 없습니다: " + username));

        // optimistic lock retry
        int attempts =0;
        long backoffDelay = 50; // initial delay time

        while(attempts < MAX_JOIN_ATTEMPTS){
            long result =matchRoomRepository.tryJoinRoomAtomically(roomId,username,joinerIp,joinerPort);

            switch((int)result){
                case 3: //success
                    //unlike method using Lua scripts like HGET are already executed in Repository's WATCH/MULTI/EXEC
                    matchRoomRepository.saveUserNicknameInRoom(roomId,username,joiner.getNickname());
                    log.info("User '{}' joined room '{}' (endpoint: {}:{}) via optimistic lock", username, roomId, joinerIp, joinerPort);
                    RoomDetails roomDetails = buildRoomDetails(roomId);
                    broadcastParticipantUpdate(roomId, roomDetails.getParticipants());

                    if("waiting".equals(roomDetails.getState())){
                        roomStateService.startMenuInput(roomId);
                        return buildRoomDetails(roomId);
                    }
                    return roomDetails;

                case 1: throw new RoomException("방 참여 실패: 방이 꽉 찼습니다.");
                case 2:throw  new RoomException("방 참여 실패: 이미 해당 방에 참여중입니다.");
                case -2: throw new RoomException("방'"+roomId+"'을(를) 찾을 수 없습니다(Optimistic Lock Check).");
                case -3: // crash occurred, retry
                    attempts++;
                    log.warn("Optimistic lock conflict joining room '{}' for user '{}'. Attempt {}/{}.", roomId,username,attempts,MAX_JOIN_ATTEMPTS);

                    if(attempts >= MAX_JOIN_ATTEMPTS){
                        throw new RoomException("방 참여 시도 중 충돌이 반복되어 실패했습니다. 잠시 후 다시 시도해주세요.");
                    }
                    //retry after a short term
                    try{ Thread.sleep(backoffDelay);
                        backoffDelay *= 2;
                    }
                    catch(InterruptedException e){ Thread.currentThread().interrupt(); }
                    continue;
                case -1: // repository's internal script execution failure
                case -4: // unknown prerequisite error
                default:
                        log.error("Unknown or critical error code {} from repository for joinRoom (user: {}, room: {})", result, username, roomId);
                        throw new RoomException("방 참여 중 알 수 없는 오류가 발생했습니다.");
            }
        }

        throw new RoomException("방 참여 시도 중 충돌이 반복되어 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }

    public void leaveRoom(String username){

        Optional<String> roomIdOpt = matchRoomRepository.findRoomIdByUser(username);

        if(!roomIdOpt.isPresent()){
            log.info("User '{}' not in any room. Attempting cleanup just in case.", username);
            matchRoomRepository.removeUserEndpoint(username);
            return;
        }

        String roomId = roomIdOpt.get();
        log.info("User '{}' attempting to leave room '{}'", username, roomId);

        long result = matchRoomRepository.tryLeaveRoomAtomically(username, roomId);
        Map<String, String> roomDetailsMapBeforeLeave = matchRoomRepository.getRoomDetailsMap(roomId);

        switch((int) result){
            case 0:
                log.info("User '{}' successfully left room '{}'", username, roomId);
                // broadcasting participants' list through WebSocket
                // buildRoomDetails can be called when the room still exists
                // if any players are in the room, sending updated participants list
                if(matchRoomRepository.getRoomUserCount(roomId) >0) {
                    broadcastParticipantUpdate(roomId, buildRoomDetails(roomId).getParticipants());
                }
                break;
            case 1:
                log.info("User '{}' (possibly host) left room '{}', causing the room to be closed by script.", username, roomId);
                matchRoomRepository.deleteRoomData(roomId);
                matchRoomRepository.removeRoomFromActiveList(roomId);
                broadcastRoomClosed(roomId, username);
                break;
            case 2:
                String msg2 = String.format("LeaveRoom Inconsistency: User '%s' was in room '%s' (locations) but not in user set.", username, roomId);
                log.error(msg2);
                manualCleanupAfterLeaveFailure(username,roomId);
                throw new RoomException(msg2);
            case 3:
                String msg3 = String.format("LeaveRoom Inconsistency: User '%s' location points to non-existent room '%s'.", username, roomId);
                log.error(msg3);
                manualCleanupAfterLeaveFailure(username, roomId);
                throw new RoomException(msg3);
            case -3:
                throw new RoomException("방 나가기 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            default:
                String msgDefault = String.format("Unknown result code %d from repository for leaveRoom(user: %s, room: %s)", result, username, roomId);
                log.error(msgDefault);
                throw new RoomException("방 나가기 처리 중 알 수 없는 오류가 발생했습니다.");
        }
    }
    private void validateRoomAccess(String roomId, String username){
        if(!isUserInMatchroom(roomId, username)){
            throw new SecurityException("User not authorized for this room");
        }
    }

    private void manualCleanupAfterLeaveFailure(String username, String roomId) {
        log.warn("Attempting manual cleanup for user '{}' after script failure or inconsistency in room '{}'", username, roomId);
        try{
            matchRoomRepository.removeUserLocation(username);
            matchRoomRepository.removeUserEndpoint(username);
            if(roomId != null){
                matchRoomRepository.removeUserNicknameFromRoom(roomId,username);
            }
            log.info("Manual cleanup successful for user '{}'.", username);
        }catch (Exception e){
            log.error("Error during manual cleanup for user '{}' : {}",username,e.getMessage());
        }
    }

    @Transactional
    public void clearAllMenuDataForRoom(String roomId){
        matchRoomRepository.clearSubmittedMenus(roomId);
        matchRoomRepository.clearLastDrawResult(roomId);

        Set<String> usersInRoom = matchRoomRepository.getRoomUsers(roomId);
        if(usersInRoom != null){
            for(String user: usersInRoom){
                matchRoomRepository.updateUserSubmitStatus(roomId, user, false);
            }
        }
        log.info("All menu data cleared for room '{}'.", roomId);
    }

    public void startPick(String username, String roomId){
        Map<String,String> details = matchRoomRepository.getRoomDetailsMap(roomId);
        if(details.isEmpty()){
            throw new RoomException("방 '" + roomId + "'을(를) 찾을 수 없습니다.");
        }

        String hostUsername = details.get("hostUsername");
        if(!username.equals(hostUsername)){
            throw new RoomException("호스트만 게임을 시작할 수 있습니다.");
        }
        
        String currentState = details.get("state");
        if("started".equals(currentState)){
            throw new RoomException("이미 추첨중입니다.");
        }

        matchRoomRepository.updateRoomState(roomId,"started");
        log.info("Pick started for room '{}' by host '{}'. State updated via Repository.", roomId, username);
        broadcastRoomStateUpdate(roomId, "started", buildRoomDetails(roomId));
    }

    public Optional<RoomDetails> getRoomDetails(String roomId){
        Map<String,String> details = matchRoomRepository.getRoomDetailsMap(roomId);
        if(details.isEmpty()){
            matchRoomRepository.removeRoomFromActiveList(roomId);
            return Optional.empty();
        }
        return Optional.of(buildRoomDetails(roomId,details));
    }

    public List<RoomDetails> getAllActiveRooms(){
        Set<String> activeRoomIds = matchRoomRepository.getActiveRoomIds();
        if(activeRoomIds.isEmpty() || activeRoomIds == null){
            return Collections.emptyList();
        }

        Map<String,Map<String,String>> allRoomDetailsMap = matchRoomRepository.getMultipleRoomDetails(activeRoomIds);
        Map<String,Set<String>> allRoomUsersMap = matchRoomRepository.getMultipleRoomUsers(activeRoomIds);

        return activeRoomIds.stream()
                .map(roomId -> {
                        Map<String,String> detailsMap = allRoomDetailsMap.get(roomId);
                        Set<String> userUsernames = allRoomUsersMap.get(roomId);

                        if(detailsMap == null || detailsMap.isEmpty() || userUsernames ==null){
                            log.warn("Inconsistent data for active room {}. Removing from active list.", roomId);
                            matchRoomRepository.removeRoomFromActiveList(roomId);
                            return null;
                        }
                    try{
                        return buildRoomDetails(roomId,detailsMap);
                    }catch(Exception e) {
                        log.warn("Failed to build room details for room '{}: {}'.", roomId, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull) // only filtering RoomDetails that are not null
                .collect(Collectors.toList());
    }

    public void handleDisconnect(String username){
        log.info("Handling disconnect for user '{}'.", username);
        try{
            leaveRoom(username);
        }catch (RoomException e){
            log.warn("RoomException during disconnect for user {}: {}", username, e.getMessage());
        }catch(Exception e){
            log.error("Unexpected error during disconnect for user {}: {}", username, e.getMessage(), e);
        }
        log.info("Finished disconnect handling for user '{}'.", username);
    }

    public RoomDetails buildRoomDetails(String roomId){
        Map<String,String> detailsMap = matchRoomRepository.getRoomDetailsMap(roomId);
        if(detailsMap.isEmpty()){
            throw new RoomException("방 정보를 빌드하는 중 오류: 방 '" + roomId + "'을(를) 찾을 수 없습니다.");
        }
        return buildRoomDetails(roomId, detailsMap);
    }

    private RoomDetails buildRoomDetails(String roomId, Map<String,String> detailsMap){
        Set<String> userUsernames = matchRoomRepository.getRoomUsers(roomId);
        Map<String,String> userEndpoints = matchRoomRepository.getUserEndpoints(new ArrayList<>(userUsernames));
        Map<String,String> userNicknames = matchRoomRepository.getUserNicknamesInRoom(roomId);

        List<Participant> participants = userUsernames.stream().map(username -> {
            String nickname = userNicknames.getOrDefault(username, username);
            String endpoint = userEndpoints.getOrDefault(username, "N/A");

            boolean submittedMenu = matchRoomRepository.hasUserSubmittedMenu(roomId, username);
            return new Participant(username, nickname, endpoint, submittedMenu);
        }).collect(Collectors.toList());

        String hostUsername = detailsMap.getOrDefault("hostUsername", "Unknown Host");
        String hostEndpoint = userEndpoints.getOrDefault(hostUsername, null);

        String hostIp =null;
        Integer hostPort = null;

        if(hostEndpoint != null && !hostEndpoint.isEmpty()) {
            //safely separating IPv6 address and port
            int lastColonIndex = hostEndpoint.lastIndexOf(":");
            if (lastColonIndex != -1) {
                hostIp = hostEndpoint.substring(0, lastColonIndex);
                try {
                    hostPort = Integer.parseInt(hostEndpoint.substring(lastColonIndex + 1));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse host endpoint '{}' in room '{}': {}", hostUsername, roomId,
                            hostEndpoint.substring(lastColonIndex + 1));
                }
            } else {
                //if no colon, it could be the whole address without port info
                hostIp = hostEndpoint;
            }
        }else{
            log.warn("Host endpoint is null or empty for user '{}' in room '{}'.",hostUsername,roomId);
        }

        return new RoomDetails(
                roomId,
                detailsMap.getOrDefault("name","Unknown Room"),
                hostUsername,
                hostIp,
                hostPort,
                Integer.parseInt(detailsMap.getOrDefault("maxUsers", "0")),
                new ArrayList<>(userUsernames),
                participants,
                detailsMap.getOrDefault("state","waiting"),
                Boolean.parseBoolean(detailsMap.getOrDefault("isPrivate","false")),
                detailsMap.getOrDefault("hostNickname", hostUsername)
        );
    }

        public void broadcastRoomStateUpdate(String roomId, String state, RoomDetails roomDetails){
        WebSocketMessage<RoomDetails> message = new WebSocketMessage<>("ROOM_STATE_UPDATE", roomDetails);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events",message);
        log.debug("Broadcast room state updated for room '{}:{}'.", roomId, state);
        }

        private void broadcastParticipantUpdate(String roomId, List<Participant> participants){
        WebSocketMessage<List<Participant>> message = new WebSocketMessage<>("PARTICIPANT_UPDATE", participants);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events",message);
        log.debug("Broadcast participant update for room {}: {}", roomId, participants.size());
    }

    private void broadcastRoomClosed(String roomId, String leavingUsername){
        Map<String,String> payload = new HashMap<>();
        payload.put("message", "Room closed because host or last user left.");
        payload.put("closedBy", leavingUsername);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/closed", payload);
        log.info("Broadcast room closed for room {}. Closed by {}", roomId, leavingUsername);
    }

}
