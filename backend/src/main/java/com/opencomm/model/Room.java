package com.opencomm.model;

import java.time.Instant;

public class Room {
    private String roomId;
    private UserSession host;
    private UserSession guest;
    private UserSession pendingGuest;
    private RoomStatus status;
    private Instant createdAt;

    public Room() {
    }

    public Room(String roomId, UserSession host) {
        this.roomId = roomId;
        this.host = host;
        this.status = RoomStatus.WAITING;
        this.createdAt = Instant.now();
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public UserSession getHost() {
        return host;
    }

    public void setHost(UserSession host) {
        this.host = host;
    }

    public UserSession getGuest() {
        return guest;
    }

    public void setGuest(UserSession guest) {
        this.guest = guest;
    }

    public UserSession getPendingGuest() {
        return pendingGuest;
    }

    public void setPendingGuest(UserSession pendingGuest) {
        this.pendingGuest = pendingGuest;
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
