package com.example.BobGourmet.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Participant {

    private String username;
    private String nickname;
    private String endpoint;
    private boolean submittedMenu;
}
