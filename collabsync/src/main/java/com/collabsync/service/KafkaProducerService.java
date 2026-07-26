package com.collabsync.service;

import com.collabsync.dto.websocket.ChatMessageWsMessage;
import com.collabsync.dto.websocket.DocOpMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendChatMessage(ChatMessageWsMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            // Use roomId as key for partitioning
            String key = message.getRoomId() != null ? message.getRoomId().toString() : "";
            kafkaTemplate.send("chat-messages", key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send chat message to Kafka: {}", ex.getMessage(), ex);
                        } else {
                            log.debug("Sent chat message to Kafka topic chat-messages partition {} offset {}",
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize chat message for Kafka: {}", e.getMessage(), e);
        }
    }

    public void sendDocEdit(DocOpMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            // Use documentId as key for partitioning
            String key = message.getDocumentId() != null ? message.getDocumentId().toString() : "";
            kafkaTemplate.send("doc-edits", key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send doc edit to Kafka: {}", ex.getMessage(), ex);
                        } else {
                            log.debug("Sent doc edit to Kafka topic doc-edits partition {} offset {}",
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize doc edit for Kafka: {}", e.getMessage(), e);
        }
    }
}
