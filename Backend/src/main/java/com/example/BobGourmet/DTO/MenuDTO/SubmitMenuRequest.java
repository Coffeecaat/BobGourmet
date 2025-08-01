package com.example.BobGourmet.DTO.MenuDTO;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SubmitMenuRequest {

    @NotNull
    @Size(min =1, max =4) private List<String> menus;

}
