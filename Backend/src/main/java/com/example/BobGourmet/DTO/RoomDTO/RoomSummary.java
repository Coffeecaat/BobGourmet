package com.example.BobGourmet.DTO.RoomDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomSummary {

    private String roomId;
    private String roomName;
    private String hostUsername;
    private int currentUserCount;
    private int maxUsers;
    private boolean GameStarted;

    private boolean isPrivate;
    private String hostNickname;

}