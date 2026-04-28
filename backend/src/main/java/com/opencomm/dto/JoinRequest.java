package com.opencomm.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class JoinRequest {
    @NotBlank
    private String roomId;

    @NotBlank
    private String sessionId;

    @NotBlank
    @Size(max = 40)
    private String name;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
