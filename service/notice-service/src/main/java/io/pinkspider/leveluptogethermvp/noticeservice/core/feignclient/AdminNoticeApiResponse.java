package io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminNoticeApiResponse {
    private String code;
    private String message;
    private List<NoticeResponse> value;
}
