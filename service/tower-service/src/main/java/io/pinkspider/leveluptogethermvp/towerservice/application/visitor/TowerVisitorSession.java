package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-705: 방문객 세션 — 비로그인 유저가 처음 들어올 때 서버가 키(UUID)와 이름 번호(네 자리)를 정하고, 같은 키로 다시 오면(층 이동·재입장) 같은 번호를
 * 쓴다. 숨기기·제재·신고의 기준이며, 제재 판정용 IP·기기 ID 는 마지막 입장 때 값으로 갱신된다. Redis 값(JSON)
 */
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TowerVisitorSession(
        String visitorKey,
        Integer visitorNo,
        String ip,
        String deviceId,
        String userAgent,
        Long createdAtMs) {

    /** 마지막 입장의 단서로 갱신(값이 없으면 이전 값 유지) */
    public TowerVisitorSession refreshedBy(TowerVisitorContext context) {
        return new TowerVisitorSession(
                visitorKey,
                visitorNo,
                context.ip() != null ? context.ip() : ip,
                context.deviceId() != null ? context.deviceId() : deviceId,
                context.userAgent() != null ? context.userAgent() : userAgent,
                createdAtMs);
    }
}
