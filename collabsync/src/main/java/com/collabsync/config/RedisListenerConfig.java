package com.collabsync.config;

import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.CursorUpdateMessage;
import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.dto.websocket.PresenceJoinMessage;
import com.collabsync.dto.websocket.PresenceLeaveMessage;
import com.collabsync.dto.websocket.TypingIndicatorMessage;
import com.collabsync.service.RedisPubSubService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RedisListenerConfig {

    private final RedisPubSubService redisPubSubService;
    private final ObjectMapper objectMapper;

    @Autowired
    public void configureRedisListeners(RedisMessageListenerContainer container) {
        // Chat message listener
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String channel = new String(message.getChannel());
                String body = new String(message.getBody());

                UUID roomId = extractIdFromChannel(channel, "chat:");
                if (roomId != null) {
                    try {
                        ChatMessageWsMessage msg = objectMapper.readValue(body, ChatMessageWsMessage.class);
                        log.debug("Received chat message for room {}: {}", roomId, msg.getContent());
                    } catch (Exception e) {
                        log.error("Failed to process chat message on channel {}", channel, e);
                    }
                }
            }
        }, topic("chat:*"));

        // Document operation listener
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String channel = new String(message.getChannel());
                String body = new String(message.getBody());

                UUID documentId = extractIdFromChannel(channel, "doc:");
                if (documentId != null) {
                    try {
                        DocOpMessage msg = objectMapper.readValue(body, DocOpMessage.class);
                        log.debug("Received doc op for document {}: type={}", documentId, msg.getPayload() != null ? msg.getPayload().getOpType() : "null");
                    } catch (Exception e) {
                        log.error("Failed to process doc op on channel {}", channel, e);
                    }
                }
            }
        }, topic("doc:*"));

        // Presence listener
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String channel = new String(message.getChannel());
                String body = new String(message.getBody());

                UUID documentId = extractIdFromChannel(channel, "presence:");
                if (documentId != null) {
                    try {
                        var node = objectMapper.readTree(body);
                        String type = node.get("type").asText();
                        log.debug("Received presence message for document {}: type={}", documentId, type);
                    } catch (Exception e) {
                        log.error("Failed to process presence message on channel {}", channel, e);
                    }
                }
            }
        }, topic("presence:*"));

        // Typing indicator listener
        container.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String channel = new String(message.getChannel());
                String body = new String(message.getBody());

                UUID roomId = extractIdFromChannel(channel, "typing:");
                if (roomId != null) {
                    try {
                        TypingIndicatorMessage msg = objectMapper.readValue(body, TypingIndicatorMessage.class);
                        log.debug("Received typing indicator for room {}: typing={}", roomId, msg.isTyping());
                    } catch (Exception e) {
                        log.error("Failed to process typing indicator on channel {}", channel, e);
                    }
                }
            }
        }, topic("typing:*"));
    }

    private UUID extractIdFromChannel(String channel, String prefix) {
        Pattern pattern = Pattern.compile(Pattern.quote(prefix) + "([0-9a-fA-F-]+)");
        Matcher matcher = pattern.matcher(channel);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private org.springframework.data.redis.listener.Topic topic(String pattern) {
        return new org.springframework.data.redis.listener.PatternTopic(pattern);
    }
}
