package com.opencomm.service;

import com.opencomm.dto.ChatMessageEvent;
import com.opencomm.model.Room;
import com.opencomm.model.RoomStatus;
import com.opencomm.model.UserSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private static final String ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, Room> roomsById = new ConcurrentHashMap<String, Room>();
    private final Map<String, String> roomIdByClientSession = new ConcurrentHashMap<String, String>();
    private final Map<String, String> clientSessionBySocketSession = new ConcurrentHashMap<String, String>();
    private final SecureRandom random = new SecureRandom();

    public Room createRoom(String hostSessionId, String hostName) {
        assertSessionIsFree(hostSessionId);

        String roomId = createRoomId();
        Room room = new Room(roomId, new UserSession(hostSessionId, cleanName(hostName)));
        roomsById.put(roomId, room);
        roomIdByClientSession.put(hostSessionId, roomId);
        return room;
    }

    public Room getRoom(String roomId) {
        Room room = roomsById.get(roomId);
        if (room == null) {
            throw new RoomException(HttpStatus.NOT_FOUND, "Room not found");
        }
        return room;
    }

    public Room getReadableRoom(String roomId, String sessionId) {
        Room room = getRoom(roomId);
        synchronized (room) {
            if (isReadRestricted(room) && !isParticipant(room, sessionId)) {
                throw new RoomException(HttpStatus.FORBIDDEN, "Room is private to host and guest");
            }
            return room;
        }
    }

    public Room requestJoin(String roomId, String guestSessionId, String guestName) {
        Room room = getRoom(roomId);
        synchronized (room) {
            if (room.getStatus() == RoomStatus.ENDED) {
                throw new RoomException(HttpStatus.GONE, "Room has ended");
            }
            if (isHost(room, guestSessionId)) {
                throw new RoomException(HttpStatus.BAD_REQUEST, "Host cannot join as guest");
            }
            if (room.getGuest() != null) {
                throw new RoomException(HttpStatus.CONFLICT, "Room already has a guest");
            }

            room.setPendingGuest(new UserSession(guestSessionId, cleanName(guestName)));
            room.setStatus(RoomStatus.PENDING);
            roomIdByClientSession.put(guestSessionId, roomId);
            return room;
        }
    }

    public Room acceptGuest(String roomId, String hostSessionId) {
        Room room = getRoom(roomId);
        synchronized (room) {
            assertHost(room, hostSessionId);
            if (room.getPendingGuest() == null) {
                throw new RoomException(HttpStatus.BAD_REQUEST, "No guest is waiting");
            }
            if (room.getGuest() != null) {
                throw new RoomException(HttpStatus.CONFLICT, "Room already has a guest");
            }

            room.setGuest(room.getPendingGuest());
            room.setPendingGuest(null);
            room.setStatus(RoomStatus.ACTIVE);
            return room;
        }
    }

    public Room rejectGuest(String roomId, String hostSessionId) {
        Room room = getRoom(roomId);
        synchronized (room) {
            assertHost(room, hostSessionId);
            if (room.getPendingGuest() != null) {
                roomIdByClientSession.remove(room.getPendingGuest().getSessionId());
            }
            room.setPendingGuest(null);
            room.setStatus(RoomStatus.WAITING);
            return room;
        }
    }

    public ChatMessageEvent createMessage(String roomId, String senderSessionId, String text) {
        Room room = getRoom(roomId);
        synchronized (room) {
            if (room.getStatus() != RoomStatus.ACTIVE) {
                throw new RoomException(HttpStatus.CONFLICT, "Chat is not active");
            }
            UserSession sender = findParticipant(room, senderSessionId);
            if (sender == null) {
                throw new RoomException(HttpStatus.FORBIDDEN, "Only room participants can send messages");
            }

            ChatMessageEvent event = new ChatMessageEvent();
            event.setRoomId(roomId);
            event.setSenderSessionId(sender.getSessionId());
            event.setSenderName(sender.getName());
            event.setText(text.trim());
            return event;
        }
    }

    public Room endRoom(String roomId, String hostSessionId) {
        Room room = getRoom(roomId);
        synchronized (room) {
            assertHost(room, hostSessionId);
            removeRoom(room);
            room.setStatus(RoomStatus.ENDED);
            return room;
        }
    }

    public DisconnectResult handleClientDisconnect(String clientSessionId) {
        String roomId = roomIdByClientSession.get(clientSessionId);
        if (roomId == null) {
            return DisconnectResult.none();
        }

        Room room = roomsById.get(roomId);
        if (room == null) {
            roomIdByClientSession.remove(clientSessionId);
            return DisconnectResult.none();
        }

        synchronized (room) {
            if (isHost(room, clientSessionId)) {
                removeRoom(room);
                room.setStatus(RoomStatus.ENDED);
                return DisconnectResult.deleted(roomId);
            }

            if (room.getPendingGuest() != null && clientSessionId.equals(room.getPendingGuest().getSessionId())) {
                roomIdByClientSession.remove(clientSessionId);
                room.setPendingGuest(null);
                room.setStatus(RoomStatus.WAITING);
                return DisconnectResult.updated(room, "Pending guest disconnected");
            }

            if (room.getGuest() != null && clientSessionId.equals(room.getGuest().getSessionId())) {
                roomIdByClientSession.remove(clientSessionId);
                room.setGuest(null);
                room.setStatus(RoomStatus.WAITING);
                return DisconnectResult.updated(room, "Guest disconnected");
            }
        }

        return DisconnectResult.none();
    }

    public void bindSocketSession(String socketSessionId, String clientSessionId) {
        if (socketSessionId != null && clientSessionId != null && !clientSessionId.trim().isEmpty()) {
            clientSessionBySocketSession.put(socketSessionId, clientSessionId);
        }
    }

    public String resolveClientSessionId(String socketSessionId) {
        if (socketSessionId == null) {
            return null;
        }
        return clientSessionBySocketSession.get(socketSessionId);
    }

    public String unbindSocketSession(String socketSessionId) {
        if (socketSessionId == null) {
            return null;
        }
        return clientSessionBySocketSession.remove(socketSessionId);
    }

    private void assertSessionIsFree(String sessionId) {
        if (roomIdByClientSession.containsKey(sessionId)) {
            throw new RoomException(HttpStatus.CONFLICT, "Session is already in a room");
        }
    }

    private void assertHost(Room room, String sessionId) {
        if (!isHost(room, sessionId)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Only the host can perform this action");
        }
    }

    private boolean isHost(Room room, String sessionId) {
        return room.getHost() != null && room.getHost().getSessionId().equals(sessionId);
    }

    private boolean isParticipant(Room room, String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        return findParticipant(room, sessionId) != null;
    }

    private boolean isReadRestricted(Room room) {
        return room.getGuest() != null;
    }

    private UserSession findParticipant(Room room, String sessionId) {
        if (room.getHost() != null && room.getHost().getSessionId().equals(sessionId)) {
            return room.getHost();
        }
        if (room.getGuest() != null && room.getGuest().getSessionId().equals(sessionId)) {
            return room.getGuest();
        }
        return null;
    }

    private void removeRoom(Room room) {
        roomsById.remove(room.getRoomId());
        if (room.getHost() != null) {
            roomIdByClientSession.remove(room.getHost().getSessionId());
        }
        if (room.getGuest() != null) {
            roomIdByClientSession.remove(room.getGuest().getSessionId());
        }
        if (room.getPendingGuest() != null) {
            roomIdByClientSession.remove(room.getPendingGuest().getSessionId());
        }
    }

    private String createRoomId() {
        String id;
        do {
            StringBuilder builder = new StringBuilder("OC-");
            for (int index = 0; index < 6; index++) {
                builder.append(ROOM_ALPHABET.charAt(random.nextInt(ROOM_ALPHABET.length())));
            }
            id = builder.toString();
        } while (roomsById.containsKey(id));
        return id;
    }

    private String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "Anonymous";
        }
        return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
    }

    public static class DisconnectResult {
        private final boolean changed;
        private final boolean deleted;
        private final String roomId;
        private final Room room;
        private final String message;

        private DisconnectResult(boolean changed, boolean deleted, String roomId, Room room, String message) {
            this.changed = changed;
            this.deleted = deleted;
            this.roomId = roomId;
            this.room = room;
            this.message = message;
        }

        public static DisconnectResult none() {
            return new DisconnectResult(false, false, null, null, null);
        }

        public static DisconnectResult deleted(String roomId) {
            return new DisconnectResult(true, true, roomId, null, "Host disconnected; room deleted");
        }

        public static DisconnectResult updated(Room room, String message) {
            return new DisconnectResult(true, false, room.getRoomId(), room, message);
        }

        public boolean isChanged() {
            return changed;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public String getRoomId() {
            return roomId;
        }

        public Room getRoom() {
            return room;
        }

        public String getMessage() {
            return message;
        }
    }
}
