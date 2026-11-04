package io.pinkspider.leveluptogethermvp.towerservice.application.queue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * LUT-701: 대기열 공유 저장소 — 여러 노드가 같은 줄을 본다(Redis, {@link RedisTowerQueueStore}). 줄은 둘이다: 로그인 유저 줄이 방문객
 * 줄보다 늘 앞이다. 순서는 줄을 선 시각.
 */
public interface TowerQueueStore {

    /** 탑 전체 웹소켓 연결 수 */
    long connectedCount();

    Optional<TowerQueueTicket> find(String ticket);

    /** 표를 줄 끝에 세운다(로그인 표면 유저 → 표 연결도) */
    void enqueue(TowerQueueTicket ticket);

    /** 표 정보만 갱신(마지막 조회 시각·입장 가능 마감) */
    void update(TowerQueueTicket ticket);

    /** 줄·입장 가능 목록·표 정보·유저 연결을 모두 지운다 */
    void delete(TowerQueueTicket ticket);

    Optional<String> ticketOfUser(String userId);

    long waitingCount(boolean visitors);

    /** 그 줄에서 0부터 센 자리. 줄에 없으면 empty */
    Optional<Long> waitingRank(TowerQueueTicket ticket);

    /** 줄 맨 앞부터 n장 */
    List<String> waitingHead(boolean visitors, int limit);

    /** 줄 전체 (오래 조회하지 않은 표 정리용) */
    List<String> waitingAll(boolean visitors);

    /** 줄에서 빼 "입장 가능" 목록으로 옮긴다 (마감 ms) */
    void markAdmitted(TowerQueueTicket ticket, long untilMs);

    /** 아직 마감이 안 지난 입장 가능 표 수 — 곧 들어올 자리라 상한 계산에 넣는다 */
    long admittedCount(long nowMs);

    /** 마감이 지난 입장 가능 표 */
    List<String> admittedExpired(long nowMs);

    boolean tryLock(Duration ttl);

    void unlock();
}
