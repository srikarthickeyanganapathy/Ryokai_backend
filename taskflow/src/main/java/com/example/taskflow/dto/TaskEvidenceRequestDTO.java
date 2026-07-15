package com.example.taskflow.dto;

import com.example.taskflow.domain.EvidenceType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskEvidenceRequestDTO {

    @NotNull(message = "Evidence type is required")
    private EvidenceType type;

    @Size(max = 255)
    private String title;

    // LINK / GITHUB / RECORDING
    private String url;
    private String unfurlJson;

    // GITHUB
    private String ghRepo;
    private Integer ghPrNo;
    private String ghCommit;
    private String ghState;

    // SCREENSHOT
    private String imageKey;
    private Integer imageW;
    private Integer imageH;

    // RECORDING
    private String videoUrl;
    private Integer durationS;

    // SNIPPET
    private String codeLang;
    private String codeBody;

    // NOTE
    private String noteMd;
}
