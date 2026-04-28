package com.opencomm.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class SendMessageRequest {
    @NotBlank
    private String roomId;

    @NotBlank
    private String sessionId;

    @NotBlank
    @Size(max = 2000)
    private String text;

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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
