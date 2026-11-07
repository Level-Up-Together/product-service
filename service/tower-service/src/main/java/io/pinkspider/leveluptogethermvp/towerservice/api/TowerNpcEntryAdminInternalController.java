package io.pinkspider.leveluptogethermvp.towerservice.api;

import io.pinkspider.global.api.ApiResult;
import io.pinkspider.leveluptogethermvp.towerservice.application.TowerNpcEntryAdminService;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryPurgeResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntrySeedRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerNpcEntryStatusRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LUT-760: NPC 수집함 내부 API (admin-service 패스스루 LUT-761, X-Internal-Api-Key). 이메일이 들어 있으므로 어드민 권한
 * 확인·내보내기 감사 로그는 admin-service 가 한다. 입력 받기 <b>설정</b>은 NPC 등록·수정 요청의 {@code form} 으로 저장한다.
 */
@RestController
@RequestMapping("/api/internal/tower/npcs/{npcId}/entries")
@RequiredArgsConstructor
public class TowerNpcEntryAdminInternalController {

    private final TowerNpcEntryAdminService adminService;

    @GetMapping
    public ApiResult<TowerAdminPage<TowerNpcEntryAdminResponse>> list(
            @PathVariable Long npcId,
            @RequestParam(required = false) TowerNpcEntryStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean reported,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return ApiResult.<TowerAdminPage<TowerNpcEntryAdminResponse>>builder()
                .value(adminService.list(npcId, status, reported, from, to, page, size))
                .build();
    }

    /** 전체(최신 먼저, 이메일 포함) — admin-service 가 CSV 로 만든다 */
    @GetMapping("/export")
    public ApiResult<List<TowerNpcEntryAdminResponse>> export(@PathVariable Long npcId) {
        return ApiResult.<List<TowerNpcEntryAdminResponse>>builder()
                .value(adminService.export(npcId))
                .build();
    }

    @PutMapping("/{entryId}/status")
    public ApiResult<TowerNpcEntryAdminResponse> updateStatus(
            @PathVariable Long npcId,
            @PathVariable Long entryId,
            @Valid @RequestBody TowerNpcEntryStatusRequest request) {
        return ApiResult.<TowerNpcEntryAdminResponse>builder()
                .value(adminService.updateStatus(npcId, entryId, request.status()))
                .build();
    }

    @PostMapping("/seed")
    public ApiResult<TowerNpcEntryAdminResponse> addSeed(
            @PathVariable Long npcId, @Valid @RequestBody TowerNpcEntrySeedRequest request) {
        return ApiResult.<TowerNpcEntryAdminResponse>builder()
                .value(adminService.addSeed(npcId, request.body()))
                .build();
    }

    /** 이메일 일괄 파기(본문은 남긴다) — 확인 창은 어드민 화면 몫 */
    @DeleteMapping("/emails")
    public ApiResult<TowerNpcEntryPurgeResponse> purgeEmails(@PathVariable Long npcId) {
        return ApiResult.<TowerNpcEntryPurgeResponse>builder()
                .value(adminService.purgeEmails(npcId))
                .build();
    }
}
