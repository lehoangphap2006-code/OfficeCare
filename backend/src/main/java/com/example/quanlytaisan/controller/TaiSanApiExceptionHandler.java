package com.example.quanlytaisan.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice(assignableTypes = TaiSanController.class)
public class TaiSanApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException exception) {
        String detail = exception.getReason() == null ? "Yêu cầu không thể xử lý." : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("detail", detail));
    }
}
