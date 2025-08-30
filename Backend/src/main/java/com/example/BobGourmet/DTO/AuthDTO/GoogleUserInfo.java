package com.example.BobGourmet.DTO.AuthDTO;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class GoogleUserInfo {

    private String sub;     // Google's unique ID
    private String email;
    private String name;
    private String givenName;
    private String familyName;
    private String picture;
}
