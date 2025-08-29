package com.example.BobGourmet.Service;

import com.example.BobGourmet.Repository.MatchRoomRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomStateService {

    private final MatchRoomRepository matchRoomRepository;

    //changing room state to "inputting"
    @Transactional
    public void startMenuInput(String roomId){
        matchRoomRepository.updateRoomState(roomId,"inputting");
        matchRoomRepository.clearSubmittedMenus(roomId);
        matchRoomRepository.clearLastDrawResult(roomId);
    }
    
    // New method: Only change state to inputting without clearing data (for late joiners)
    @Transactional
    public void resumeMenuInput(String roomId){
        matchRoomRepository.updateRoomState(roomId,"inputting");
        // Don't clear submitted menus - preserve existing submissions for late joiners
    }

    //changing room state to "submitted"
    @Transactional
    public void allMenusSubmitted(String roomId){
        matchRoomRepository.updateRoomState(roomId, "submitted");
    }

    // changing room state to "result_viewing"
    @Transactional
    public void startResultViewing(String roomId, String selectedMenu, long timestamp){
        matchRoomRepository.saveLastDrawResult(roomId, selectedMenu, timestamp);
        matchRoomRepository.updateRoomState(roomId, "result_viewing");
    }
}
