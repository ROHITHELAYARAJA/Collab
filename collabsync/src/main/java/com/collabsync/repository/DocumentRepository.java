package com.collabsync.repository;

import com.collabsync.model.Document;
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
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("SELECT d FROM Document d WHERE d.workspaceId = :workspaceId ORDER BY d.updatedAt DESC")
    Page<Document> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.workspaceId = :workspaceId AND d.id = :id")
    Optional<Document> findByWorkspaceIdAndId(@Param("workspaceId") UUID workspaceId, @Param("id") UUID id);

    @Query("SELECT d FROM Document d WHERE d.createdBy = :userId ORDER BY d.updatedAt DESC")
    Page<Document> findByCreatedBy(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.workspaceId = :workspaceId")
    long countByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    boolean existsByWorkspaceIdAndTitle(UUID workspaceId, String title);
}