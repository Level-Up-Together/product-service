package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LUT-705: 탑 방문객 세션·빈도 제한·제재 설정 — config-repository 환경별로 덮어쓸 수 있다 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "tower.visitor")
public class TowerVisitorProperties {
    /** 방문객 세션 유지 시간(마지막 입장부터) — 같은 기기는 이 안에서 같은 키·번호로 다시 들어온다 */
    private int sessionTtlHours = 24;

    /** 같은 IP 의 방문객 입장 요청 수 상한(1분) */
    private int enterPerMinutePerIp = 20;

    /** 같은 IP 에서 동시에 쓰는 방문객 세션 수 상한 */
    private int sessionsPerIp = 10;

    /** 세션을 "쓰는 중" 으로 보는 창(마지막 입장부터) — 방문객 무조작 퇴장(10분)과 같다 */
    private int sessionWindowMinutes = 10;

    /** 같은 IP 의 방문객 채팅·리액션 수 상한(1분) — 점유자별 5초 간격과 별개 */
    private int chatPerMinutePerIp = 12;

    /** 제재 목록 메모 — 어드민이 걸거나 풀면 이 안에 반영된다(저장한 노드는 즉시) */
    private int sanctionCacheSeconds = 30;

    /** 신고에 남길 최근 원문 수 */
    private int reportMessageCount = 20;

    /** 같은 신고자가 같은 방문객을 다시 신고해도 새로 남기지 않는 창 */
    private int reportDuplicateHours = 24;
}
