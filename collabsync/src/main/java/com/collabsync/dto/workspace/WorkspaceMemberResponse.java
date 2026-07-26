package com.collabsync.dto.workspace;

import com.collabsync.model.WorkspaceMember;
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
public class WorkspaceMemberResponse {

    private UUID id;
    private UUID workspaceId;
    private UUID userId;
    private String email;
    private String displayName;
    private WorkspaceMember.Role role;
    private Instant createdAt;
}