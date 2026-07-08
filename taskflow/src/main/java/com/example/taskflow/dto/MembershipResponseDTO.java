package com.example.taskflow.dto;

import java.time.LocalDateTime;
import com.example.taskflow.domain.OrgRole;
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
    private OrgRole orgRole;
    private LocalDateTime joinedAt;
}
