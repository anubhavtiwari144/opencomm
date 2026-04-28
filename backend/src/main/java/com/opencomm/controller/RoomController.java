package com.opencomm.controller;

import com.opencomm.dto.CreateRoomRequest;
import com.opencomm.dto.RoomActionRequest;
import com.opencomm.dto.RoomResponse;
import com.opencomm.model.Room;
import com.opencomm.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@RequestBody CreateRoomRequest request) {
        Room room = roomService.createRoom(request.getSessionId(), request.getName());
        return RoomResponse.from(room);
    }

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@PathVariable String roomId, @RequestParam String sessionId) {
        return RoomResponse.from(roomService.getReadableRoom(roomId, sessionId));
    }

    @DeleteMapping("/{roomId}")
    public RoomResponse endRoom(@PathVariable String roomId, @Valid @RequestBody RoomActionRequest request) {
        return RoomResponse.from(roomService.endRoom(roomId, request.getSessionId()));
    }
}
