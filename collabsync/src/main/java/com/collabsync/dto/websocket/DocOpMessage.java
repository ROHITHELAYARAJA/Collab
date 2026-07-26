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
public class DocOpMessage {
    private String type;
    private UUID senderId;
    private Instant timestamp;
    private UUID documentId;
    private OpPayload payload;
    private Long clientSeq;
    private Long serverSeq;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpPayload {
        private OpType opType;
        private Integer position;
        private String content;
        private Integer length;
    }

    public enum OpType {
        INSERT, DELETE
    }
}