package com.collabsync.dto.kafka;

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
public class ChatMessageEvent {
    private UUID messageId;
    private UUID roomId;
    private UUID authorId;
    private String content;
    private Instant createdAt;
    private long sequence;
}
