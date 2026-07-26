package com.collabsync.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_ops", indexes = {
    @Index(name = "idx_document_ops_document_seq", columnList = "document_id, server_seq")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentOp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "op_type", nullable = false, length = 10)
    private OpType opType;

    @Column(nullable = false)
    private Integer position;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "client_seq", nullable = false)
    private Long clientSeq;

    @Column(name = "server_seq", nullable = false, unique = true)
    private Long serverSeq;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum OpType {
        INSERT, DELETE
    }
}