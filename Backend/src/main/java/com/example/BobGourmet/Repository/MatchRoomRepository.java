package com.example.BobGourmet.Repository;

import com.example.BobGourmet.DTO.MenuDTO.MenuVoteDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface MatchRoomRepository {

    Optional<String> findRoomIdByUser(String username);

    void saveLastDrawResult(String roomId, String menu, long timestamp);

    Optional<String> getLastDrawResult(String roomId);

    Optional<Long> getLastDrawTimestamp(String roomId);

    void saveUserEndpoint(String username, String ipAddress, int port);

    void saveUserNicknameInRoom(String roomId, String username, String nickname);

    Optional<String> getUserNicknameInRoom(String roomId, String username);

    Map<String,String> getUserNicknamesInRoom(String roomId);

    void removeUserNicknameFromRoom(String roomId, String username);

    void removeAllUserNicknamesFromRoom(String roomId);

    void removeUserEndpoint(String username);
    Map<String, String> getUserEndpoints(List<String> usernames);

    String generateNewRoomId();

    void saveNewRoom(String roomId, String roomName, String hostUsername, String hostIp, int maxUsers,
                      boolean isPrivate, String hashedPassword, String hostNickname);

    void addRoomToActiveList(String roomId);
    Set<String> getActiveRoomIds();

    int getUserMenuQuota(String roomId, String username);
    void initUserMenuQuota(String roomId, String username, int quota);
    long decrementUserMenuQuota(String roomId, String username);

    Map<String, String> getRoomDetailsMap(String roomId);
    Set<String> getRoomUsers(String roomId);
    Long getRoomUserCount(String roomId);
    String getRoomState(String roomId);

    void addUserToRoom(String roomId, String username);
    void setUserLocation(String username, String roomId);

    // Lua script execution method(result return code)
    long tryJoinRoomAtomically(String roomId, String username, String joinerIp, int joinerPort);
    long tryLeaveRoomAtomically(String username, String roomId);
    long createRoomAtomically(String roomId, String username, String hostUsername, String hostIp,int hostPort, int maxUsers,
                              boolean isPrivate, String hashedPassword, String hostNickname);

    void removeRoomFromActiveList(String roomId);
    void deleteRoomData(String roomId);
    void removeUserLocation(String username); // one user
    void removeUsersLocation(List<String> usernames); // many users

    void updateRoomState(String roomId, String newState);

    Map<String, Map<String,String>> getMultipleRoomDetails(Set<String> roomIds);

    Map<String, Set<String>> getMultipleRoomUsers(Set<String> roomIds);

    void saveSubmittedMenus(String roomId, String username, List<String> menus);
    List<String> getSubmittedMenus(String roomId, String username);

    @SuppressWarnings("unchecked")
    Map<String, MenuVoteDetails> getAllSubmittedMenusWithDetails(String roomId);

    Map<String, List<String>> getAllSubmittedMenusInRoom(String roomId);
    boolean hasUserSubmittedMenu(String roomId, String username);

    public Set<String> getMenuDislikers(String roomId, String menuKey);

    boolean haveAllUsersSubmitted(String roomId);

    Set<String> getAllUniqueMenuKeysInRoom(String roomId);

    boolean isMenuExcluded(String roomId, String menuKey);

    void clearSubmittedMenus(String roomId);

    void clearLastDrawResult(String roomId);

    void removeMenuVote(String roomId, String menuKey, String voterUsername);

    Long getMenuVoteCount(String roomId, String menuKey);



    void markMenuAsExcluded(String roomId, String menuKey, boolean excluded);

    void updateUserSubmitStatus(String roomId, String username, boolean submitted);
    Map<String, Boolean> getRoomSubmitStatus(String roomId);

    @SuppressWarnings("unchecked")
    void updateMenuVoteInfo(String roomId, String menuKey, String voteType, String username, boolean add);

    void updateMenuDetailsField(String roomId, String menuKey, String fieldName, Object value);

    String getRoomSubmittedMenusKey(String roomId);
}