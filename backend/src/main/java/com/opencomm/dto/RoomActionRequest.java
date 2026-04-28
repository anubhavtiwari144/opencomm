package com.opencomm.dto;

import javax.validation.constraints.NotBlank;

public class RoomActionRequest {
    @NotBlank
    private String roomId;

    @NotBlank
    private String sessionId;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
