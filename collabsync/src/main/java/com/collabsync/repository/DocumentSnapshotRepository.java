package com.collabsync.repository;

import com.collabsync.model.DocumentSnapshot;
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
public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshot, UUID> {

    @Query("SELECT s FROM DocumentSnapshot s WHERE s.documentId = :documentId ORDER BY s.version DESC")
    Page<DocumentSnapshot> findByDocumentId(@Param("documentId") UUID documentId, Pageable pageable);

    @Query("SELECT s FROM DocumentSnapshot s WHERE s.documentId = :documentId AND s.version = :version")
    Optional<DocumentSnapshot> findByDocumentIdAndVersion(@Param("documentId") UUID documentId, @Param("version") Long version);

    @Query("SELECT MAX(s.version) FROM DocumentSnapshot s WHERE s.documentId = :documentId")
    Long findMaxVersionByDocumentId(@Param("documentId") UUID documentId);
}