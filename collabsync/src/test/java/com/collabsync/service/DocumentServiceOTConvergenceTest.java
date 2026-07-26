package com.collabsync.service;

import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.model.DocumentOp;
import com.collabsync.repository.DocumentOpRepository;
import com.collabsync.repository.DocumentRepository;
import com.collabsync.repository.DocumentSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceOTConvergenceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentOpRepository documentOpRepository;

    @Mock
    private DocumentSnapshotRepository documentSnapshotRepository;

    private DocumentService documentService;

    @Test
    void testConvergence_ConcurrentInsertsAtSamePosition() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);

        // Two users insert at the same position concurrently
        DocOpMessage.OpPayload opA = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("A")
                .build();

        DocOpMessage.OpPayload opB = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("B")
                .build();

        // Transform A against B
        DocOpMessage.OpPayload transformedA = documentService.transform(opA, opB);
        // Transform B against A
        DocOpMessage.OpPayload transformedB = documentService.transform(opB, opA);

        // Apply both orders and verify they converge
        String initial = "Hello World";

        // Order 1: A then transformed B
        String result1 = applyOp(applyOp(initial, opA), transformedB);

        // Order 2: B then transformed A
        String result2 = applyOp(applyOp(initial, opB), transformedA);

        assertEquals(result1, result2, "Concurrent inserts at same position should converge");
    }

    @Test
    void testConvergence_InsertBeforeDelete() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);

        DocOpMessage.OpPayload insert = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(3)
                .content("X")
                .build();

        DocOpMessage.OpPayload delete = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(2)
                .build();

        DocOpMessage.OpPayload transformedInsert = documentService.transform(insert, delete);
        DocOpMessage.OpPayload transformedDelete = documentService.transform(delete, insert);

        String initial = "Hello World";

        String result1 = applyOp(applyOp(initial, insert), transformedDelete);
        String result2 = applyOp(applyOp(initial, delete), transformedInsert);

        assertEquals(result1, result2, "Insert before delete should converge");
    }

    @Test
    void testConvergence_DeleteBeforeInsert() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);

        DocOpMessage.OpPayload delete = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(2)
                .build();

        DocOpMessage.OpPayload insert = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("Y")
                .build();

        DocOpMessage.OpPayload transformedDelete = documentService.transform(delete, insert);
        DocOpMessage.OpPayload transformedInsert = documentService.transform(insert, delete);

        String initial = "Hello World";

        String result1 = applyOp(applyOp(initial, delete), transformedInsert);
        String result2 = applyOp(applyOp(initial, insert), transformedDelete);

        assertEquals(result1, result2, "Delete before insert should converge");
    }

    @Test
    void testConvergence_ConcurrentDeletesOverlapping() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);

        DocOpMessage.OpPayload deleteA = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(4)
                .build();

        DocOpMessage.OpPayload deleteB = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(3)
                .build();

        DocOpMessage.OpPayload transformedA = documentService.transform(deleteA, deleteB);
        DocOpMessage.OpPayload transformedB = documentService.transform(deleteB, deleteA);

        String initial = "Hello World";

        String result1 = applyOp(applyOp(initial, deleteA), transformedB);
        String result2 = applyOp(applyOp(initial, deleteB), transformedA);

        assertEquals(result1, result2, "Overlapping deletes should converge");
    }

    @Test
    void testConvergence_DeleteCoversInsert() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);

        DocOpMessage.OpPayload insert = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("INSERTED")
                .build();

        DocOpMessage.OpPayload delete = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(10)
                .build();

        DocOpMessage.OpPayload transformedInsert = documentService.transform(insert, delete);
        DocOpMessage.OpPayload transformedDelete = documentService.transform(delete, insert);

        String initial = "Hello World";

        String result1 = applyOp(applyOp(initial, insert), transformedDelete);
        String result2 = applyOp(applyOp(initial, delete), transformedInsert);

        assertEquals(result1, result2, "Delete covering insert should converge");
    }

    @Test
    void testConvergence_MultipleTransformations() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);

        // Three concurrent operations
        DocOpMessage.OpPayload op1 = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("1")
                .build();

        DocOpMessage.OpPayload op2 = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("2")
                .build();

        DocOpMessage.OpPayload op3 = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(2)
                .build();

        // Transform all against each other
        DocOpMessage.OpPayload t12 = documentService.transform(op1, op2);
        DocOpMessage.OpPayload t13 = documentService.transform(t12, op3);

        DocOpMessage.OpPayload t21 = documentService.transform(op2, op1);
        DocOpMessage.OpPayload t23 = documentService.transform(t21, op3);

        DocOpMessage.OpPayload t31 = documentService.transform(op3, op1);
        DocOpMessage.OpPayload t32 = documentService.transform(t31, op2);

        String initial = "Hello World";

        // Apply in different orders - all should converge
        String r1 = applyOp(applyOp(applyOp(initial, op1), t12), t13);
        String r2 = applyOp(applyOp(applyOp(initial, op2), t21), t23);
        String r3 = applyOp(applyOp(applyOp(initial, op3), t31), t32);

        assertEquals(r1, r2, "Order 1 vs 2 should converge");
        assertEquals(r2, r3, "Order 2 vs 3 should converge");
    }

    private String applyOp(String text, DocOpMessage.OpPayload op) {
        if (text == null) text = "";

        if (op.getOpType() == DocOpMessage.OpType.INSERT) {
            int pos = Math.min(op.getPosition(), text.length());
            return text.substring(0, pos) + (op.getContent() != null ? op.getContent() : "") + text.substring(pos);
        } else if (op.getOpType() == DocOpMessage.OpType.DELETE) {
            int pos = Math.min(op.getPosition(), text.length());
            int end = Math.min(pos + (op.getLength() != null ? op.getLength() : 0), text.length());
            return text.substring(0, pos) + text.substring(end);
        }
        return text;
    }
}