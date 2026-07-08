package com.example.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChecklistItemRequestDTO {

    @NotBlank(message = "Checklist item text cannot be blank")
    @Size(max = 200, message = "Checklist item text cannot exceed 200 characters")
    private String text;

    public ChecklistItemRequestDTO() {}

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
