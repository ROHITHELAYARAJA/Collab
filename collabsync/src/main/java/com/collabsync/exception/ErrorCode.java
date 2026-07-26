package com.collabsync.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Auth errors
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_003", "Invalid token"),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH_004", "User with this email already exists"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH_005", "User not found"),

    // Workspace errors
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE_001", "Workspace not found"),
    WORKSPACE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "WORKSPACE_002", "Access denied to workspace"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE_003", "Member not found"),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "WORKSPACE_004", "User is already a member"),
    CANNOT_REMOVE_OWNER(HttpStatus.BAD_REQUEST, "WORKSPACE_005", "Cannot remove workspace owner"),
    CANNOT_CHANGE_OWNER_ROLE(HttpStatus.BAD_REQUEST, "WORKSPACE_006", "Cannot change owner role"),
    ONLY_OWNER_CAN_ADD_OWNER(HttpStatus.FORBIDDEN, "WORKSPACE_007", "Only owners can add other owners"),

    // Document errors
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DOCUMENT_001", "Document not found"),
    DOCUMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DOCUMENT_002", "Access denied to document"),
    DOCUMENT_TITLE_EXISTS(HttpStatus.CONFLICT, "DOCUMENT_003", "Document with this title already exists in workspace"),
    VIEWER_CANNOT_EDIT(HttpStatus.FORBIDDEN, "DOCUMENT_004", "Viewers cannot edit documents"),
    ONLY_OWNER_CAN_DELETE(HttpStatus.FORBIDDEN, "DOCUMENT_005", "Only workspace owners can delete documents"),

    // Chat errors
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "Chat room not found"),
    CHAT_ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT_002", "Access denied to chat room"),
    DOCUMENT_NOT_IN_WORKSPACE(HttpStatus.BAD_REQUEST, "CHAT_003", "Document does not belong to workspace"),

    // Validation errors
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_001", "Validation failed"),

    // Internal errors
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_001", "Internal server error");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}