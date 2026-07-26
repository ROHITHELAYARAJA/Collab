package com.collabsync.service;

import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.dto.websocket.PresenceJoinMessage;
import com.collabsync.dto.websocket.PresenceLeaveMessage;
import com.collabsync.dto.websocket.CursorUpdateMessage;
import com.collabsync.dto.websocket.TypingIndicatorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(pattern);
        String payload = new String(message.getBody());

        log.debug("Received message on channel: {}", channel);

        try {
            if (channel.startsWith("chat:")) {
                handleChatMessage(channel, payload);
            } else if (channel.startsWith("doc:ops:")) {
                handleDocOp(channel, payload);
            } else if (channel.startsWith("doc:presence:") || channel.startsWith("presence:")) {
                handlePresenceMessage(channel, payload);
            } else if (channel.startsWith("chat:typing:") || channel.startsWith("typing:")) {
                handleTypingIndicator(channel, payload);
            }
        } catch (Exception e) {
            log.error("Error processing message from channel {}", channel, e);
        }
    }

    private void handleChatMessage(String channel, String payload) {
        try {
            ChatMessageWsMessage message = objectMapper.readValue(payload, ChatMessageWsMessage.class);
            UUID roomId = message.getRoomId();
            messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
            log.debug("Rebroadcasted chat message to local clients for room {}", roomId);
        } catch (Exception e) {
            log.error("Failed to process chat message from channel {}", channel, e);
        }
    }

    private void handleDocOp(String channel, String payload) {
        try {
            DocOpMessage message = objectMapper.readValue(payload, DocOpMessage.class);
            UUID documentId = message.getDocumentId();
            messagingTemplate.convertAndSend("/topic/doc/" + documentId, message);
            log.debug("Rebroadcasted doc op to local clients for document {}", documentId);
        } catch (Exception e) {
            log.error("Failed to process doc op from channel {}", channel, e);
        }
    }

    private void handlePresenceMessage(String channel, String payload) {
        try {
            if (payload.contains("\"type\":\"PRESENCE_JOIN\"")) {
                PresenceJoinMessage message = objectMapper.readValue(payload, PresenceJoinMessage.class);
                messagingTemplate.convertAndSend("/topic/doc/" + message.getDocumentId() + "/presence", message);
            } else if (payload.contains("\"type\":\"PRESENCE_LEAVE\"")) {
                PresenceLeaveMessage message = objectMapper.readValue(payload, PresenceLeaveMessage.class);
                messagingTemplate.convertAndSend("/topic/doc/" + message.getDocumentId() + "/presence", message);
            } else if (payload.contains("\"type\":\"CURSOR_UPDATE\"")) {
                CursorUpdateMessage message = objectMapper.readValue(payload, CursorUpdateMessage.class);
                messagingTemplate.convertAndSend("/topic/doc/" + message.getDocumentId() + "/presence", message);
            }
            log.debug("Rebroadcasted presence message from channel {}", channel);
        } catch (Exception e) {
            log.error("Failed to process presence message from channel {}", channel, e);
        }
    }

    private void handleTypingIndicator(String channel, String payload) {
        try {
            TypingIndicatorMessage message = objectMapper.readValue(payload, TypingIndicatorMessage.class);
            UUID roomId = message.getRoomId();
            messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/typing", message);
            log.debug("Rebroadcasted typing indicator from channel {}", channel);
        } catch (Exception e) {
            log.error("Failed to process typing indicator from channel {}", channel, e);
        }
    }
}
