package com.collabsync.websocket;

import com.collabsync.dto.websocket.AckMessage;
import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.CursorUpdateMessage;
import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.dto.websocket.ErrorMessage;
import com.collabsync.dto.websocket.PresenceJoinMessage;
import com.collabsync.dto.websocket.PresenceLeaveMessage;
import com.collabsync.dto.websocket.TypingIndicatorMessage;
import com.collabsync.model.ChatMessage;
import com.collabsync.model.ChatRoom;
import com.collabsync.model.Document;
import com.collabsync.model.DocumentOp;
import com.collabsync.model.User;
import com.collabsync.model.WorkspaceMember;
import com.collabsync.repository.ChatMessageRepository;
import com.collabsync.repository.ChatRoomRepository;
import com.collabsync.repository.DocumentOpRepository;
import com.collabsync.repository.DocumentRepository;
import com.collabsync.repository.UserRepository;
import com.collabsync.repository.WorkspaceMemberRepository;
import com.collabsync.security.JwtTokenProvider;
import com.collabsync.service.DocumentService;
import com.collabsync.service.PresenceService;
import com.collabsync.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DocumentWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final DocumentRepository documentRepository;
    private final DocumentOpRepository documentOpRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final PresenceService presenceService;
    private final WorkspaceMemberService memberService;

    @MessageMapping("/doc/{documentId}/edit")
    public void handleDocEdit(
            @DestinationVariable UUID documentId,
            @Payload DocOpMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = getUserId(headerAccessor);
        if (userId == null) {
            sendError(headerAccessor, "UNAUTHORIZED", "User not authenticated");
            return;
        }

        // Verify document access
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            sendError(headerAccessor, "DOCUMENT_NOT_FOUND", "Document not found");
            return;
        }

        if (!memberService.canEdit(document.getWorkspaceId(), userId)) {
            sendError(headerAccessor, "FORBIDDEN", "Insufficient permissions to edit");
            return;
        }

        // Process through OT service
        DocumentService.TransformedOp transformed = documentService.applyOperation(
                documentId,
                message.getPayload(),
                userId,
                message.getClientSeq()
        );

        // Send ACK to sender
        AckMessage ack = AckMessage.builder()
                .type("ACK")
                .documentId(documentId)
                .clientSeq(message.getClientSeq())
                .serverSeq(transformed.getServerSeq())
                .success(true)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(
                getEmail(headerAccessor),
                "/queue/acks",
                ack
        );

        // Broadcast to all collaborators
        DocOpMessage broadcast = DocOpMessage.builder()
                .type("DOC_OP")
                .documentId(documentId)
                .payload(transformed.getPayload())
                .senderId(userId)
                .serverSeq(transformed.getServerSeq())
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/doc/" + documentId, broadcast);
    }

    @MessageMapping("/doc/{documentId}/cursor")
    public void handleCursorUpdate(
            @DestinationVariable UUID documentId,
            @Payload CursorUpdateMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = getUserId(headerAccessor);
        if (userId == null) return;

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        // Verify access
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || !memberService.isMember(document.getWorkspaceId(), userId)) {
            return;
        }

        // Update presence
        presenceService.updateCursor(documentId, userId, message.getCursorPosition(), message.getSelectionEnd());

        // Broadcast to others
        CursorUpdateMessage broadcast = CursorUpdateMessage.builder()
                .type("CURSOR_UPDATE")
                .documentId(documentId)
                .senderId(userId)
                .cursorPosition(message.getCursorPosition())
                .selectionEnd(message.getSelectionEnd())
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/doc/" + documentId + "/presence", broadcast);
    }

    @MessageMapping("/doc/{documentId}/join")
    public void handlePresenceJoin(
            @DestinationVariable UUID documentId,
            @Payload PresenceJoinMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = getUserId(headerAccessor);
        if (userId == null) return;

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null || !memberService.isMember(document.getWorkspaceId(), userId)) {
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        presenceService.join(documentId, userId, user.getDisplayName());

        PresenceJoinMessage broadcast = PresenceJoinMessage.builder()
                .type("PRESENCE_JOIN")
                .documentId(documentId)
                .senderId(userId)
                .displayName(user.getDisplayName())
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/doc/" + documentId + "/presence", broadcast);
    }

    @MessageMapping("/doc/{documentId}/leave")
    public void handlePresenceLeave(
            @DestinationVariable UUID documentId,
            @Payload PresenceLeaveMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = getUserId(headerAccessor);
        if (userId == null) return;

        presenceService.leave(documentId, userId);
        presenceService.removeCursor(documentId, userId);

        PresenceLeaveMessage broadcast = PresenceLeaveMessage.builder()
                .type("PRESENCE_LEAVE")
                .documentId(documentId)
                .senderId(userId)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSend("/topic/doc/" + documentId + "/presence", broadcast);
    }

    private UUID getUserId(SimpMessageHeaderAccessor accessor) {
        return (UUID) accessor.getSessionAttributes().get("userId");
    }

    private String getEmail(SimpMessageHeaderAccessor accessor) {
        return (String) accessor.getSessionAttributes().get("email");
    }

    private void sendError(SimpMessageHeaderAccessor accessor, String code, String message) {
        ErrorMessage error = ErrorMessage.builder()
                .type("ERROR")
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .build();
        String email = getEmail(accessor);
        if (email != null) {
            messagingTemplate.convertAndSendToUser(email, "/queue/errors", error);
        }
    }
}