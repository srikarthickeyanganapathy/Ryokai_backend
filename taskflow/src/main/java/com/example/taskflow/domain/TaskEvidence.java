package com.example.taskflow.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_evidence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TaskEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by", nullable = false)
    private User addedBy;

    @Column(length = 255)
    private String title;

    // LINK / GITHUB / RECORDING
    @Column(columnDefinition = "TEXT")
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unfurl_json", columnDefinition = "jsonb")
    private String unfurlJson;

    // GITHUB-specific
    @Column(name = "gh_repo", length = 255)
    private String ghRepo;

    @Column(name = "gh_pr_no")
    private Integer ghPrNo;

    @Column(name = "gh_commit", length = 64)
    private String ghCommit;

    @Column(name = "gh_state", length = 20)
    private String ghState;

    // SCREENSHOT-specific
    @Column(name = "image_key", length = 500)
    private String imageKey;

    @Column(name = "image_w")
    private Integer imageW;

    @Column(name = "image_h")
    private Integer imageH;

    // RECORDING-specific
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "duration_s")
    private Integer durationS;

    // SNIPPET-specific
    @Column(name = "code_lang", length = 50)
    private String codeLang;

    @Column(name = "code_body", columnDefinition = "TEXT")
    private String codeBody;

    // NOTE-specific
    @Column(name = "note_md", columnDefinition = "TEXT")
    private String noteMd;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Soft-delete fields: evidence is append-only for audit purposes. A
    // "deleted" record is hidden from normal listings but never physically
    // removed, so the historical timeline referenced in TaskStatusHistory
    // stays intact for auditors.
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
