package com.example.BobGourmet.DTO.RoomDTO;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "방 이름은 필수입니다.")
    @Size(min =2, max = 20, message = "방 이름은 2자 이상 20자 이하만 됩니다.")
    private String roomName;

    @Min(value = 2, message = "최소 인원은 2명입니다.")
    @Max(value =10, message = "최대 인원은 10명입니다.")
    private int maxUsers; // default 4 users

    private boolean isPrivate;
    private String password;
}