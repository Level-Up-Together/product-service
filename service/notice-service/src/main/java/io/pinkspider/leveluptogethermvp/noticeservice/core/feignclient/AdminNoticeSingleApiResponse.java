package io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminNoticeSingleApiResponse {
    private String code;
    private String message;
    private NoticeResponse value;
}
