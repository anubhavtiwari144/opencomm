package com.opencomm.websocket;

import com.opencomm.dto.ChatMessageEvent;
import com.opencomm.dto.JoinRequest;
import com.opencomm.dto.RoomActionRequest;
import com.opencomm.dto.SendMessageRequest;
import com.opencomm.dto.StatusEvent;
import com.opencomm.model.Room;
import com.opencomm.service.RoomService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;

@Controller
public class ChatWebSocketController {
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/join")
    public void join(@Valid JoinRequest request) {
        Room room = roomService.requestJoin(request.getRoomId(), request.getSessionId(), request.getName());
        sendStatus(room, "JOIN_REQUESTED", request.getName() + " wants to join");
    }

    @MessageMapping("/accept")
    public void accept(@Valid RoomActionRequest request) {
        Room room = roomService.acceptGuest(request.getRoomId(), request.getSessionId());
        sendStatus(room, "GUEST_ACCEPTED", room.getGuest().getName() + " joined the room");
    }

    @MessageMapping("/reject")
    public void reject(@Valid RoomActionRequest request) {
        Room room = roomService.rejectGuest(request.getRoomId(), request.getSessionId());
        sendStatus(room, "GUEST_REJECTED", "Guest request rejected");
    }

    @MessageMapping("/send")
    public void send(@Valid SendMessageRequest request) {
        ChatMessageEvent event = roomService.createMessage(
                request.getRoomId(),
                request.getSessionId(),
                request.getText()
        );
        messagingTemplate.convertAndSend("/topic/room/" + request.getRoomId(), event);
    }

    @MessageMapping("/end")
    public void end(@Valid RoomActionRequest request) {
        Room room = roomService.endRoom(request.getRoomId(), request.getSessionId());
        messagingTemplate.convertAndSend(
                "/topic/room/" + request.getRoomId() + "/status",
                StatusEvent.of("ROOM_ENDED", room, "Host ended the room")
        );
    }

    private void sendStatus(Room room, String type, String message) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + room.getRoomId() + "/status",
                StatusEvent.of(type, room, message)
        );
    }
}
