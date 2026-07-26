package com.collabsync.controller;

import com.collabsync.dto.chat.ChatMessageResponse;
import com.collabsync.dto.chat.ChatRoomRequest;
import com.collabsync.dto.chat.ChatRoomResponse;
import com.collabsync.model.ChatMessage;
import com.collabsync.model.ChatRoom;
import com.collabsync.model.WorkspaceMember;
import com.collabsync.repository.ChatMessageRepository;
import com.collabsync.repository.ChatRoomRepository;
import com.collabsync.repository.WorkspaceMemberRepository;
import com.collabsync.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @GetMapping("/rooms")
    public ResponseEntity<Page<ChatRoomResponse>> getChatRooms(
            @RequestParam UUID workspaceId,
            Authentication authentication,
            Pageable pageable
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            return ResponseEntity.status(403).build();
        }

        Page<ChatRoom> rooms = chatRoomRepository.findByWorkspaceId(workspaceId, pageable);
        return ResponseEntity.ok(rooms.map(this::toRoomResponse));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createChatRoom(
            @Valid @RequestBody ChatRoomRequest request,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(request.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }

        ChatRoom room = ChatRoom.builder()
                .workspaceId(request.getWorkspaceId())
                .documentId(request.getDocumentId())
                .name(request.getName())
                .build();

        room = chatRoomRepository.save(room);
        return ResponseEntity.ok(toRoomResponse(room));
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomResponse> getChatRoom(
            @PathVariable UUID roomId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(room.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toRoomResponse(room));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @RequestParam(required = false) UUID before,
            @RequestParam(required = false) UUID after,
            Authentication authentication,
            Pageable pageable
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(room.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }

        Page<ChatMessage> messages;
        if (before != null) {
            messages = chatMessageRepository.findByRoomIdBeforeId(roomId, before, pageable);
        } else if (after != null) {
            messages = chatMessageRepository.findByRoomIdAfterId(roomId, after, pageable);
        } else {
            messages = chatMessageRepository.findByRoomId(roomId, pageable);
        }

        return ResponseEntity.ok(messages.map(this::toMessageResponse));
    }

    @GetMapping("/rooms/document/{documentId}")
    public ResponseEntity<ChatRoomResponse> getDocumentChatRoom(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = userDetails.getUserId();

        ChatRoom room = chatRoomRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("Document chat room not found"));

        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserId(room.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toRoomResponse(room));
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        return ChatRoomResponse.builder()
                .id(room.getId())
                .workspaceId(room.getWorkspaceId())
                .documentId(room.getDocumentId())
                .name(room.getName())
                .createdAt(room.getCreatedAt())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .authorId(message.getAuthorId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}