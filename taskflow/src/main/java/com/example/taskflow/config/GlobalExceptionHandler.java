package com.example.taskflow.config;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.taskflow.exception.TokenRefreshException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private Map<String, Object> createErrorResponse(HttpStatus status, String error, String message, String code, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", status.value());
        response.put("error", error);
        response.put("message", message);
        response.put("code", code);
        response.put("path", request != null ? request.getRequestURI() : "Unknown");
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            response.put("correlationId", correlationId);
        }
        return response;
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<Object> handleTokenRefreshException(TokenRefreshException ex, HttpServletRequest request) {
        HttpStatus status = ex.getErrorCode() == TokenRefreshException.ErrorCode.REUSE_DETECTED ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        return new ResponseEntity<>(createErrorResponse(status, status.getReasonPhrase(), ex.getMessage(), ex.getErrorCode().name(), request), status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ResponseEntity<>(createErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, "VALIDATION_FAILED", request), HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "CONSTRAINT_VIOLATION", request), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.CONFLICT, "Conflict", "Database constraint violated", "DATA_INTEGRITY_VIOLATION", request), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLockingFailureException(org.springframework.dao.OptimisticLockingFailureException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.CONFLICT, "Conflict", "The resource was modified by another transaction. Please refresh and try again.", "OPTIMISTIC_LOCK_CONFLICT", request), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "ACCESS_DENIED", request), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), "AUTHENTICATION_FAILED", request), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(com.example.taskflow.exception.InvalidCredentialsException.class)
    public ResponseEntity<Object> handleInvalidCredentialsException(com.example.taskflow.exception.InvalidCredentialsException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), "INVALID_CREDENTIALS", request), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(com.example.taskflow.exception.UsernameConflictException.class)
    public ResponseEntity<Object> handleUsernameConflictException(com.example.taskflow.exception.UsernameConflictException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "USERNAME_CONFLICT", request), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.example.taskflow.exception.TaskNotFoundException.class)
    public ResponseEntity<Object> handleTaskNotFoundException(com.example.taskflow.exception.TaskNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "TASK_NOT_FOUND", request), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.example.taskflow.exception.UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFoundException(com.example.taskflow.exception.UserNotFoundException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "USER_NOT_FOUND", request), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(com.example.taskflow.exception.UnauthorizedActionException.class)
    public ResponseEntity<Object> handleUnauthorizedActionException(com.example.taskflow.exception.UnauthorizedActionException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "UNAUTHORIZED_ACTION", request), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(com.example.taskflow.exception.OrganizationSuspendedException.class)
    public ResponseEntity<Object> handleOrganizationSuspendedException(com.example.taskflow.exception.OrganizationSuspendedException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "ORGANIZATION_SUSPENDED", request), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "INVALID_STATE", request), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        return new ResponseEntity<>(createErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "BAD_REQUEST", request), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("Unhandled RuntimeException", ex);
        return new ResponseEntity<>(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error", "INTERNAL_ERROR", request), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled Exception", ex);
        return new ResponseEntity<>(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Unexpected error", "INTERNAL_ERROR", request), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
