package com.example.taskflow.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "key_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class KeyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(nullable = false)
    private String title;

    @Column(name = "current_value", nullable = false)
    private Double currentValue = 0.0;

    @Column(name = "target_value", nullable = false)
    private Double targetValue;

    private String unit;
}
