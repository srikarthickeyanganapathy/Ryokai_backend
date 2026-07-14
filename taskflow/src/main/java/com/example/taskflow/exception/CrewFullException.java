package com.example.taskflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CrewFullException extends RuntimeException {
    public CrewFullException(String message) {
        super(message);
    }
}
