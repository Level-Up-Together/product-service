package io.pinkspider.leveluptogethermvp.supportservice.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InquiryResponse {

    private Long id;

    @JsonProperty("inquiry_type")
    private InquiryType inquiryType;

    @JsonProperty("inquiry_type_name")
    private String inquiryTypeName;

    private String title;

    private String content;

    private InquiryStatus status;

    @JsonProperty("status_name")
    private String statusName;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("modified_at")
    private LocalDateTime modifiedAt;

    private List<ReplyResponse> replies;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyResponse {
        private Long id;
        private String content;
        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }
}
