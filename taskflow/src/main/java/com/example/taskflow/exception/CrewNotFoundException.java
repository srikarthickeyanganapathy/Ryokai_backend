package com.example.taskflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CrewNotFoundException extends RuntimeException {
    public CrewNotFoundException(String message) {
        super(message);
    }
}
