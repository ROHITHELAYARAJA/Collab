package com.collabsync.service;

import com.collabsync.dto.kafka.ChatMessageEvent;
import com.collabsync.dto.kafka.DocOpEvent;
import com.collabsync.model.ChatMessage;
import com.collabsync.model.DocumentOp;
import com.collabsync.repository.ChatMessageRepository;
import com.collabsync.repository.DocumentOpRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final ChatMessageRepository chatMessageRepository;
    private final DocumentOpRepository documentOpRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "chat-messages", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeChatMessage(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        try {
            ChatMessageEvent event = objectMapper.readValue(payload, ChatMessageEvent.class);

            // Idempotency check - skip if message already exists
            if (chatMessageRepository.existsById(event.getMessageId())) {
                log.debug("Chat message {} already persisted, skipping", event.getMessageId());
                ack.acknowledge();
                return;
            }

            ChatMessage message = ChatMessage.builder()
                    .id(event.getMessageId())
                    .roomId(event.getRoomId())
                    .authorId(event.getAuthorId())
                    .content(event.getContent())
                    .build();

            chatMessageRepository.save(message);
            log.info("Persisted chat message {} to database", event.getMessageId());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process chat message from partition {} offset {}", partition, offset, e);
            // Don't acknowledge - let it retry
        }
    }

    @KafkaListener(topics = "doc-edits", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consumeDocOp(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack
    ) {
        try {
            DocOpEvent event = objectMapper.readValue(payload, DocOpEvent.class);

            // Idempotency check - skip if operation already exists
            if (event.getServerSeq() != null && documentOpRepository.existsByServerSeq(event.getServerSeq())) {
                log.debug("Doc op with serverSeq {} already persisted, skipping", event.getServerSeq());
                ack.acknowledge();
                return;
            }

            DocumentOp op = DocumentOp.builder()
                    .documentId(event.getDocumentId())
                    .opType(DocumentOp.OpType.valueOf(event.getOpType()))
                    .position(event.getPosition())
                    .content(event.getContent())
                    .clientSeq(event.getClientSeq())
                    .serverSeq(event.getServerSeq())
                    .authorId(event.getAuthorId())
                    .build();

            documentOpRepository.save(op);
            log.info("Persisted doc op {} to database", event.getServerSeq());

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process doc op from partition {} offset {}", partition, offset, e);
            // Don't acknowledge - let it retry
        }
    }
}
