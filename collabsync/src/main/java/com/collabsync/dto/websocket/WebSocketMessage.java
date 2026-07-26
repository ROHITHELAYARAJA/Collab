package com.collabsync.dto.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DocOpMessage.class, name = "DOC_OP"),
    @JsonSubTypes.Type(value = CursorUpdateMessage.class, name = "CURSOR_UPDATE"),
    @JsonSubTypes.Type(value = PresenceJoinMessage.class, name = "PRESENCE_JOIN"),
    @JsonSubTypes.Type(value = PresenceLeaveMessage.class, name = "PRESENCE_LEAVE"),
    @JsonSubTypes.Type(value = ChatMessageWsMessage.class, name = "CHAT_MESSAGE"),
    @JsonSubTypes.Type(value = TypingIndicatorMessage.class, name = "TYPING_INDICATOR"),
    @JsonSubTypes.Type(value = AckMessage.class, name = "ACK"),
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")
})
public abstract class WebSocketMessage {

    private String type;
    private UUID senderId;
    private Instant timestamp;
    private Long clientSeq;
}