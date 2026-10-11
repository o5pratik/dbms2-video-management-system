package edu.lab.vms;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,String>> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst()
            .map(e -> e.getDefaultMessage() == null ? "Invalid input" : e.getDefaultMessage()).orElse("Invalid input");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> database(DataAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Database operation failed. Check the connection and try again."));
    }
}

