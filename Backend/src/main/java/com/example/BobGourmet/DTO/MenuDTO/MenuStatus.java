package com.example.BobGourmet.DTO.MenuDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MenuStatus {

    private Map<String, List<String>> submittedMenusByUsers;
    private Map<String, MenuVoteDetails> menuVotes;
    private Set<String> dislikedAndExcludedMenuKeys;
    private Map<String,Boolean> userSubmitStatus;
}
