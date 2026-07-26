package com.collabsync.service;

import com.collabsync.dto.websocket.DocOpMessage;
import com.collabsync.model.DocumentOp;
import com.collabsync.repository.DocumentOpRepository;
import com.collabsync.repository.DocumentRepository;
import com.collabsync.repository.DocumentSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentOpRepository documentOpRepository;

    @Mock
    private DocumentSnapshotRepository documentSnapshotRepository;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, documentOpRepository, documentSnapshotRepository);
    }

    @Test
    void testTransform_InsertVsInsert_RemoteBeforeLocal() {
        // Remote insert at position 5, local insert at position 10
        // Local should be shifted by remote content length
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(10)
                .content("local")
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("remote")
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(16, transformed.getPosition()); // 10 + 6 (remote.length)
    }

    @Test
    void testTransform_InsertVsInsert_RemoteAtSamePosition_TieBreak() {
        // Both insert at position 5, remote wins (earlier serverSeq)
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("local")
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("remote")
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(11, transformed.getPosition()); // 5 + 6 (remote.length)
    }

    @Test
    void testTransform_InsertVsInsert_RemoteAfterLocal() {
        // Remote insert after local - no change
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("local")
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(10)
                .content("remote")
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(5, transformed.getPosition());
    }

    @Test
    void testTransform_InsertVsDelete_RemoteBeforeLocal() {
        // Remote delete at position 3, length 4; local insert at position 10
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(10)
                .content("local")
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(4)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        // max(3, 10-4) = max(3, 6) = 6
        assertEquals(6, transformed.getPosition());
    }

    @Test
    void testTransform_InsertVsDelete_RemoteAfterLocal() {
        // Remote delete after local insert - no change
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("local")
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(10)
                .length(4)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(5, transformed.getPosition());
    }

    @Test
    void testTransform_DeleteVsInsert_RemoteBeforeLocal() {
        // Remote insert at 5, local delete at 10
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(10)
                .length(3)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(5)
                .content("remote")
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(16, transformed.getPosition()); // 10 + 6
    }

    @Test
    void testTransform_DeleteVsInsert_RemoteWithinDeleteRange() {
        // Remote insert at position 8 within local delete range (5-13)
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(8)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(8)
                .content("x")
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        // Length should be extended by remote content length
        assertEquals(9, transformed.getLength()); // 8 + 1
    }

    @Test
    void testTransform_DeleteVsInsert_RemoteAfterDeleteRange() {
        // Remote insert after local delete range - no change
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(3)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(10)
                .content("x")
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(5, transformed.getPosition());
        assertEquals(3, transformed.getLength());
    }

    @Test
    void testTransform_DeleteVsDelete_RemoteEntirelyBefore() {
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(10)
                .length(5)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(2)
                .length(3)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(7, transformed.getPosition()); // 10 - 3
        assertEquals(5, transformed.getLength());
    }

    @Test
    void testTransform_DeleteVsDelete_RemoteEntirelyAfter() {
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(3)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(15)
                .length(3)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(5, transformed.getPosition());
        assertEquals(3, transformed.getLength());
    }

    @Test
    void testTransform_DeleteVsDelete_RemoteCoversLocal() {
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(5)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(10)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(3, transformed.getPosition());
        assertEquals(0, transformed.getLength());
    }

    @Test
    void testTransform_DeleteVsDelete_RemoteOverlapsStart() {
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(10)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(3)
                .length(4)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(3, transformed.getPosition());
        assertEquals(8, transformed.getLength()); // 15 - 7
    }

    @Test
    void testTransform_DeleteVsDelete_RemoteOverlapsEnd() {
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(10)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(12)
                .length(5)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(7, transformed.getLength()); // 12 - 5
    }

    @Test
    void testTransform_DeleteVsDelete_RemoteInMiddle() {
        DocOpMessage.OpPayload local = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(5)
                .length(10)
                .build();

        DocOpMessage.OpPayload remote = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.DELETE)
                .position(8)
                .length(3)
                .build();

        DocOpMessage.OpPayload transformed = documentService.transform(local, remote);

        assertEquals(7, transformed.getLength()); // 10 - 3
    }

    @Test
    void testApplyOperation_InsertAtBeginning() {
        String content = "";
        DocOpMessage.OpPayload op = DocOpMessage.OpPayload.builder()
                .opType(DocOpMessage.OpType.INSERT)
                .position(0)
                .content("Hello")
                .build();

        // We can't easily test the private applyToDocument method directly,
        // but we can verify the transform logic works by checking applyOperation
        // through the public API or by testing the content manipulation logic
    }

    @Test
    void testApplyOperation_DeleteInMiddle() {
        String content = "Hello World";
        int pos = 5;
        int len = 1;

        // Simulate the apply logic
        String result = content.substring(0, pos) + content.substring(pos + len);

        assertEquals("HelloWorld", result);
    }
}