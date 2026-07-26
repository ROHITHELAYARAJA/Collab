package com.collabsync.service;

import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.dto.websocket.PresenceJoinMessage;
import com.collabsync.dto.websocket.PresenceLeaveMessage;
import com.collabsync.dto.websocket.CursorUpdateMessage;
import com.collabsync.dto.websocket.TypingIndicatorMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisPubSubService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishChatMessage(UUID roomId, ChatMessageWsMessage message) {
        String channel = "chat:" + roomId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chat message for room {}", roomId, e);
        }
    }

    public void publishTypingIndicator(UUID roomId, TypingIndicatorMessage message) {
        String channel = "chat:typing:" + roomId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize typing indicator for room {}", roomId, e);
        }
    }

    public void publishDocOp(UUID documentId, DocOpMessage message) {
        String channel = "doc:ops:" + documentId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize doc op for document {}", documentId, e);
        }
    }

    public void publishPresenceJoin(UUID documentId, PresenceJoinMessage message) {
        String channel = "doc:presence:" + documentId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize presence join for document {}", documentId, e);
        }
    }

    public void publishPresenceLeave(UUID documentId, PresenceLeaveMessage message) {
        String channel = "doc:presence:" + documentId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize presence leave for document {}", documentId, e);
        }
    }

    public void publishCursorUpdate(UUID documentId, CursorUpdateMessage message) {
        String channel = "doc:presence:" + documentId;
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cursor update for document {}", documentId, e);
        }
    }
}
