package com.example.taskflow.task.api.response;

public record ImageUploadResponseDTO(
    String imageKey,
    String originalFilename,
    String uploadStatus
) {}
