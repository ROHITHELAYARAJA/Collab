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
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

// @Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            // Determine message type from channel and deserialize appropriately
            if (channel.startsWith("chat:")) {
                UUID roomId = UUID.fromString(channel.substring(5));
                ChatMessageWsMessage msg = objectMapper.readValue(body, ChatMessageWsMessage.class);
                messagingTemplate.convertAndSend("/topic/chat/" + roomId, msg);
            } else if (channel.startsWith("doc:")) {
                UUID docId = UUID.fromString(channel.substring(4));
                DocOpMessage msg = objectMapper.readValue(body, DocOpMessage.class);
                messagingTemplate.convertAndSend("/topic/doc/" + docId, msg);
            } else if (channel.startsWith("presence:")) {
                UUID docId = UUID.fromString(channel.substring(9));
                
                // Try to determine the message type by checking the JSON
                WebSocketMessage baseMsg = objectMapper.readValue(body, WebSocketMessage.class);
                
                if ("PRESENCE_JOIN".equals(baseMsg.getType())) {
                    PresenceJoinMessage msg = objectMapper.readValue(body, PresenceJoinMessage.class);
                    messagingTemplate.convertAndSend("/topic/doc/" + docId + "/presence", msg);
                } else if ("PRESENCE_LEAVE".equals(baseMsg.getType())) {
                    PresenceLeaveMessage msg = objectMapper.readValue(body, PresenceLeaveMessage.class);
                    messagingTemplate.convertAndSend("/topic/doc/" + docId + "/presence", msg);
                } else if ("CURSOR_UPDATE".equals(baseMsg.getType())) {
                    CursorUpdateMessage msg = objectMapper.readValue(body, CursorUpdateMessage.class);
                    messagingTemplate.convertAndSend("/topic/doc/" + docId + "/presence", msg);
                }
            } else if (channel.startsWith("typing:")) {
                UUID roomId = UUID.fromString(channel.substring(7));
                TypingIndicatorMessage msg = objectMapper.readValue(body, TypingIndicatorMessage.class);
                messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/typing", msg);
            }
        } catch (Exception e) {
            log.error("Failed to process Redis message from channel {}: {}", channel, e.getMessage(), e);
        }
    }
}
