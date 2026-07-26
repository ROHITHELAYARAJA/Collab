package com.collabsync.repository;

import com.collabsync.model.ChatRoom;
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
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    @Query("SELECT r FROM ChatRoom r WHERE r.workspaceId = :workspaceId ORDER BY r.createdAt DESC")
    Page<ChatRoom> findByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("SELECT r FROM ChatRoom r WHERE r.workspaceId = :workspaceId AND r.documentId IS NULL")
    List<ChatRoom> findGeneralRoomsByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT r FROM ChatRoom r WHERE r.documentId = :documentId")
    Optional<ChatRoom> findByDocumentId(@Param("documentId") UUID documentId);

    @Query("SELECT r FROM ChatRoom r WHERE r.workspaceId = :workspaceId AND r.id = :id")
    Optional<ChatRoom> findByWorkspaceIdAndId(@Param("workspaceId") UUID workspaceId, @Param("id") UUID id);
}