package com.example.taskflow.whiteboard;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import com.example.taskflow.crew.domain.Crew;
import com.example.taskflow.user.domain.User;

@Entity
@Table(name = "whiteboards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Whiteboard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crew_id", nullable = false)
    private Crew crew;

    @Column(nullable = false)
    private String title;

    // Base64 PNG data URL â€” periodic durability snapshot, not the live
    // draw stream. Live strokes travel over STOMP only.
    @Column(name = "snapshot_data_url", columnDefinition = "TEXT")
    private String snapshotDataUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}