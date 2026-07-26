package com.collabsync.repository;

import com.collabsync.model.WorkspaceMember;
import com.collabsync.model.WorkspaceMember.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    @Query("SELECT m FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId")
    List<WorkspaceMember> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT m FROM WorkspaceMember m WHERE m.userId = :userId")
    List<WorkspaceMember> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT m FROM WorkspaceMember m WHERE m.workspaceId = :workspaceId AND m.role IN :roles")
    List<WorkspaceMember> findByWorkspaceIdAndRoleIn(@Param("workspaceId") UUID workspaceId, @Param("roles") List<Role> roles);

    boolean existsByWorkspaceIdAndUserId(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId);

    long countByWorkspaceIdAndRole(@Param("workspaceId") UUID workspaceId, @Param("role") Role role);
}