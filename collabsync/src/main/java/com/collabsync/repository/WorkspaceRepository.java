package com.collabsync.repository;

import com.collabsync.model.Workspace;
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
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    @Query("SELECT w FROM Workspace w JOIN WorkspaceMember m ON w.id = m.workspaceId WHERE m.userId = :userId")
    Page<Workspace> findByMemberUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT w FROM Workspace w WHERE w.ownerId = :userId")
    List<Workspace> findByOwnerId(@Param("userId") UUID userId);
}