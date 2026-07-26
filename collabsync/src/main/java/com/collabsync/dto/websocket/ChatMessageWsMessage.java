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
public class ChatMessageWsMessage {
    private String type;
    private UUID roomId;
    private UUID senderId;
    private String content;
    private Long clientSeq;
    private Instant timestamp;
}