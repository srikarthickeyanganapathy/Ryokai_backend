package com.example.taskflow.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembershipResponseDTO {
    private Long id;
    private Long userId;
    private String username;
    private String orgRole;
    private Integer rolePriority;
    private java.util.List<String> permissions;
    private LocalDateTime joinedAt;
}
