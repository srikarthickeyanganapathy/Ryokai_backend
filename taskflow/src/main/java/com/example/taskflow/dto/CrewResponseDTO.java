package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.taskflow.domain.CrewVisibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrewResponseDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String avatarUrl;
    private CrewVisibility visibility;
    private Integer memberCap;
    private int memberCount;
    private String myRole;
    private LocalDateTime createdAt;
    private List<CrewChannelDTO> channels;
    private List<CrewMemberDTO> members;
}
