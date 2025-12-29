package io.pinkspider.leveluptogethermvp.supportservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminInquiryTypesApiResponse {
    private String code;
    private String message;
    private InquiryType[] value;
}
