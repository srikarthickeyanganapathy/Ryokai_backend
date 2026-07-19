package com.example.taskflow.dto;

import com.example.taskflow.domain.Announcement;
import java.time.LocalDateTime;

public class AnnouncementResponseDTO {

    private Long id;
    private String title;
    private String content;
    private UserSummaryDTO author;
    private LocalDateTime createdAt;

    public AnnouncementResponseDTO(Announcement announcement) {
        this.id = announcement.getId();
        this.title = announcement.getTitle();
        this.content = announcement.getContent();
        this.author = new UserSummaryDTO(
                announcement.getAuthor().getId(), 
                announcement.getAuthor().getUsername()
        );
        this.createdAt = announcement.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UserSummaryDTO getAuthor() {
        return author;
    }

    public void setAuthor(UserSummaryDTO author) {
        this.author = author;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
