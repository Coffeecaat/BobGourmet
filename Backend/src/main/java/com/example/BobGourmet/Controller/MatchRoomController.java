package com.example.BobGourmet.Controller;

import com.example.BobGourmet.DTO.MenuDTO.MenuStatus;
import com.example.BobGourmet.DTO.MenuDTO.SubmitMenuRequest;
import com.example.BobGourmet.DTO.RoomDTO.CreateRoomRequest;
import com.example.BobGourmet.DTO.RoomDTO.JoinRoomRequest;
import com.example.BobGourmet.DTO.RoomDTO.RoomDetails;
import com.example.BobGourmet.Exception.RoomException;
import com.example.BobGourmet.Service.MatchroomService;
import com.example.BobGourmet.Service.MenuService;
import com.example.BobGourmet.Service.RoomStateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name= "MatchRooms", description="매치룸 관련 API")
@Slf4j
@RestController
@RequestMapping("/api/MatchRooms")
@RequiredArgsConstructor
public class MatchRoomController {

    private final MatchroomService matchroomService;
    private final MenuService menuService;
    private final RoomStateService roomStateService;

    @GetMapping
    public ResponseEntity<List<RoomDetails>> getActiveRooms() {
        List<RoomDetails> rooms = matchroomService.getAllActiveRooms();
        return ResponseEntity.ok(rooms);
    }

    @Operation(summary = "방 정보 조회", description = "특정 방의 상세 정보 조회")
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDetails> getRoomInfo(@AuthenticationPrincipal UserDetails userDetails,
                                                   @PathVariable String roomId) {
        try {
            RoomDetails roomDetails = matchroomService.buildRoomDetails(roomId);
            return ResponseEntity.ok(roomDetails);
        } catch (RoomException e) {
            if (e.getMessage().contains("찾을 수 없습니다")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            throw e;
        }
    }

    @Operation(summary= "방 생성", description="새로운 매치룸 생성")
    @PostMapping
    public ResponseEntity<RoomDetails> createRoom(@AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody CreateRoomRequest request,
                                                  HttpServletRequest httpServletRequest) {
        String hostIp = httpServletRequest.getRemoteAddr(); // IP address of the client who sent the request
        int hostPort = httpServletRequest.getRemotePort(); // port of the client who sent the request

        RoomDetails roomDetails = matchroomService.createRoom(
                userDetails.getUsername(),
                request,
                hostIp,
                hostPort);
        return new ResponseEntity<>(roomDetails, HttpStatus.CREATED);
    }

    @Operation(summary= "방 참여", description="생성되어 있는 매치룸에 참여")
    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomDetails> joinRoom(@AuthenticationPrincipal UserDetails userDetails,
                                                @PathVariable String roomId,
                                                @RequestBody JoinRoomRequest request,
                                                HttpServletRequest httpServletRequest) {
        String joinerIp = httpServletRequest.getRemoteAddr(); // joiner's IP address
        int joinerPort = httpServletRequest.getRemotePort();
        RoomDetails roomDetails = matchroomService.joinRoom(userDetails.getUsername(), roomId, request,
                joinerIp, joinerPort);
        return ResponseEntity.ok(roomDetails);
    }

    @Operation(summary= "방 퇴장", description="참여중인 방에서 퇴장")
    @PostMapping("/{roomId}/leave")
    public ResponseEntity<String> leaveRoom(@AuthenticationPrincipal UserDetails userDetails,
                                            @PathVariable String roomId){
        matchroomService.leaveRoom(userDetails.getUsername());
        return ResponseEntity.ok("Left Room successfully");
    }

    @Operation(summary= "메뉴 제출", description="작성한 메뉴 제출")
    @PostMapping("/{roomId}/menus")
    public ResponseEntity<MenuStatus> submitMenus(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable String roomId,
                                                  @RequestBody SubmitMenuRequest request) {
        Map<String,Object> result = menuService.submitMenus(userDetails.getUsername(), roomId, request);
        String nextState = (String) result.get("nextState");

        if("submitted".equals(nextState)){
            roomStateService.allMenusSubmitted(roomId);
            matchroomService.broadcastRoomStateUpdate(roomId, "submitted",matchroomService.buildRoomDetails(roomId));
        }else if("inputting".equals(nextState)){
            roomStateService.startMenuInput(roomId);
            matchroomService.broadcastRoomStateUpdate(roomId, "inputting",matchroomService.buildRoomDetails(roomId));
        }
        return ResponseEntity.ok((MenuStatus)result.get("menuStatus"));
    }

    @Operation(summary= "추첨 시작", description="추첨된 메뉴들을 기반으로 추첨 시작")
    @PostMapping("/{roomId}/start-draw")
    public ResponseEntity<RoomDetails> startDraw(@AuthenticationPrincipal UserDetails userDetails,
                                                 @PathVariable String roomId){

        try {
            Map<String, Object> drawResult = menuService.startDraw(userDetails.getUsername(), roomId);
            String selectedMenu = (String) drawResult.get("selectedMenu");
            long timestamp = (long) drawResult.get("timestamp");
            roomStateService.startResultViewing(roomId, selectedMenu, timestamp);
            RoomDetails roomDetails = matchroomService.buildRoomDetails(roomId);
            matchroomService.broadcastRoomStateUpdate(roomId, "result_viewing",roomDetails);
            return ResponseEntity.ok(roomDetails);
        }
        catch (RoomException e) {
            if(e.getMessage().contains("추첨할 메뉴가 없습니다.")){
                log.warn("Draw failed for room {}: {}. Resetting room.", roomId, e.getMessage());
                roomStateService.startMenuInput(roomId);
                RoomDetails updatedDetails = matchroomService.buildRoomDetails(roomId);
                return new ResponseEntity<>(updatedDetails, HttpStatus.OK);
            }throw e;

        }
    }


    @Operation(summary= "방 초기화", description="메뉴 다시 받기 위해 초기화")
    @PostMapping("/{roomId}/reset")
    public ResponseEntity<RoomDetails> resetDraw(@AuthenticationPrincipal UserDetails userDetails,@PathVariable String roomId){
        menuService.resetDraw(userDetails.getUsername(), roomId);
        roomStateService.startMenuInput(roomId);
        RoomDetails updatedDetails = matchroomService.buildRoomDetails(roomId);
        matchroomService.broadcastRoomStateUpdate(roomId, "inputting",updatedDetails);

        return ResponseEntity.ok(updatedDetails);
    }

    @Operation(summary= "특정 메뉴 추천", description="추가되어 있는 특정 메뉴 추천(대신 개인이 추가 가능한 메뉴 하나 삭감")
    @PostMapping("/{roomId}/menus/{menuKey}/recommend")
    public ResponseEntity<MenuStatus> recommendMenu(@AuthenticationPrincipal UserDetails userDetails,
                                                    @PathVariable String roomId,
                                                    @PathVariable String menuKey){
        MenuStatus menuStatus = menuService.recommendMenu(userDetails.getUsername(), roomId, menuKey);
        return ResponseEntity.ok(menuStatus);
    }

    @Operation(summary= "특정 메뉴 비추", description="추가되어 있는 특정 메뉴 제거")
    @PostMapping("{roomId}/menus/{menuKey}/dislike")
    public ResponseEntity<MenuStatus> dislikeMenu(@AuthenticationPrincipal UserDetails userDetails,
                                                  @PathVariable String roomId,
                                                  @PathVariable String menuKey){
        MenuStatus menuStatus = menuService.dislikeMenu(userDetails.getUsername(), roomId, menuKey);
        return ResponseEntity.ok(menuStatus);
    }


}
