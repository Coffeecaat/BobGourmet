package com.example.BobGourmet.DTO.MenuDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuVoteDetails {
    private Set<String> recommenders = new HashSet<>();
    private Set<String> submitters = new HashSet<>();
    private Set<String> dislikedBy = new HashSet<>();
    private boolean isExcluded = false;
}
