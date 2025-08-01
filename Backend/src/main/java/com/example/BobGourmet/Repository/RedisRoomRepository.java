package com.example.BobGourmet.Repository;


import com.example.BobGourmet.DTO.MenuDTO.MenuVoteDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RedisRoomRepository implements MatchRoomRepository{

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StringRedisTemplate stringRedisTemplate;

    public RedisRoomRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;

    }

    // result Constants
    public static final long JOIN_SUCCESS = 3L;
    public static final long JOIN_ERROR_ROOM_FULL = -1L;
    public static final long JOIN_ERROR_ALREADY_IN_ROOM = -2L;
    public static final long JOIN_ERROR_ROOM_NOT_FOUND = -4L;
    public static final long JOIN_ERROR_WATCH_CONFLICT = -3L;
    public static final long JOIN_ERROR_UNKNOWN = -5L;


    // -- Redis Key Constants --
    private static final String ROOM_NICKNAMES_HASH_KEY_PREFIX = "room:";
    private static final String ROOMS_ACTIVE_SET_KEY = "rooms:active_set";
    private static final String ROOM_DETAILS_HASH_KEY_PREFIX = "room:";
    private static final String ROOM_USERS_SET_KEY_PREFIX = "room:";
    private static final String USER_LOCATIONS_HASH_KEY = "user:locations";
    private static final String USER_ENDPOINTS_HASH_KEY = "user:endpoints";

    private static final String ROOM_SUBMITTED_MENUS_HASH_KEY_PREFIX = "room:";
    private static final String ROOM_SUBMIT_STATUS_HASH_KEY_PREFIX = "room:";

    private static final int MAX_ID_GENERATION_ATTEMPTS = 10;

    // --User Location & IP ---
    @Override
    public Optional<String> findRoomIdByUser(String username){

        return Optional.ofNullable(stringRedisTemplate.<String,String>opsForHash().get(USER_LOCATIONS_HASH_KEY, username));
    }

    @Override
    public void saveLastDrawResult(String roomId, String menu, long timestamp){
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        String detailsKey = getRoomDetailsKey(roomId);
        hashOps.put(detailsKey, "lastDrawResult", menu);
        hashOps.put(detailsKey, "lastDrawTimestamp", String.valueOf(timestamp));
    }

    @Override
    public Optional<String> getLastDrawResult(String roomId){
        return Optional.ofNullable(stringRedisTemplate.<String,String>opsForHash().get(getRoomDetailsKey(roomId), "lastDrawResult"));
    }

    @Override
    public Optional<Long> getLastDrawTimestamp(String roomId){
        String timestampStr = stringRedisTemplate.<String,String>opsForHash().get(getRoomDetailsKey(roomId), "lastDrawTimestamp");
        return Optional.ofNullable(timestampStr).map(Long::parseLong);
    }

    @Override
    public void saveUserEndpoint(String username, String ipAddress, int port){
        String endpoint = ipAddress + ":" + port;

        //save ipAddress as serialized JSON
        stringRedisTemplate.opsForHash().put(USER_ENDPOINTS_HASH_KEY, username, endpoint);
    }

    @Override
    public void saveUserNicknameInRoom(String roomId, String username, String nickname){
       if(nickname !=null){
           stringRedisTemplate.opsForHash().put(getRoomNicknamesKey(roomId), username, nickname);
           log.debug("Saved nickname for user '{}' in room '{}': {}", username, roomId, nickname);
       }else{
           log.warn("Attempted to save null nickname for user '{}' in room '{}'", username, roomId);
       }
    }

    @Override
    public Optional<String> getUserNicknameInRoom(String roomId, String username){
        return Optional.ofNullable(stringRedisTemplate.<String,String>opsForHash().get(getRoomNicknamesKey(roomId), username));
    }

    @Override
    public Map<String,String> getUserNicknamesInRoom(String roomId){
        HashOperations<String,String,String> hashOps = stringRedisTemplate.opsForHash();
        return hashOps.entries(getRoomNicknamesKey(roomId));
    }

    @Override
    public void removeUserNicknameFromRoom(String roomId, String username){
        Long deletedCount = stringRedisTemplate.opsForHash().delete(getRoomNicknamesKey(roomId), username);
        if(deletedCount != null && deletedCount > 0){
            log.debug("Removed nickname for user '{}' in room '{}'", username, roomId);
        }
    }

    @Override
    public void removeAllUserNicknamesFromRoom(String roomId){
        Boolean deleted = stringRedisTemplate.delete(getRoomNicknamesKey(roomId));
        if(Boolean.TRUE.equals(deleted)){
            log.debug("Removed all nickname for room '{}'", roomId);
        }
    }

    @Override
    public void removeUserEndpoint(String username){
        stringRedisTemplate.opsForHash().delete(USER_ENDPOINTS_HASH_KEY, username);
    }

    @Override
    public Map<String, String> getUserEndpoints(List<String> usernames){

        if(usernames == null || usernames.isEmpty()){
            return Collections.emptyMap();
        }

        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        List<String> endpoints = hashOps.multiGet(USER_ENDPOINTS_HASH_KEY, usernames);

        Map<String, String> resultMap = new HashMap<>();
        for (int i = 0; i < usernames.size(); i++) {
            String endpointValue = "Endpoint 정보 없음";
            if (endpoints != null && i < endpoints.size() && endpoints.get(i) != null) {
                endpointValue = endpoints.get(i);
            }
            resultMap.put(usernames.get(i), endpointValue);
        }
        return resultMap;

    }

    @Override
    public void setUserLocation(String username, String roomId){
        stringRedisTemplate.opsForHash().put(USER_LOCATIONS_HASH_KEY, username, roomId);
    }

    @Override
    public void removeUserLocation(String username){
        stringRedisTemplate.opsForHash().delete(USER_LOCATIONS_HASH_KEY, username);
    }

    @Override
    public void removeUsersLocation(List<String> usernames){
        if(usernames != null && !usernames.isEmpty()){
            stringRedisTemplate.opsForHash().delete(USER_LOCATIONS_HASH_KEY, (Object[])usernames.toArray(new String[0]));
        }
    }

    @Override
    public String generateNewRoomId(){

        int attempts =0;
        while( attempts < MAX_ID_GENERATION_ATTEMPTS){

            // 1. random ID generation
            String candidateId = "room-" +UUID.randomUUID().toString().substring(0,6);
            String detailsKey = getRoomDetailsKey(candidateId); //  ID's details key generated with candidate

            // 2. check if candidate key exists in Redis (using hasKey)
            // if key exists -> hasKey = true, if not -> false
            Boolean exists = stringRedisTemplate.hasKey(detailsKey);

            // 3. if key doesn't exist, unique ID found
            if(Boolean.FALSE.equals(exists)){
                log.info("Generated unique room ID '{}' after {} attempt(s).", candidateId, attempts +1);
                return candidateId;
            }

            // 4. if Key already exists, one attempt increment and then retry
            attempts++;
            log.warn("Room ID collision detected for '{}'. Retrying generation (attempt {}/{}...",
                    candidateId, attempts, MAX_ID_GENERATION_ATTEMPTS);
        }

        // 5. if exceeds maximum trial error occurs
        log.error("Failed to generate a unique room ID after {} attempts.", MAX_ID_GENERATION_ATTEMPTS);
        throw new RuntimeException("Could not generate a unique room ID after " + MAX_ID_GENERATION_ATTEMPTS +
                " attempts.");
    }

    @Override
    public void saveNewRoom(String roomId, String roomName, String hostUsername, String hostIp, int maxUsers,
                             boolean isPrivate, String hashedPassword, String hostNickname){

        String roomDetailsKey = getRoomDetailsKey(roomId);
        Map<String, String> roomDetails = new HashMap<>();

        roomDetails.put("name", roomName);
        roomDetails.put("hostUsername", hostUsername);
        roomDetails.put("hostIp", hostIp);
        roomDetails.put("maxUsers", String.valueOf(maxUsers));
        roomDetails.put("state", "inputting");
        roomDetails.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
        roomDetails.put("isPrivate", String.valueOf(isPrivate));
        if(isPrivate && hashedPassword != null){
            roomDetails.put("password", hashedPassword);
        }
        roomDetails.put("hostNickname", hostNickname);
        stringRedisTemplate.opsForHash().putAll(roomDetailsKey, roomDetails);
    }

    @Override
    public void addRoomToActiveList(String roomId){
        stringRedisTemplate.opsForSet().add(ROOMS_ACTIVE_SET_KEY, roomId);
    }

    @Override
    public Set<String> getActiveRoomIds(){

        // member() return Set<Object>
        Set<String> roomIds = stringRedisTemplate.opsForSet().members(ROOMS_ACTIVE_SET_KEY);
        return roomIds != null ? roomIds : Collections.emptySet();
    }

    @Override
    public Map<String, String> getRoomDetailsMap(String roomId){
        return stringRedisTemplate.<String,String>opsForHash().entries(getRoomDetailsKey(roomId));
    }

    @Override
    public Set<String> getRoomUsers(String roomId){

        // member() returns Set<Object>
        Set<String> users = stringRedisTemplate.opsForSet().members(getRoomUsersKey(roomId));
        return users != null ? users : Collections.emptySet();
    }

    @Override
    public Long getRoomUserCount(String roomId){
        // size() returns Long
        Long count = stringRedisTemplate.opsForSet().size(getRoomUsersKey(roomId));
        return count != null ? count : 0L;
    }

    @Override
    public String getRoomState(String roomId){
        return stringRedisTemplate.<String,String>opsForHash().get(getRoomDetailsKey(roomId), "state");
    }

    @Override
    public void addUserToRoom(String roomId, String username){
        stringRedisTemplate.opsForSet().add(getRoomUsersKey(roomId), username);
    }

    @Override
    public void removeRoomFromActiveList(String roomId){
        stringRedisTemplate.opsForSet().remove(ROOMS_ACTIVE_SET_KEY, roomId);
    }

    @Override
    public void deleteRoomData(String roomId){
        stringRedisTemplate.delete(Arrays.asList(getRoomDetailsKey(roomId), getRoomUsersKey(roomId),
                getRoomNicknamesKey(roomId),getRoomSubmittedMenusKey(roomId),getRoomSubmitStatusKey(roomId)));
    }

    @Override
    public void updateRoomState(String roomId, String newState){
        stringRedisTemplate.opsForHash().put(getRoomDetailsKey(roomId), "state", newState);
    }



    @Override
    public long tryJoinRoomAtomically(String roomId, String username, String joinerIp, int joinerPort){
        List<Object> execResult = stringRedisTemplate.execute(new SessionCallback<List<Object>>(){
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException{
                //keys to WATCH
                String roomDetailsKey = getRoomDetailsKey(roomId);
                String roomUsersKey = getRoomUsersKey(roomId);
                operations.watch(Arrays.asList(roomDetailsKey, roomUsersKey));



                return performJoinRoomChecksAndQueueCommands(operations, roomId, username, joinerIp, joinerPort);
            }
        });

        return analyzeExecResultForJoin(execResult, roomId, username);
    }

    @Override
    public long tryLeaveRoomAtomically(String username, String roomId) {
        List<Object> execResult = stringRedisTemplate.execute(new SessionCallback<List<Object>>(){

            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException{
                String roomDetailsKey = getRoomDetailsKey(roomId);
                String roomUsersKey = getRoomUsersKey(roomId);

                // watching the room state and users list
                operations.watch(Arrays.asList(roomDetailsKey, roomUsersKey));

                // pre-check before transaction
                if(Boolean.FALSE.equals(operations.hasKey(roomDetailsKey))){
                    operations.unwatch();
                    return Collections.singletonList(3L); //no room
                }
                if(Boolean.FALSE.equals(operations.opsForSet().isMember(roomUsersKey, username))){
                    operations.unwatch();
                    return Collections.singletonList(2L); // user not in the room
                }
                String hostUsername = (String) operations.opsForHash().get(roomDetailsKey, "hostUsername");

                // transaction begin
                operations.multi();

                // user related info deletion
                operations.opsForHash().delete(USER_LOCATIONS_HASH_KEY, username);
                operations.opsForHash().delete(USER_ENDPOINTS_HASH_KEY, username);
                operations.opsForHash().delete(getRoomNicknamesKey(roomId), username);

                // delete from room user list
                operations.opsForSet().remove(roomUsersKey, username);

                //including SCARD in transaction to check if last user or the host left
                operations.opsForSet().size(roomUsersKey);

                //transaction exec
                List<Object> results = operations.exec();

                // after transaction
                if(results ==null){ // transaction failed due to WATCH key alteration
                    return null; // returning null to cause crash on service layer
                }

                long remainingCount = (long) results.get(results.size()-1); // result of SCARD
                if(username.equals(hostUsername) || remainingCount == 0){
                    // if the host or last user left, atomic room deletion is hard to be carried out
                    // -> sending signal to carry it out on service layer
                    return Collections.singletonList(1L); // 1: room closed
                }
                else{
                    return Collections.singletonList(0L); // 0: normal exit
                }
            }
        });
        if(execResult == null){
            return -3L; // -3: WATCH crash
        }else{
            return (long) execResult.get(0);
        }
    }

    @Override
    public long createRoomAtomically(String roomId, String roomName, String hostUsername,String hostIp, int hostPort, int maxUsers,
                                     boolean isPrivate, String hashedPassword, String hostNickname){
        List<Object> execResult =stringRedisTemplate.execute(new SessionCallback<List<Object>>(){
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException{

                // checking if the user is already in other room
                operations.watch(USER_LOCATIONS_HASH_KEY);

                String existingRoom = (String) operations.opsForHash().get(USER_LOCATIONS_HASH_KEY, hostUsername);
                if(existingRoom != null){
                    operations.unwatch(); // -5: error code meaning "already in other room"
                    return Collections.singletonList(-5L);
                }

                // beginning transaction
                operations.multi();

                //queueing every commmand needed for room creation
                String roomDetailsKey = getRoomDetailsKey(roomId);
                Map<String,String> roomDetails = new HashMap<>();
                roomDetails.put("name", roomName);
                roomDetails.put("hostUsername", hostUsername);
                roomDetails.put("hostIp", hostIp);
                roomDetails.put("maxUsers", String.valueOf(maxUsers));
                roomDetails.put("state", "inputting");
                roomDetails.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
                roomDetails.put("isPrivate", String.valueOf(isPrivate));
                if(isPrivate && hashedPassword != null){
                    roomDetails.put("password", hashedPassword);
                }
                roomDetails.put("hostNickname", hostNickname);
                operations.opsForHash().putAll(roomDetailsKey, roomDetails);

                operations.opsForSet().add(ROOMS_ACTIVE_SET_KEY, roomId);
                operations.opsForSet().add(getRoomUsersKey(roomId), hostUsername);
                operations.opsForHash().put(USER_LOCATIONS_HASH_KEY, hostUsername, roomId);
                operations.opsForHash().put(USER_ENDPOINTS_HASH_KEY, hostUsername, hostIp + ":" + hostPort);
                operations.opsForHash().put(getRoomNicknamesKey(roomId), hostUsername, hostNickname);
                return operations.exec();
            }
        });

        if(execResult ==null){
            //if WATCH key changed and causes transaction failure
            return JOIN_ERROR_WATCH_CONFLICT;
        }else if(!execResult.isEmpty() && execResult.get(0) instanceof Long && (Long) execResult.get(0) == -5L){
            //if failed due to "already in other room" problem
            return -5L;
        }else{
            return JOIN_SUCCESS;
        }
    }

    @Override
    public Map<String, Map<String,String>> getMultipleRoomDetails(Set<String> roomIds){
        List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>(){
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException{
                operations.multi();
                for(String roomId : roomIds){
                    operations.opsForHash().entries(getRoomDetailsKey(roomId));
                }
                return operations.exec();
            }
        });

        Map<String,Map<String,String>> allRoomDetails = new HashMap<>();
        int i=0;
        for(String roomId : roomIds){
            if(results != null && results.get(i) instanceof Map){
                allRoomDetails.put(roomId, (Map<String,String>) results.get(i));
            }
            i++;
        }
        return allRoomDetails;
    }

    @Override
    public Map<String, Set<String>> getMultipleRoomUsers(Set<String> roomIds){
        List<Object> results = stringRedisTemplate.execute(new SessionCallback<List<Object>>(){
            @Override
            public List<Object> execute(RedisOperations operations) throws DataAccessException{
                operations.multi();
                for(String roomId : roomIds){
                    operations.opsForSet().members(getRoomUsersKey(roomId));
                }
                return operations.exec();
            }
        });

        Map<String,Set<String>> allRoomUsers = new HashMap<>();
        int i=0;
        for(String roomId : roomIds){
            if(results != null && results.get(i) instanceof Set){
                Set<String> users = ((Set<Object>) results.get(i)).stream()
                        .map(String::valueOf)
                        .collect(Collectors.toSet());
                allRoomUsers.put(roomId, users);
            }
            i++;
        }
        return allRoomUsers;
    }

    // method to call before transaction begins and after WATCH
    @SuppressWarnings("unchecked") // ignore RedisOperations cast warning
    private List<Object> performJoinRoomChecksAndQueueCommands(RedisOperations operations, String roomId, String username, String joinerIp,
                                                               int joinerPort){
        String roomDetailsKey = getRoomDetailsKey(roomId);
        String roomUsersKey = getRoomUsersKey(roomId);

        // data lookup after WATCH begins (before transaction begins)
        Map<Object, Object> roomDetailsRaw = operations.opsForHash().entries(roomDetailsKey);
        Long currentPlayerCountObj = operations.opsForSet().size(roomUsersKey);
        Boolean isMember = operations.opsForSet().isMember(roomUsersKey, username);

        // --- 디버깅 로그 추가 ---
                 log.info("--- JOIN CHECK ---");
                 log.info("Room ID: {}", roomId);
                 log.info("Joiner: {}", username);
                 log.info("Room Details Raw: {}", roomDetailsRaw);
                 log.info("Current Player Count Obj: {}", currentPlayerCountObj);

        // condition check in application level
        if(roomDetailsRaw.isEmpty()){
            operations.unwatch();
            return Collections.singletonList(JOIN_ERROR_ROOM_NOT_FOUND); // no room
        }

        String currentState = String.valueOf(roomDetailsRaw.get("state"));
        int maxUsers = Integer.parseInt(String.valueOf(roomDetailsRaw.getOrDefault("maxUsers", 0)));
        long currentPlayerCount = (currentPlayerCountObj != null) ? currentPlayerCountObj : 0L;

        if(Boolean.TRUE.equals(isMember)){
            operations.unwatch();
            return Collections.singletonList(JOIN_ERROR_ALREADY_IN_ROOM);
        }
        if(currentPlayerCount >= maxUsers){
            operations.unwatch();
            return Collections.singletonList(JOIN_ERROR_ROOM_FULL);
        }
        // condition check over

        // MULTI: transaction begins
        operations.multi();

        // queuing scripts in the transaction
        operations.opsForSet().add(roomUsersKey,username);
        operations.opsForHash().put(USER_LOCATIONS_HASH_KEY,username, roomId);
        operations.opsForHash().put(USER_ENDPOINTS_HASH_KEY,username, joinerIp + ":" + joinerPort);

        // transaction executes(returning SessionCallback)
        return operations.exec();
    }

    // returning final result after analyzing the result of EXEC
    private long analyzeExecResultForJoin(List<Object> execResult, String roomId, String username){

        if(execResult == null){
            log.warn("Optimistic lock failed for joinRoom: roomId = {}, user = {}. WATCH key changed.", roomId, username);
            return JOIN_ERROR_WATCH_CONFLICT; // retry needed after crash
        }

        if(!execResult.isEmpty() && execResult.get(0) instanceof Long){
                long preCheckResult = (Long) execResult.get(0);
                if(preCheckResult<0){
                    return preCheckResult;
                }

            // transaction succeeded, all scripts are successfully queued
            log.info("Successfully joined room via optimistic lock for roomId = {}, user = {}", roomId, username);
            return JOIN_SUCCESS;
        }else {
            //in case execResult is empty(theoretically cannot occur at all)
            log.error("Unexpected empty execResult for joinRoom: roomId = {}, user = {}", roomId, username);
            return JOIN_ERROR_UNKNOWN;
        }
    }

    public void saveSubmittedMenus(String roomId, String username, List<String> menus){

        String roomSubmittedMenusKey = getRoomSubmittedMenusKey(roomId);
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();

        List<String> previousMenus = getSubmittedMenus(roomId,username);
        for(String prevMenu : previousMenus){
            updateMenuVoteInfo(roomId, prevMenu, "submitters", username, false);
        }

        for(String menu : menus){
            updateMenuVoteInfo(roomId, menu, "submitters", username, true);
        }
    }

    @Override
    public List<String> getSubmittedMenus(String roomId, String username) {

        Map<String,MenuVoteDetails> allMenusWithDetails = getAllSubmittedMenusWithDetails(roomId);
        List<String> userMenus = new ArrayList<>();
        allMenusWithDetails.forEach((menu,menuDetails) -> {
            if(menuDetails.getSubmitters().contains(username)){
                userMenus.add(menu);
            }
        });
        return userMenus;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, MenuVoteDetails> getAllSubmittedMenusWithDetails(String roomId){
        Map<String,String> rawMenuDetails = stringRedisTemplate.<String,String>opsForHash().entries(getRoomSubmittedMenusKey(roomId));
        Map<String, MenuVoteDetails> result = new HashMap<>();
        for(Map.Entry<String, String> entry : rawMenuDetails.entrySet()){
            try{
                result.put(entry.getKey(), objectMapper.readValue(entry.getValue(), MenuVoteDetails.class));
            }catch (Exception e){
                log.error("Error parsing menu details for menu {} in room {}: {}", entry.getKey(), roomId, e.getMessage());
            }
        }
        return result;
    }

    @Override
    public Map<String, List<String>> getAllSubmittedMenusInRoom(String roomId) {

        Map<String, List<String>> result = new HashMap<>();
        Set<String> users = getRoomUsers(roomId);
        for(String user: users){
            result.put(user, getSubmittedMenus(roomId, user));
        }

        return result;
    }

    @Override
    public boolean hasUserSubmittedMenu(String roomId, String username) {
        String status = stringRedisTemplate.<String,String>opsForHash().get(getRoomSubmitStatusKey(roomId),username);
        return Boolean.parseBoolean(status);
    }

    @Override
    public boolean haveAllUsersSubmitted(String roomId){
        Set<String> usersInRoom = getRoomUsers(roomId);
        if(usersInRoom == null || usersInRoom.isEmpty()){ return true;}

        Map<String,Boolean> submitStatus = getRoomSubmitStatus(roomId);
        for(String user: usersInRoom){
            if(!submitStatus.getOrDefault(user, false)){return false;}
        }
        return true;
    }

    @Override
    public Set<String> getAllUniqueMenuKeysInRoom(String roomId){
        Set<Object> rawKeys = stringRedisTemplate.opsForHash().keys(getRoomSubmittedMenusKey(roomId));
        if (rawKeys == null) {
            return Collections.emptySet();
        }
        return rawKeys.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public void clearSubmittedMenus(String roomId) {
        stringRedisTemplate.delete(getRoomSubmittedMenusKey(roomId));
    }

    @Override
    public void clearLastDrawResult(String roomId){
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        String detailsKey = getRoomDetailsKey(roomId);
        hashOps.delete(detailsKey, "lastDrawResult", "lastDrawTimestamp");
    }


    @Override
    public void removeMenuVote(String roomId, String menuKey, String voterUsername) {
        updateMenuVoteInfo(roomId, menuKey, "recommenders", voterUsername, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Long getMenuVoteCount(String roomId, String menuKey) {
        String menuDetailsJson = stringRedisTemplate.<String,String>opsForHash().get(getRoomSubmittedMenusKey(roomId),menuKey);
        if(menuDetailsJson != null){
            try{
                Map<String,Object> menuDetails = objectMapper.readValue(menuDetailsJson, new TypeReference<Map<String, Object>>() {});
                Set<String> recommenders = (Set<String>) menuDetails.getOrDefault("recommenders", Collections.emptySet());
                return (long) recommenders.size();
            }catch(Exception e){
                log.error("Error getting vote count for menu {} in room {}: {}", menuKey, roomId, e.getMessage());
            }
        }
        return 0L;
    }


    @SuppressWarnings("unchecked")
    public Set<String> getMenuDislikers(String roomId, String menuKey) {
        String menuDetailsJson = stringRedisTemplate.<String,String>opsForHash().get(getRoomSubmittedMenusKey(roomId),menuKey);
        if(menuDetailsJson != null){
            try{
                Map<String,Object> menuDetails = objectMapper.readValue(menuDetailsJson, new TypeReference<Map<String, Object>>() {});
                return (Set<String>) menuDetails.getOrDefault("dislikedBy", Collections.emptySet());
            }catch(Exception e){
                log.error("Error getting dislikers for menu {} in room {}: {}", menuKey, roomId, e.getMessage());}
        }
        return Collections.emptySet();
    }


    @Override
    public boolean isMenuExcluded(String roomId, String menuKey) {
        String menuDetailsJson = stringRedisTemplate.<String,String>opsForHash().get(getRoomSubmittedMenusKey(roomId), menuKey);
        if(menuDetailsJson != null){
            try{
                Map<String,Object> menuDetails = objectMapper.readValue(menuDetailsJson, new TypeReference<Map<String, Object>>() {});
                return (Boolean) menuDetails.getOrDefault("isExcluded", false);
            }catch(Exception e){
                log.error("Error checking if menu {} is excluded in room {}: {}", menuKey, roomId, e.getMessage());
            }
        }
        return false;
    }

    @Override
    public void markMenuAsExcluded(String roomId, String menuKey, boolean excluded) {
        updateMenuDetailsField(roomId, menuKey, "isExcluded", excluded);
    }

    @Override
    public void updateUserSubmitStatus(String roomId, String username, boolean submitted) {
        stringRedisTemplate.opsForHash().put(getRoomSubmitStatusKey(roomId), username, String.valueOf(submitted));
    }

    @Override
    public Map<String, Boolean> getRoomSubmitStatus(String roomId) {

        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        Map<String,String> rawStatus = hashOps.entries(getRoomSubmitStatusKey(roomId));
        Map<String, Boolean> statusMap = new HashMap<>();
        rawStatus.forEach((user, statusStr) -> statusMap.put(user, Boolean.parseBoolean(statusStr)));
        return statusMap;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updateMenuVoteInfo(String roomId, String menuKey, String voteType, String username, boolean add){
        String roomSubmittedMenusKey = getRoomSubmittedMenusKey(roomId);
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        String menuDetailsJson = hashOps.get(roomSubmittedMenusKey, menuKey);


        try{
            MenuVoteDetails menuDetails;
            if(menuDetailsJson != null){
                menuDetails = objectMapper.readValue(menuDetailsJson, MenuVoteDetails.class);
            }else{
                menuDetails = new MenuVoteDetails();
            }

            Set<String> voters;
            switch(voteType){
                case "submitters":
                    voters = menuDetails.getSubmitters();
                    break;
                case "recommenders":
                    voters = menuDetails.getRecommenders();
                    break;
                    case "dislikedBy":
                    voters=menuDetails.getDislikedBy();
                    break;
                default:
                    log.error("Unknown vote type {} in updateMenuVoteInfo", voteType);
                    return;
            }

            if(add){
                voters.add(username);
            }else{
                voters.remove(username);
            }

            hashOps.put(roomSubmittedMenusKey, menuKey, objectMapper.writeValueAsString(menuDetails));
            log.debug("Updated '{}' for menu '{}' in room '{}'. User: {}, Add:{}", voteType, menuKey, roomId, username, add);
        }catch(Exception e){
            log.error("Error updating '{}' for menu '{}' in room '{}': {}", voteType, menuKey, roomId, e.getMessage());
        }
    }

    @Override
    public void updateMenuDetailsField(String roomId, String menuKey, String fieldName, Object value){
        String roomSubmittedMenusKey = getRoomSubmittedMenusKey(roomId);
        HashOperations<String, String, String> hashOps = stringRedisTemplate.opsForHash();
        String menuDetailsJson = hashOps.get(roomSubmittedMenusKey, menuKey);

        try{
            MenuVoteDetails menuDetails;
            if(menuDetailsJson != null){
                menuDetails = objectMapper.readValue(menuDetailsJson, MenuVoteDetails.class);
            }else{
                if(value == null){
                    log.warn("메뉴 '{}'가 방 '{}'에 없어 필드 '{}'를 업데이트할 수 없습니다 (값 삭제 시도)", menuKey,roomId, fieldName);
                    return;
            }
            log.warn("메뉴 '{}'가 방 '{}'에 없어 새로 생성하며 필드 '{}'를 설정합니다.", menuKey, roomId, fieldName);

            menuDetails = new MenuVoteDetails();
        }


        if("isExcluded".equals(fieldName) && value instanceof Boolean){
            menuDetails.setExcluded((Boolean) value);
        }else {
            log.warn("Usupported field '{}' in updateMenuDetailsField", fieldName);
            return;
        }

        hashOps.put(roomSubmittedMenusKey, menuKey, objectMapper.writeValueAsString(menuDetails));
    }catch(Exception e){
            log.error("Error updating field '{}' for menu '{}' in room '{}':{}", fieldName, menuKey,roomId, e.getMessage(),e);
        }
    }

    private String getRoomMenuQuotasKey(String roomId){
        return "room:" + roomId + ":menu_quotas";
    }

    public void initUserMenuQuota(String roomId, String username, int quota){
        stringRedisTemplate.opsForHash().put(getRoomMenuQuotasKey(roomId), username, String.valueOf(quota));
    }

    public long decrementUserMenuQuota(String roomId, String username){
        return stringRedisTemplate.opsForHash().increment(getRoomMenuQuotasKey(roomId), username, -1);
    }

    public int getUserMenuQuota(String roomId, String username){
        String quotaStr = (String)stringRedisTemplate.opsForHash().get(getRoomMenuQuotasKey(roomId), username);
        return (quotaStr == null) ? 0 : Integer.parseInt(quotaStr);
    }

    private String getRoomNicknamesKey(String roomId){return ROOM_NICKNAMES_HASH_KEY_PREFIX + roomId + ":nicknames";}
    private String getRoomDetailsKey(String roomId){
        return ROOM_DETAILS_HASH_KEY_PREFIX + roomId + ":details";
    }
    private String getRoomUsersKey(String roomId){
        return ROOM_USERS_SET_KEY_PREFIX  + roomId + ":users";
    }

    private String getRoomUserMenusKey(String roomId, String username){
        return "room:" + roomId + ":user:" + username + ":menus";
    }

    @Override
    public String getRoomSubmittedMenusKey(String roomId){
        return ROOM_SUBMITTED_MENUS_HASH_KEY_PREFIX + roomId + ":submitted_menus";
    }

    private String getRoomAllMenusKey(String roomId) {
        return "room:" + roomId + ":all_menus";
    }

    private String getRoomMenuVotesKey(String roomId, String menuKey){
        return "room:" + roomId + ":menu:" + menuKey.replace("::", "_") + ":votes";
    }

    private String getRoomDislikedMenusKey(String roomId) {
        return "room:" + roomId + ":disliked_menus";
    }

    private String getRoomSubmitStatusKey(String roomId){
        return ROOM_SUBMIT_STATUS_HASH_KEY_PREFIX + roomId + ":submit_status";
    }
}
