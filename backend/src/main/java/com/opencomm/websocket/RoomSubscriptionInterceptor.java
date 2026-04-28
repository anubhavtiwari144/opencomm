package com.opencomm.websocket;

import com.opencomm.service.RoomService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class RoomSubscriptionInterceptor implements ChannelInterceptor {
    private static final String ROOM_TOPIC_PREFIX = "/topic/room/";

    private final RoomService roomService;

    public RoomSubscriptionInterceptor(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        String roomId = extractRoomId(destination);
        if (roomId == null) {
            return message;
        }

        String clientSessionId = roomService.resolveClientSessionId(accessor.getSessionId());
        roomService.getReadableRoom(roomId, clientSessionId);
        return message;
    }

    private String extractRoomId(String destination) {
        if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
            return null;
        }
        String roomPart = destination.substring(ROOM_TOPIC_PREFIX.length());
        int slashIndex = roomPart.indexOf('/');
        if (slashIndex > -1) {
            roomPart = roomPart.substring(0, slashIndex);
        }
        return roomPart.trim().isEmpty() ? null : roomPart;
    }
}
