package com.example.BobGourmet.DTO.RoomDTO;

import com.example.BobGourmet.DTO.Participant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomDetails {
    private String roomId;
    private String roomName;
    private String hostUsername;
    private String hostIpAddress;
    private Integer hostPort;
    private int maxUsers;
    private List<String> users; // current list of users
    private List<Participant> participants;
    private String state; // "WAITING", "SUBMITTING_MENUS", "VOTING", "DRAWING", "RESULT_DISPLAYED"
    private boolean isPrivate;
    private String hostNickname;


}