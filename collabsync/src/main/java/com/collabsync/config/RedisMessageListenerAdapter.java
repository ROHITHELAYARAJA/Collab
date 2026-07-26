package com.collabsync.config;

import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.CursorUpdateMessage;
import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.dto.websocket.PresenceJoinMessage;
import com.collabsync.dto.websocket.PresenceLeaveMessage;
import com.collabsync.dto.websocket.TypingIndicatorMessage;
import com.collabsync.dto.websocket.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageListenerAdapter {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void onMessage(byte[] body, byte[] pattern) {
        String channel = new String(pattern);
        String messageBody = new String(body);

        try {
            if (channel.startsWith("chat:")) {
                UUID roomId = UUID.fromString(channel.substring(5));
                ChatMessageWsMessage msg = objectMapper.readValue(messageBody, ChatMessageWsMessage.class);
                messagingTemplate.convertAndSend("/topic/chat/" + roomId, msg);
            } else if (channel.startsWith("doc:")) {
                UUID docId = UUID.fromString(channel.substring(4));
                DocOpMessage msg = objectMapper.readValue(messageBody, DocOpMessage.class);
                messagingTemplate.convertAndSend("/topic/doc/" + docId, msg);
            } else if (channel.startsWith("presence:")) {
                UUID docId = UUID.fromString(channel.substring(9));

                WebSocketMessage baseMsg = objectMapper.readValue(messageBody, WebSocketMessage.class);

                if ("PRESENCE_JOIN".equals(baseMsg.getType())) {
                    PresenceJoinMessage msg = objectMapper.readValue(messageBody, PresenceJoinMessage.class);
                    messagingTemplate.convertAndSend("/topic/doc/" + docId + "/presence", msg);
                } else if ("PRESENCE_LEAVE".equals(baseMsg.getType())) {
                    PresenceLeaveMessage msg = objectMapper.readValue(messageBody, PresenceLeaveMessage.class);
                    messagingTemplate.convertAndSend("/topic/doc/" + docId + "/presence", msg);
                } else if ("CURSOR_UPDATE".equals(baseMsg.getType())) {
                    CursorUpdateMessage msg = objectMapper.readValue(messageBody, CursorUpdateMessage.class);
                    messagingTemplate.convertAndSend("/topic/doc/" + docId + "/presence", msg);
                }
            } else if (channel.startsWith("typing:")) {
                UUID roomId = UUID.fromString(channel.substring(7));
                TypingIndicatorMessage msg = objectMapper.readValue(messageBody, TypingIndicatorMessage.class);
                messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/typing", msg);
            }
        } catch (Exception e) {
            log.error("Failed to process Redis message from channel {}: {}", channel, e.getMessage(), e);
        }
    }
}
