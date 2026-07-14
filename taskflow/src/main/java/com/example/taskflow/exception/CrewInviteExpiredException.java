package com.example.taskflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class CrewInviteExpiredException extends RuntimeException {
    public CrewInviteExpiredException(String message) {
        super(message);
    }
}
