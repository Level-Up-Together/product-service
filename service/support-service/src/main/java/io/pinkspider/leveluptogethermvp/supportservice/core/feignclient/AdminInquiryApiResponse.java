package io.pinkspider.leveluptogethermvp.supportservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminInquiryApiResponse {
    private String code;
    private String message;
    private InquiryResponse value;
}
