package com.collabsync.repository;

import com.collabsync.model.ChatMessage;
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
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByRoomId(@Param("roomId") UUID roomId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id < :beforeId ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByRoomIdBeforeId(@Param("roomId") UUID roomId, @Param("beforeId") UUID beforeId, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id > :afterId ORDER BY m.createdAt ASC")
    Page<ChatMessage> findByRoomIdAfterId(@Param("roomId") UUID roomId, @Param("afterId") UUID afterId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.roomId = :roomId")
    long countByRoomId(@Param("roomId") UUID roomId);

    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId ORDER BY m.createdAt ASC")
    List<ChatMessage> findByRoomIdOrderByCreatedAtAsc(@Param("roomId") UUID roomId);
}