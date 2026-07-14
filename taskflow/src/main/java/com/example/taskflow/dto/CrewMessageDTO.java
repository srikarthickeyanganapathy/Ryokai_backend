package com.example.taskflow.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrewMessageDTO {
    private Long id;
    private String authorUsername;
    private String content;
    private Long taskId;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;
}
