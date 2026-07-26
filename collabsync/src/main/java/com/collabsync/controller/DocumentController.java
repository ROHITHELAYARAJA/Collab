package com.collabsync.controller;

import com.collabsync.dto.document.DocumentResponse;
import com.collabsync.dto.document.DocumentSnapshotResponse;
import com.collabsync.model.Document;
import com.collabsync.model.DocumentSnapshot;
import com.collabsync.repository.DocumentRepository;
import com.collabsync.repository.DocumentSnapshotRepository;
import com.collabsync.service.WorkspaceMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentSnapshotRepository snapshotRepository;
    private final WorkspaceMemberService memberService;

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @RequestParam UUID workspaceId,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = getUserId(userDetails);
        if (!memberService.isMember(workspaceId, userId)) {
            return ResponseEntity.status(403).build();
        }

        Page<Document> documents = documentRepository.findByWorkspaceId(workspaceId, pageable);
        return ResponseEntity.ok(documents.map(this::toResponse));
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(
            @RequestParam UUID workspaceId,
            @RequestParam String title,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = getUserId(userDetails);
        if (!memberService.canEdit(workspaceId, userId)) {
            return ResponseEntity.status(403).build();
        }

        if (documentRepository.existsByWorkspaceIdAndTitle(workspaceId, title)) {
            return ResponseEntity.badRequest().build();
        }

        Document document = Document.builder()
                .workspaceId(workspaceId)
                .title(title)
                .content("")
                .createdBy(userId)
                .build();

        document = documentRepository.save(document);
        return ResponseEntity.ok(toResponse(document));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = getUserId(userDetails);
        Document document = documentRepository.findById(id).orElseThrow();
        if (!memberService.isMember(document.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(toResponse(document));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = getUserId(userDetails);
        Document document = documentRepository.findById(id).orElseThrow();
        if (!memberService.canEdit(document.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }
        documentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<Page<DocumentSnapshotResponse>> getHistory(
            @PathVariable UUID id,
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UUID userId = getUserId(userDetails);
        Document document = documentRepository.findById(id).orElseThrow();
        if (!memberService.isMember(document.getWorkspaceId(), userId)) {
            return ResponseEntity.status(403).build();
        }

        Page<DocumentSnapshot> snapshots = snapshotRepository.findByDocumentId(id, pageable);
        return ResponseEntity.ok(snapshots.map(this::toSnapshotResponse));
    }

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .workspaceId(doc.getWorkspaceId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .createdBy(doc.getCreatedBy())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .version(doc.getVersion())
                .build();
    }

    private DocumentSnapshotResponse toSnapshotResponse(DocumentSnapshot snapshot) {
        return DocumentSnapshotResponse.builder()
                .id(snapshot.getId())
                .documentId(snapshot.getDocumentId())
                .content(snapshot.getContent())
                .version(snapshot.getVersion())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }

    private UUID getUserId(UserDetails userDetails) {
        return UUID.fromString(userDetails.getUsername());
    }
}