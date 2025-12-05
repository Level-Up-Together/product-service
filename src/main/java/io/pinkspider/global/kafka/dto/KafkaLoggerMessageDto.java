package io.pinkspider.global.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KafkaLoggerMessageDto {

    private String id;
    private String level;
    private String service;
    private String message;
    private String createdAt;
}

