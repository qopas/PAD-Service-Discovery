package com.pad.discovery.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Custom exception for service-related errors.
 */
@Getter
public class ServiceException extends RuntimeException {
    
    private final HttpStatus status;
    
    public ServiceException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
    
    public ServiceException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
    
    public static ServiceException notFound(String message) {
        return new ServiceException(message, HttpStatus.NOT_FOUND);
    }
    
    public static ServiceException badRequest(String message) {
        return new ServiceException(message, HttpStatus.BAD_REQUEST);
    }
    
    public static ServiceException internalError(String message) {
        return new ServiceException(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

