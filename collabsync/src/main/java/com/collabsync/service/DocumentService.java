package com.collabsync.service;

import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.model.Document;
import com.collabsync.model.DocumentOp;
import com.collabsync.model.DocumentSnapshot;
import com.collabsync.repository.DocumentOpRepository;
import com.collabsync.repository.DocumentRepository;
import com.collabsync.repository.DocumentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentOpRepository documentOpRepository;
    private final DocumentSnapshotRepository documentSnapshotRepository;

    // In-memory queue for pending operations per document (for OT)
    // In production with multiple instances, this would be Redis-based
    private final ConcurrentHashMap<UUID, AtomicLong> serverSeqCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<PendingOp>> pendingOps = new ConcurrentHashMap<>();

    @Transactional
    public TransformedOp applyOperation(UUID documentId, DocOpMessage.OpPayload payload, UUID authorId, Long clientSeq) {
        // Get or initialize server sequence counter
        AtomicLong counter = serverSeqCounters.computeIfAbsent(documentId, k -> {
            Long maxSeq = documentOpRepository.findMaxServerSeqByDocumentId(documentId);
            return new AtomicLong(maxSeq != null ? maxSeq : 0);
        });

        long serverSeq = counter.incrementAndGet();

        // Get pending operations for this document
        var queue = pendingOps.computeIfAbsent(documentId, k -> new ConcurrentLinkedQueue<>());

        // Transform against concurrent pending operations
        DocOpMessage.OpPayload transformedPayload = payload;
        for (PendingOp pending : queue) {
            if (!pending.authorId.equals(authorId) && pending.clientSeq > clientSeq) {
                transformedPayload = transform(transformedPayload, pending.payload);
            }
        }

        // Create and persist document operation
        DocumentOp docOp = DocumentOp.builder()
                .documentId(documentId)
                .opType(DocumentOp.OpType.valueOf(transformedPayload.getOpType().name()))
                .position(transformedPayload.getPosition())
                .content(transformedPayload.getContent())
                .clientSeq(clientSeq)
                .serverSeq(serverSeq)
                .authorId(authorId)
                .build();

        documentOpRepository.save(docOp);

        // Apply to document content
        applyToDocument(documentId, transformedPayload);

        // Add to pending queue
        queue.add(new PendingOp(authorId, clientSeq, serverSeq, transformedPayload));

        // Clean up acknowledged operations
        cleanupPendingOps(documentId, clientSeq);

        // Create snapshot periodically (every 100 operations)
        if (serverSeq % 100 == 0) {
            createSnapshot(documentId, serverSeq);
        }

        return new TransformedOp(serverSeq, transformedPayload);
    }

    private void applyToDocument(UUID documentId, DocOpMessage.OpPayload payload) {
        Document document = documentRepository.findById(documentId).orElseThrow();
        String content = document.getContent();
        if (content == null) content = "";

        if (payload.getOpType() == DocOpMessage.OpType.INSERT) {
            int pos = Math.min(payload.getPosition(), content.length());
            content = content.substring(0, pos) + payload.getContent() + content.substring(pos);
        } else if (payload.getOpType() == DocOpMessage.OpType.DELETE) {
            int pos = Math.min(payload.getPosition(), content.length());
            int end = Math.min(pos + payload.getLength(), content.length());
            content = content.substring(0, pos) + content.substring(end);
        }

        document.setContent(content);
        documentRepository.save(document);
    }

    DocOpMessage.OpPayload transform(DocOpMessage.OpPayload op1, DocOpMessage.OpPayload op2) {
        // Transform op1 against op2 (op2 happened first)
        DocOpMessage.OpPayload result = DocOpMessage.OpPayload.builder()
                .opType(op1.getOpType())
                .position(op1.getPosition())
                .content(op1.getContent())
                .length(op1.getLength())
                .build();

        if (op1.getOpType() == DocOpMessage.OpType.INSERT && op2.getOpType() == DocOpMessage.OpType.INSERT) {
            // Insert vs Insert
            if (op2.getPosition() <= op1.getPosition()) {
                result.setPosition(op1.getPosition() + op2.getContent().length());
            } else if (op2.getPosition() == op1.getPosition()) {
                // Tie-break: op2 wins (earlier serverSeq)
                result.setPosition(op1.getPosition() + op2.getContent().length());
            }
        } else if (op1.getOpType() == DocOpMessage.OpType.INSERT && op2.getOpType() == DocOpMessage.OpType.DELETE) {
            // Insert vs Delete
            if (op2.getPosition() <= op1.getPosition()) {
                result.setPosition(Math.max(op2.getPosition(), op1.getPosition() - op2.getLength()));
            }
        } else if (op1.getOpType() == DocOpMessage.OpType.DELETE && op2.getOpType() == DocOpMessage.OpType.INSERT) {
            // Delete vs Insert
            if (op2.getPosition() <= op1.getPosition()) {
                result.setPosition(op1.getPosition() + op2.getContent().length());
            } else if (op2.getPosition() < op1.getPosition() + op1.getLength()) {
                result.setLength(op1.getLength() + op2.getContent().length());
            }
        } else if (op1.getOpType() == DocOpMessage.OpType.DELETE && op2.getOpType() == DocOpMessage.OpType.DELETE) {
            // Delete vs Delete
            int op1End = op1.getPosition() + op1.getLength();
            int op2End = op2.getPosition() + op2.getLength();

            if (op2End <= op1.getPosition()) {
                // op2 entirely before op1
                result.setPosition(op1.getPosition() - op2.getLength());
            } else if (op2.getPosition() >= op1End) {
                // op2 entirely after op1 - no change needed
            } else if (op2.getPosition() <= op1.getPosition() && op2End >= op1End) {
                // op2 completely covers op1
                result.setPosition(op2.getPosition());
                result.setLength(0);
            } else if (op2.getPosition() <= op1.getPosition()) {
                // op2 overlaps start of op1
                result.setPosition(op2.getPosition());
                result.setLength(op1End - op2End);
            } else if (op2End >= op1End) {
                // op2 overlaps end of op1
                result.setLength(op2.getPosition() - op1.getPosition());
            } else {
                // op2 in middle of op1 - split (simplified: just reduce length)
                result.setLength(op1.getLength() - op2.getLength());
            }
        }

        return result;
    }

    private void cleanupPendingOps(UUID documentId, Long acknowledgedClientSeq) {
        var queue = pendingOps.get(documentId);
        if (queue != null) {
            queue.removeIf(op -> op.clientSeq <= acknowledgedClientSeq);
        }
    }

    @Transactional
    public void createSnapshot(UUID documentId, long serverSeq) {
        Document document = documentRepository.findById(documentId).orElseThrow();
        long version = serverSeq / 100 + 1;

        DocumentSnapshot snapshot = DocumentSnapshot.builder()
                .documentId(documentId)
                .content(document.getContent())
                .version(version)
                .build();

        documentSnapshotRepository.save(snapshot);
        log.debug("Created snapshot for document {} at version {}", documentId, version);
    }

    public Document getDocumentState(UUID documentId) {
        return documentRepository.findById(documentId).orElseThrow();
    }

    public String getDocumentContent(UUID documentId) {
        return documentRepository.findById(documentId)
                .map(Document::getContent)
                .orElse("");
    }

    private static class PendingOp {
        final UUID authorId;
        final Long clientSeq;
        final Long serverSeq;
        final DocOpMessage.OpPayload payload;

        PendingOp(UUID authorId, Long clientSeq, Long serverSeq, DocOpMessage.OpPayload payload) {
            this.authorId = authorId;
            this.clientSeq = clientSeq;
            this.serverSeq = serverSeq;
            this.payload = payload;
        }
    }

    public static class TransformedOp {
        private final Long serverSeq;
        private final DocOpMessage.OpPayload payload;

        public TransformedOp(Long serverSeq, DocOpMessage.OpPayload payload) {
            this.serverSeq = serverSeq;
            this.payload = payload;
        }

        public Long getServerSeq() { return serverSeq; }
        public DocOpMessage.OpPayload getPayload() { return payload; }
    }
}