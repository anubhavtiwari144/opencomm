package com.opencomm.websocket;

import com.opencomm.dto.StatusEvent;
import com.opencomm.service.RoomService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

@Component
public class WebSocketLifecycleListener {
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketLifecycleListener(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String socketSessionId = accessor.getSessionId();
        String clientSessionId = firstNativeHeader(accessor, "client-session-id");
        roomService.bindSocketSession(socketSessionId, clientSessionId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String clientSessionId = roomService.unbindSocketSession(accessor.getSessionId());
        if (clientSessionId == null) {
            return;
        }

        RoomService.DisconnectResult result = roomService.handleClientDisconnect(clientSessionId);
        if (!result.isChanged()) {
            return;
        }

        String destination = "/topic/room/" + result.getRoomId() + "/status";
        if (result.isDeleted()) {
            messagingTemplate.convertAndSend(destination, StatusEvent.deleted(result.getRoomId(), result.getMessage()));
        } else {
            messagingTemplate.convertAndSend(destination, StatusEvent.of("SESSION_DISCONNECTED", result.getRoom(), result.getMessage()));
        }
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
