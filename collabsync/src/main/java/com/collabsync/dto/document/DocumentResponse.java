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
public class DocumentResponse {

    private UUID id;
    private UUID workspaceId;
    private String title;
    private String content;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}