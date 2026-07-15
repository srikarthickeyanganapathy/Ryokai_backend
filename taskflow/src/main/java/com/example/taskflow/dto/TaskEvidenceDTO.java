package com.example.taskflow.dto;

import java.time.LocalDateTime;

import com.example.taskflow.domain.EvidenceType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvidenceDTO {
    private Long id;
    private Long taskId;
    private EvidenceType type;
    private String addedBy;
    private String title;
    private String url;
    private String unfurlJson;
    private String ghRepo;
    private Integer ghPrNo;
    private String ghCommit;
    private String ghState;
    private String imageKey;
    private Integer imageW;
    private Integer imageH;
    private String videoUrl;
    private Integer durationS;
    private String codeLang;
    private String codeBody;
    private String noteMd;
    private LocalDateTime createdAt;
}
