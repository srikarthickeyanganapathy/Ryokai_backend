package com.example.taskflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMessageResponseDTO {
    private Long id;
    private Long teamId;
    private Long authorId;
    private String authorUsername;
    private String content;
    private LocalDateTime createdAt;
}
