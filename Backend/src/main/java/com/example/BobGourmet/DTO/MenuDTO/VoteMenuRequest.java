package com.example.BobGourmet.DTO.MenuDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VoteMenuRequest {
    @NotBlank private String targetMenuKey;
}
