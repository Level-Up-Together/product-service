package io.pinkspider.leveluptogethermvp.customerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.customerservice.domain.entity.CustomerInquiry;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryStatus;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CustomerInquiryResponse {

    private Long id;
    private InquiryType inquiryType;
    private String inquiryTypeDescription;
    private String title;
    private String content;
    private InquiryStatus status;
    private String statusDescription;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private List<ReplyResponse> replies;

    public static CustomerInquiryResponse from(CustomerInquiry inquiry) {
        return CustomerInquiryResponse.builder()
            .id(inquiry.getId())
            .inquiryType(inquiry.getInquiryType())
            .inquiryTypeDescription(inquiry.getInquiryType().getDescription())
            .title(inquiry.getTitle())
            .content(inquiry.getContent())
            .status(inquiry.getStatus())
            .statusDescription(inquiry.getStatus().getDescription())
            .createdAt(inquiry.getCreatedAt())
            .modifiedAt(inquiry.getModifiedAt())
            .replies(inquiry.getReplies().stream()
                .filter(reply -> Boolean.TRUE.equals(reply.getIsVisible()))
                .map(ReplyResponse::from)
                .toList())
            .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ReplyResponse {
        private Long id;
        private String content;
        private LocalDateTime createdAt;

        public static ReplyResponse from(io.pinkspider.leveluptogethermvp.customerservice.domain.entity.CustomerInquiryReply reply) {
            return ReplyResponse.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .build();
        }
    }
}
