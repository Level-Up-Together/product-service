package io.pinkspider.global.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppPushMessageDto {

    @JsonProperty("template_id")
    private String templateId;

    @JsonProperty("user_id")
    private String userId;
}
