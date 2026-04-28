package com.opencomm.dto;

import com.opencomm.model.Room;
import com.opencomm.model.RoomStatus;

public class StatusEvent {
    private String type;
    private String roomId;
    private RoomStatus status;
    private String hostName;
    private String guestName;
    private String pendingGuestName;
    private String message;

    public static StatusEvent of(String type, Room room, String message) {
        StatusEvent event = new StatusEvent();
        event.setType(type);
        event.setRoomId(room.getRoomId());
        event.setStatus(room.getStatus());
        event.setHostName(room.getHost().getName());
        event.setGuestName(room.getGuest() == null ? null : room.getGuest().getName());
        event.setPendingGuestName(room.getPendingGuest() == null ? null : room.getPendingGuest().getName());
        event.setMessage(message);
        return event;
    }

    public static StatusEvent deleted(String roomId, String message) {
        StatusEvent event = new StatusEvent();
        event.setType("ROOM_DELETED");
        event.setRoomId(roomId);
        event.setStatus(RoomStatus.ENDED);
        event.setMessage(message);
        return event;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public String getPendingGuestName() {
        return pendingGuestName;
    }

    public void setPendingGuestName(String pendingGuestName) {
        this.pendingGuestName = pendingGuestName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
