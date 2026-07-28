package com.example.taskflow.crew.dto;

import java.time.LocalDateTime;

import com.example.taskflow.crew.domain.CrewRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.taskflow.organization.rbac.domain.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrewMemberDTO {
    private Long userId;
    private String username;
    private CrewRole role;
    private LocalDateTime joinedAt;
}