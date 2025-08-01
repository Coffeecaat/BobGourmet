package com.example.BobGourmet.DTO.MenuDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DislikeMenuRequest {

    @NotBlank
    private String targetMenuKey;
}
