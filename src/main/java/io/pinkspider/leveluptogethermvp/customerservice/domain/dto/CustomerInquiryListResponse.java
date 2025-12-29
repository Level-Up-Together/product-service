package io.pinkspider.leveluptogethermvp.customerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.customerservice.domain.entity.CustomerInquiry;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryStatus;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryType;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CustomerInquiryListResponse {

    private Long id;
    private InquiryType inquiryType;
    private String inquiryTypeDescription;
    private String title;
    private InquiryStatus status;
    private String statusDescription;
    private boolean hasReply;
    private LocalDateTime createdAt;

    public static CustomerInquiryListResponse from(CustomerInquiry inquiry) {
        return CustomerInquiryListResponse.builder()
            .id(inquiry.getId())
            .inquiryType(inquiry.getInquiryType())
            .inquiryTypeDescription(inquiry.getInquiryType().getDescription())
            .title(inquiry.getTitle())
            .status(inquiry.getStatus())
            .statusDescription(inquiry.getStatus().getDescription())
            .hasReply(!inquiry.getReplies().isEmpty())
            .createdAt(inquiry.getCreatedAt())
            .build();
    }
}
