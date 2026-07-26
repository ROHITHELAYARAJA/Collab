package com.collabsync.service;

import com.collabsync.model.WorkspaceMember;
import com.collabsync.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceMemberService {

    private final WorkspaceMemberRepository memberRepository;

    public boolean isMember(UUID workspaceId, UUID userId) {
        return memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }

    public boolean canEdit(UUID workspaceId, UUID userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(m -> m.getRole() == WorkspaceMember.Role.OWNER || m.getRole() == WorkspaceMember.Role.EDITOR)
                .orElse(false);
    }

    public WorkspaceMember.Role getRole(UUID workspaceId, UUID userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::getRole)
                .orElse(null);
    }
}