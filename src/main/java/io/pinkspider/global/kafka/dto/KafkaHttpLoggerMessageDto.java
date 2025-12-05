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
public class KafkaHttpLoggerMessageDto {

    private String direction;
    private String method;
    private String traceId;
    private String spanId;
    private String service;
    private String payload;
    private String createdDate;
}
