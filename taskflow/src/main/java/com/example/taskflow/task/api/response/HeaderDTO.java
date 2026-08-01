package com.example.taskflow.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeaderDTO {
    private String eyebrow;
    private String title;
    private String subtitle;
}
