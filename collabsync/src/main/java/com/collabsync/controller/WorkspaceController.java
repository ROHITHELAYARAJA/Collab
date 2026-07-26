package com.collabsync.controller;

import com.collabsync.dto.workspace.WorkspaceMemberRequest;
import com.collabsync.dto.workspace.WorkspaceMemberResponse;
import com.collabsync.dto.workspace.WorkspaceRequest;
import com.collabsync.dto.workspace.WorkspaceResponse;
import com.collabsync.model.User;
import com.collabsync.model.Workspace;
import com.collabsync.model.WorkspaceMember;
import com.collabsync.repository.UserRepository;
import com.collabsync.repository.WorkspaceMemberRepository;
import com.collabsync.repository.WorkspaceRepository;
import com.collabsync.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<WorkspaceResponse>> getWorkspaces(Authentication authentication, Pageable pageable) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        Page<Workspace> workspaces = workspaceRepository.findByMemberUserId(userId, pageable);
        return ResponseEntity.ok(workspaces.map(this::toResponse));
    }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(@Valid @RequestBody WorkspaceRequest request, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .ownerId(userId)
                .build();

        workspace = workspaceRepository.save(workspace);

        // Add creator as owner
        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .workspaceId(workspace.getId())
                .userId(userId)
                .role(WorkspaceMember.Role.OWNER)
                .build();
        workspaceMemberRepository.save(ownerMember);

        return ResponseEntity.ok(toResponse(workspace));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(@PathVariable UUID workspaceId, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new RuntimeException("Workspace not found"));

        // Check membership
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toResponse(workspace));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberResponse>> getMembers(@PathVariable UUID workspaceId, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            return ResponseEntity.status(403).build();
        }

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        List<WorkspaceMemberResponse> response = members.stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<WorkspaceMemberResponse> addMember(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceMemberRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        // Check if requester is owner or editor
        WorkspaceMember requesterMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("Not a member of this workspace"));

        if (requesterMember.getRole() == WorkspaceMember.Role.VIEWER) {
            return ResponseEntity.status(403).build();
        }

        // Only owners can add owners
        if (request.getRole() == WorkspaceMember.Role.OWNER && requesterMember.getRole() != WorkspaceMember.Role.OWNER) {
            return ResponseEntity.status(403).build();
        }

        if (workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, request.getUserId())) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(request.getUserId())
                .role(request.getRole())
                .build();

        member = workspaceMemberRepository.save(member);

        return ResponseEntity.ok(toMemberResponse(member));
    }

    @PatchMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<WorkspaceMemberResponse> updateMemberRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @RequestParam WorkspaceMember.Role role,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        WorkspaceMember requesterMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("Not a member of this workspace"));

        if (requesterMember.getRole() != WorkspaceMember.Role.OWNER) {
            return ResponseEntity.status(403).build();
        }

        // Cannot change owner role
        if (role == WorkspaceMember.Role.OWNER) {
            return ResponseEntity.badRequest().build();
        }

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (!member.getWorkspaceId().equals(workspaceId)) {
            return ResponseEntity.badRequest().build();
        }

        member.setRole(role);
        member = workspaceMemberRepository.save(member);

        return ResponseEntity.ok(toMemberResponse(member));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        WorkspaceMember requesterMember = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new RuntimeException("Not a member of this workspace"));

        if (requesterMember.getRole() != WorkspaceMember.Role.OWNER) {
            return ResponseEntity.status(403).build();
        }

        WorkspaceMember member = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        if (!member.getWorkspaceId().equals(workspaceId)) {
            return ResponseEntity.badRequest().build();
        }

        // Cannot remove owner
        if (member.getRole() == WorkspaceMember.Role.OWNER) {
            return ResponseEntity.badRequest().build();
        }

        workspaceMemberRepository.delete(member);

        return ResponseEntity.noContent().build();
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .ownerId(workspace.getOwnerId())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMember member) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return WorkspaceMemberResponse.builder()
                .id(member.getId())
                .workspaceId(member.getWorkspaceId())
                .userId(member.getUserId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(member.getRole())
                .createdAt(member.getCreatedAt())
                .build();
    }
}