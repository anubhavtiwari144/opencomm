package com.opencomm.dto;

import com.opencomm.model.Room;
import com.opencomm.model.RoomStatus;

import java.time.Instant;

public class RoomResponse {
    private String roomId;
    private String hostName;
    private String guestName;
    private String pendingGuestName;
    private RoomStatus status;
    private Instant createdAt;

    public static RoomResponse from(Room room) {
        RoomResponse response = new RoomResponse();
        response.setRoomId(room.getRoomId());
        response.setHostName(room.getHost().getName());
        response.setGuestName(room.getGuest() == null ? null : room.getGuest().getName());
        response.setPendingGuestName(room.getPendingGuest() == null ? null : room.getPendingGuest().getName());
        response.setStatus(room.getStatus());
        response.setCreatedAt(room.getCreatedAt());
        return response;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
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

    public RoomStatus getStatus() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
