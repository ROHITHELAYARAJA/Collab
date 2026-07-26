package com.collabsync.dto.chat;

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
public class ChatRoomResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID documentId;
    private String name;
    private Instant createdAt;
}