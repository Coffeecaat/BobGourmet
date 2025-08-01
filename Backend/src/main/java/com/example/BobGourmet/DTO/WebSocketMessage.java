package com.example.BobGourmet.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {
    private String type; // 메시지 타입(예: "PARTICIPANT_UPDATE", "MENU_STATUS", "DRAW_RESULT")
    private T payload;
}