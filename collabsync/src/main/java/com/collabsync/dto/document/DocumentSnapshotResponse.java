package com.collabsync.dto.document;

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
public class DocumentSnapshotResponse {

    private UUID id;
    private UUID documentId;
    private String content;
    private Long version;
    private Instant createdAt;
}