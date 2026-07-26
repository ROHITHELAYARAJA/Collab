package com.collabsync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PRESENCE_PREFIX = "presence:doc:";
    private static final String CURSOR_PREFIX = "cursor:doc:";
    private static final Duration TTL = Duration.ofSeconds(30);

    public void join(UUID documentId, UUID userId, String displayName) {
        String key = PRESENCE_PREFIX + documentId;
        String userKey = "user:" + userId;

        Map<String, String> userData = new HashMap<>();
        userData.put("userId", userId.toString());
        userData.put("displayName", displayName);
        userData.put("joinedAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().put(key, userKey, userData);
        redisTemplate.expire(key, TTL);
    }

    public void leave(UUID documentId, UUID userId) {
        String key = PRESENCE_PREFIX + documentId;
        String userKey = "user:" + userId;
        redisTemplate.opsForHash().delete(key, userKey);
    }

    public void updateCursor(UUID documentId, UUID userId, Integer cursorPosition, Integer selectionEnd) {
        String key = CURSOR_PREFIX + documentId;
        String userKey = "cursor:" + userId;

        Map<String, String> cursorData = new HashMap<>();
        cursorData.put("userId", userId.toString());
        cursorData.put("cursorPosition", String.valueOf(cursorPosition));
        if (selectionEnd != null) {
            cursorData.put("selectionEnd", String.valueOf(selectionEnd));
        }
        cursorData.put("updatedAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().put(key, userKey, cursorData);
        redisTemplate.expire(key, TTL);
    }

    public void removeCursor(UUID documentId, UUID userId) {
        String key = CURSOR_PREFIX + documentId;
        String userKey = "cursor:" + userId;
        redisTemplate.opsForHash().delete(key, userKey);
    }

    public List<PresenceInfo> getPresence(UUID documentId) {
        String key = PRESENCE_PREFIX + documentId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        List<PresenceInfo> result = new ArrayList<>();
        for (Object value : entries.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> userData = (Map<String, String>) value;
                result.add(PresenceInfo.builder()
                        .userId(UUID.fromString(userData.get("userId")))
                        .displayName(userData.get("displayName"))
                        .joinedAt(Long.parseLong(userData.get("joinedAt")))
                        .build());
            }
        }
        return result;
    }

    public Map<UUID, CursorInfo> getCursors(UUID documentId) {
        String key = CURSOR_PREFIX + documentId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        Map<UUID, CursorInfo> result = new HashMap<>();
        for (Object value : entries.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> cursorData = (Map<String, String>) value;
                UUID userId = UUID.fromString(cursorData.get("userId"));
                result.put(userId, CursorInfo.builder()
                        .userId(userId)
                        .cursorPosition(Integer.parseInt(cursorData.get("cursorPosition")))
                        .selectionEnd(cursorData.containsKey("selectionEnd") ?
                                Integer.parseInt(cursorData.get("selectionEnd")) : null)
                        .updatedAt(Long.parseLong(cursorData.get("updatedAt")))
                        .build());
            }
        }
        return result;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PresenceInfo {
        private UUID userId;
        private String displayName;
        private long joinedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CursorInfo {
        private UUID userId;
        private Integer cursorPosition;
        private Integer selectionEnd;
        private long updatedAt;
    }
}