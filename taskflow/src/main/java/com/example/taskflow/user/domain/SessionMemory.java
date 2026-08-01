package com.example.taskflow.user.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_memory")
@Getter
@Setter
public class SessionMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "last_workspace_lens", length = 50)
    private String lastWorkspaceLens;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 30, nullable = false)
    private com.example.taskflow.workspace.domain.Mode mode = com.example.taskflow.workspace.domain.Mode.NORMAL;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "active_filters", columnDefinition = "jsonb")
    private String activeFilters;

    @Column(name = "last_active_drawer", length = 50)
    private String lastActiveDrawer;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "recent_searches", columnDefinition = "jsonb")
    private String recentSearches;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "pinned_items", columnDefinition = "jsonb")
    private String pinnedItems;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
