package com.seatbook.error;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeclineException.class)
    ResponseEntity<Map<String, String>> decline(DeclineException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "declined", "reason", e.reason().label));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<Map<String, String>> badInput(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid request"));
    }

    /** Genuinely unexpected: this IS a 5xx and must stay visible in metrics. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("unhandled", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal"));
    }
}
