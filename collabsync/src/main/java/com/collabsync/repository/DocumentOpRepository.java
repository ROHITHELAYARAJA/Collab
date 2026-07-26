package com.collabsync.repository;

import com.collabsync.model.DocumentOp;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentOpRepository extends JpaRepository<DocumentOp, UUID> {

    @Query("SELECT o FROM DocumentOp o WHERE o.documentId = :documentId ORDER BY o.serverSeq ASC")
    Page<DocumentOp> findByDocumentId(@Param("documentId") UUID documentId, Pageable pageable);

    @Query("SELECT o FROM DocumentOp o WHERE o.documentId = :documentId AND o.serverSeq > :seq ORDER BY o.serverSeq ASC")
    List<DocumentOp> findByDocumentIdAndServerSeqGreaterThan(@Param("documentId") UUID documentId, @Param("seq") Long seq);

    @Query("SELECT MAX(o.serverSeq) FROM DocumentOp o WHERE o.documentId = :documentId")
    Long findMaxServerSeqByDocumentId(@Param("documentId") UUID documentId);

    boolean existsByServerSeq(Long serverSeq);

    @Query("SELECT o FROM DocumentOp o WHERE o.documentId = :documentId ORDER BY o.serverSeq DESC")
    List<DocumentOp> findRecentByDocumentId(@Param("documentId") UUID documentId, Pageable pageable);
}