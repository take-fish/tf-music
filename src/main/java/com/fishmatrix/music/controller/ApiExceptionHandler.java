package com.fishmatrix.music.controller;

import com.fishmatrix.music.model.NotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "参数不合法" : exception.getMessage();
        return Map.of("error", "BAD_REQUEST", "message", message);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NotFoundException exception) {
        return Map.of("error", "NOT_FOUND", "message", exception.getMessage());
    }
}
