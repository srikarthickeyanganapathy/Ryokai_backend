package com.example.taskflow.dto;

import com.example.taskflow.domain.ChannelType;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CrewChannelRequestDTO {

    @NotBlank(message = "Channel name is required")
    private String name;

    private ChannelType type = ChannelType.TEXT;
}
