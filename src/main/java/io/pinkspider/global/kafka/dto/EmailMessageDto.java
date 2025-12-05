package io.pinkspider.global.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailMessageDto {

    @JsonProperty("template_file_name")
    private String templateFileName;

    @JsonProperty("receiver_email_address")
    private String receiverEmailAddress;

    @JsonProperty("email_subject")
    private String emailSubject;

    @JsonProperty("is_included_logo")
    private Boolean isIncludedLogo;

    @JsonProperty("content_map_values")
    private Map<String, Object> contentMapValues;
}
