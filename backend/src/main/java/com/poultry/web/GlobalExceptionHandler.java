package com.poultry.web;

import com.poultry.service.BusinessException;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public record ApiError(Instant timestamp, int status, String message, Map<String, String> fieldErrors) {}

    private ResponseEntity<ApiError> of(HttpStatus s, String m, Map<String, String> f) {
        return ResponseEntity.status(s).body(new ApiError(Instant.now(), s.value(), m, f));
    }
    @ExceptionHandler(BusinessException.class) ResponseEntity<ApiError> business(BusinessException e) { return of(e.status(), e.getMessage(), Map.of()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> f = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x -> f.put(x.getField(), x.getDefaultMessage()));
        return of(HttpStatus.BAD_REQUEST, "Please check the highlighted fields", f);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> badInput(Exception e) { return of(HttpStatus.BAD_REQUEST, "The request could not be read. Check the values you sent.", Map.of()); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        log.warn("Integrity violation", e);
        return of(HttpStatus.CONFLICT, "That change conflicts with existing records", Map.of());
    }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ApiError> denied(AccessDeniedException e) { return of(HttpStatus.FORBIDDEN, "You don’t have permission to do that", Map.of()); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        log.error("Unhandled error", e);   // details stay in the log, never in the response
        return of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server", Map.of());
    }
}
