package com.collabsync.websocket;

import com.collabsync.dto.websocket.AckMessage;
import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.TypingIndicatorMessage;
import com.collabsync.dto.websocket.WebSocketMessage;
import com.collabsync.model.ChatMessage;
import com.collabsync.model.ChatRoom;
import com.collabsync.model.User;
import com.collabsync.repository.ChatMessageRepository;
import com.collabsync.repository.ChatRoomRepository;
import com.collabsync.repository.UserRepository;
import com.collabsync.security.JwtTokenProvider;
import com.collabsync.service.KafkaProducerService;
import com.collabsync.service.RedisPubSubService;
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
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisPubSubService redisPubSubService;
    private final KafkaProducerService kafkaProducerService;

    @MessageMapping("/chat/{roomId}/send")
    public void handleChatMessage(
            @DestinationVariable UUID roomId,
            @Payload ChatMessageWsMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = getUserId(headerAccessor);
        String email = getEmail(headerAccessor);

        // Verify room exists and user has access
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        if (!memberService.isMember(room.getWorkspaceId(), userId)) {
            throw new IllegalArgumentException("Access denied to chat room");
        }

        // Get user for display name
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Persist message
        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(roomId)
                .authorId(userId)
                .content(message.getContent())
                .build();
        chatMessage = chatMessageRepository.save(chatMessage);

        // Build enriched message
        ChatMessageWsMessage enrichedMessage = ChatMessageWsMessage.builder()
                .type("CHAT_MESSAGE")
                .senderId(userId)
                .timestamp(chatMessage.getCreatedAt())
                .roomId(roomId)
                .content(message.getContent())
                .build();

        // Send ACK to sender
        AckMessage ack = AckMessage.builder()
                .type("ACK")
                .roomId(roomId)
                .clientSeq(message.getClientSeq())
                .serverSeq((long) chatMessage.getId().hashCode())
                .success(true)
                .timestamp(Instant.now())
                .build();
        messagingTemplate.convertAndSendToUser(email, "/queue/acks", ack);

        // Publish to Redis for cross-instance fan-out
        redisPubSubService.publishChatMessage(roomId, enrichedMessage);

        // Send to Kafka for durable persistence
        kafkaProducerService.sendChatMessage(enrichedMessage);
    }

    @MessageMapping("/chat/{roomId}/typing")
    public void handleTypingIndicator(
            @DestinationVariable UUID roomId,
            @Payload TypingIndicatorMessage message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        UUID userId = getUserId(headerAccessor);

        // Verify access
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found"));

        if (!memberService.isMember(room.getWorkspaceId(), userId)) {
            throw new IllegalArgumentException("Access denied to chat room");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        TypingIndicatorMessage enrichedMessage = TypingIndicatorMessage.builder()
                .type("TYPING_INDICATOR")
                .senderId(userId)
                .timestamp(Instant.now())
                .roomId(roomId)
                .typing(message.isTyping())
                .build();

        // Publish to Redis for cross-instance fan-out
        redisPubSubService.publishTypingIndicator(roomId, enrichedMessage);
    }

    private UUID getUserId(SimpMessageHeaderAccessor accessor) {
        return (UUID) accessor.getSessionAttributes().get("userId");
    }

    private String getEmail(SimpMessageHeaderAccessor accessor) {
        return (String) accessor.getSessionAttributes().get("email");
    }
}
