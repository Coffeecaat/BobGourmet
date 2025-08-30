package com.example.BobGourmet.DTO.AuthDTO;

@lombok.Getter
@lombok.Setter
public class GoogleOAuthRequest {
    private String code;
    private String state;
}
