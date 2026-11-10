package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-630: 입장 결과. {@code resumed} = 자리 유지 중이던 방(또는 자동 착석 자리)으로 복귀. {@code visitor_key} 는
 * 방문객(비로그인)에게만 — 퇴장 때 본인 확인용(세션·이름은 LUT-705). 좌표는 배경 원본 픽셀.
 *
 * <p>LUT-646: {@code auto_seat_continued} = 자동 착석 자리에서 직접 입장으로 이어졌는가(자동 착석 → 직접 입장 전환율 측정). 추가 필드라
 * 구앱은 무시한다.
 *
 * <p>LUT-701: 탑 전체 동시 접속 상한에 닿으면 배정 대신 {@code queued=true} + {@code queue}(순번·예상 대기·입장 가능 마감)를 돌려준다
 * — 이때 인스턴스·점유자 필드는 비어 있다. 입장 가능 신호를 받으면 같은 표로 다시 입장을 요청한다.
 *
 * <p>LUT-734: {@code first_visit} = 이 층의 첫 방문 안내(지금 버전)를 처음 보는 입장인가 — 서버가 유저×층으로 기억한다(기기를 바꿔도 다시 안
 * 뜸). 안내가 없으면 false, 방문객은 null(클라이언트가 기기에 기억).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerEnterResponse(
        Integer floorNumber,
        Long instanceId,
        Long occupantId,
        Boolean resumed,
        Boolean missionActive,
        String visitorKey,
        Integer entryPointX,
        Integer entryPointY,
        Integer seatX,
        Integer seatY,
        Long population,
        Integer visitorNo,
        Boolean autoSeatContinued,
        Boolean queued,
        TowerQueueStatus queue,
        Boolean firstVisit) {

    public static TowerEnterResponse queued(int floorNumber, TowerQueueStatus status) {
        return new TowerEnterResponse(
                floorNumber,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                status.population(),
                null,
                false,
                true,
                status,
                null);
    }
}
