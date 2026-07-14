package com.example.taskflow.dto;

import com.example.taskflow.domain.ChannelType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrewChannelDTO {
    private Long id;
    private String name;
    private ChannelType type;
    private Integer position;
    private int messageCount;
}
