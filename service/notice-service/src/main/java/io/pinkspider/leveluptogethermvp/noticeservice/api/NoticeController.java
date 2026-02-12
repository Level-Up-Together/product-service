package io.pinkspider.leveluptogethermvp.noticeservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.application.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /**
     * 현재 활성화된 공지사항 목록 조회
     * - 홈 화면에서 표시할 공지사항
     */
    @GetMapping
    public ResponseEntity<ApiResult<List<NoticeResponse>>> getActiveNotices() {
        List<NoticeResponse> notices = noticeService.getActiveNotices();
        return ResponseEntity.ok(ApiResult.<List<NoticeResponse>>builder().value(notices).build());
    }

    /**
     * 공지사항 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResult<NoticeResponse>> getNotice(@PathVariable Long id) {
        NoticeResponse notice = noticeService.getNoticeById(id);
        if (notice == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResult.<NoticeResponse>builder().value(notice).build());
    }
}
