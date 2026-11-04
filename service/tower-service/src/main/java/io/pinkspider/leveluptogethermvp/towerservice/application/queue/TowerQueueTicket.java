package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import org.springframework.lang.Nullable;

/**
 * LUT-701: 입장 대기표. {@code userId} 가 없으면 방문객(표 자체가 본인 확인 수단). {@code admittedUntil} 이 있으면 "입장 가능" 상태
 * — 그 시각(ms)까지 입장하지 않으면 다음 순번으로 넘어간다.
 */
public record TowerQueueTicket(
        String ticket,
        @Nullable String userId,
        int floorNumber,
        long enqueuedAt,
        long lastSeenAt,
        @Nullable Long admittedUntil) {

    public boolean isVisitor() {
        return userId == null;
    }

    public boolean isAdmitted(long nowMs) {
        return admittedUntil != null && admittedUntil >= nowMs;
    }

    /** 요청자가 이 표의 주인인가 — 로그인 표는 같은 유저만, 방문객 표는 표를 가진 비로그인 요청 */
    public boolean ownedBy(@Nullable String requesterUserId) {
        return userId == null ? requesterUserId == null : userId.equals(requesterUserId);
    }

    public TowerQueueTicket seen(long nowMs) {
        return new TowerQueueTicket(ticket, userId, floorNumber, enqueuedAt, nowMs, admittedUntil);
    }

    public TowerQueueTicket admit(long untilMs) {
        return new TowerQueueTicket(ticket, userId, floorNumber, enqueuedAt, lastSeenAt, untilMs);
    }
}
