package com.collabsync.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceLeaveMessage {
    private String type;
    private UUID documentId;
    private UUID senderId;
    private Instant timestamp;
}