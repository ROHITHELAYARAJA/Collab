package com.collabsync.dto.workspace;

import com.collabsync.model.WorkspaceMember;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceMemberRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private WorkspaceMember.Role role;
}