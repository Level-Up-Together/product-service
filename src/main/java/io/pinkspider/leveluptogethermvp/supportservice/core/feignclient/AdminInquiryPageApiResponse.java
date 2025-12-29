package io.pinkspider.leveluptogethermvp.supportservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.supportservice.api.dto.InquiryResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminInquiryPageApiResponse {
    private String code;
    private String message;
    private PageValue value;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageValue {
        private List<InquiryResponse> content;
        private int totalPages;
        private long totalElements;
        private int size;
        private int number;
        private boolean first;
        private boolean last;
        private boolean empty;
    }
}
