package com.example.BobGourmet.DTO.AuthDTO;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank @Size(min = 3, max = 20) private String username;
    @NotBlank @Email private String email;
    @NotBlank @Size(min = 3, max = 20) private String nickname;
    @NotBlank @Size(min =6, max = 40) private String password;
}
