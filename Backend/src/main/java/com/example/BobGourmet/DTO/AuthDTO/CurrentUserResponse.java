package com.example.BobGourmet.DTO.AuthDTO;

// Never serialize the User entity: it contains password and verification fields.
public record CurrentUserResponse(String username, String email, String nickname) {
}
