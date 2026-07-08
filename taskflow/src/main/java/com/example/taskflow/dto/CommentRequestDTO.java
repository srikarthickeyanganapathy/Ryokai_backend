package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentRequestDTO {
    
    @NotBlank(message = "Comment text cannot be blank")
    @Size(max = 2000, message = "Comment text cannot exceed 2000 characters")
    private String text;

    public CommentRequestDTO() {}

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
