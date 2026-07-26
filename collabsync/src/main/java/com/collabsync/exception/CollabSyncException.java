package com.collabsync.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CollabSyncException extends RuntimeException {

    private final ErrorCode errorCode;

    public CollabSyncException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CollabSyncException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public HttpStatus getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    public String getErrorCode() {
        return errorCode.getCode();
    }
}