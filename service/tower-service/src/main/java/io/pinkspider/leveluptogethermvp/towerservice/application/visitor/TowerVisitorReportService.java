package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorReport;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerChatLogRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerVisitorReportRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-705: 방문객 신고 — 로그인 유저가 같은 방의 방문객을 신고하면 그 방문객 세션의 최근 원문(운영 로그)과 기기 정보(IP·기기 ID·User-Agent)를 함께
 * 남긴다. 운영자는 이것으로 기기·IP 제재를 건다. 같은 신고자가 같은 방문객을 24시간 안에 다시 신고하면 새로 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TowerVisitorReportService {
    private final TowerOccupantRepository occupantRepository;
    private final TowerVisitorReportRepository reportRepository;
    private final TowerChatLogRepository chatLogRepository;
    private final TowerVisitorSessionStore sessionStore;
    private final TowerVisitorProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(transactionManager = "towerTransactionManager")
    public TowerVisitorReportResponse report(
            String reporterUserId, Long instanceId, TowerVisitorReportRequest request) {
        occupantRepository
                .findByUserId(reporterUserId)
                .filter(o -> instanceId.equals(o.getInstanceId()))
                .orElseThrow(TowerEntryException::notInInstance);
        TowerOccupant target =
                occupantRepository
                        .findById(request.occupantId())
                        .filter(o -> o.isVisitor() && instanceId.equals(o.getInstanceId()))
                        .orElseThrow(TowerVisitorException::reportTargetNotFound);
        LocalDateTime now = LocalDateTime.now();
        Optional<TowerVisitorReport> recent =
                reportRepository
                        .findFirstByReporterUserIdAndVisitorKeyAndCreatedAtAfterOrderByIdDesc(
                                reporterUserId,
                                target.getVisitorKey(),
                                now.minusHours(properties.getReportDuplicateHours()));
        if (recent.isPresent()) {
            return new TowerVisitorReportResponse(recent.get().getId(), true);
        }
        TowerVisitorSession session = sessionStore.find(target.getVisitorKey()).orElse(null);
        TowerVisitorReport saved =
                reportRepository.save(
                        TowerVisitorReport.builder()
                                .reporterUserId(reporterUserId)
                                .instanceId(instanceId)
                                .floorNumber(target.getFloorNumber())
                                .targetOccupantId(target.getId())
                                .visitorKey(target.getVisitorKey())
                                .visitorNo(target.getVisitorNo())
                                .clientIp(session != null ? session.ip() : null)
                                .deviceId(session != null ? session.deviceId() : null)
                                .userAgent(session != null ? session.userAgent() : null)
                                .reason(request.reason())
                                .recentMessages(recentMessages(target.getVisitorKey()))
                                .build());
        log.info(
                "[tower-visitor] 방문객 신고: reportId={} instanceId={} visitorNo={}",
                saved.getId(),
                instanceId,
                target.getVisitorNo());
        return new TowerVisitorReportResponse(saved.getId(), false);
    }

    @Transactional(transactionManager = "towerTransactionManager", readOnly = true)
    public TowerAdminPage<TowerVisitorReportAdminResponse> list(int page, int size) {
        return TowerAdminPage.of(
                reportRepository
                        .findAllByOrderByIdDesc(
                                PageRequest.of(
                                        Math.max(0, page),
                                        TowerVisitorSanctionService.clampSize(size)))
                        .map(
                                r ->
                                        TowerVisitorReportAdminResponse.from(
                                                r, TowerVisitorTag.of(r.getVisitorKey()))));
    }

    /** 최근 원문(최신 먼저) — JSON 배열. 읽기·직렬화 실패는 빈 배열(신고 자체는 남긴다) */
    String recentMessages(String visitorKey) {
        try {
            List<TowerChatLog> logs =
                    chatLogRepository.findTop20ByVisitorKeyOrderByIdDesc(visitorKey);
            List<Map<String, Object>> lines =
                    logs.stream()
                            .limit(properties.getReportMessageCount())
                            .map(TowerVisitorReportService::line)
                            .toList();
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("[tower-visitor] 신고 원문 수집 실패: {}", e.getMessage());
            return "[]";
        }
    }

    private static Map<String, Object> line(TowerChatLog log) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("sent_at", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        line.put("instance_id", log.getInstanceId());
        line.put("content", log.getOriginalContent());
        line.put("result", log.getResult());
        return line;
    }
}
