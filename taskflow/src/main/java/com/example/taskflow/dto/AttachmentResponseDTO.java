package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class AttachmentResponseDTO {
    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String uploadedBy;

    public AttachmentResponseDTO() {}

    public AttachmentResponseDTO(Long id, String originalFilename, String contentType, Long fileSize, LocalDateTime uploadedAt, String uploadedBy) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
