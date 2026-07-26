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
public class DocOpEvent {
    private UUID documentId;
    private UUID authorId;
    private String opType; // INSERT, DELETE
    private Integer position;
    private String content;
    private Integer length;
    private Long clientSeq;
    private Long serverSeq;
    private Instant timestamp;
    private long sequence; // For idempotency
}
