package io.pinkspider.leveluptogethermvp.towerservice.application;

import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerTraceView;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerMissionSeat;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerMissionSeatRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-693: 실제 흔적 — 오늘(KST) 이 층에서 미션을 완료한 사람 수(실제 수치만)와 완료한 자리(누구인지는 내리지 않는다, 최근 50곳). 자동 착석으로 앉아
 * 완료한 사람도 실제 사람이라 센다. NPC 는 점유자가 아니므로 들어가지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "towerTransactionManager", readOnly = true)
public class TowerTraceService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int MAX_SEATS = 50;

    private final TowerMissionSeatRepository towerMissionSeatRepository;

    public TowerTraceView today(int floorNumber) {
        return on(floorNumber, LocalDate.now(KST));
    }

    /** KST 날짜 하루 = UTC [D-1 15:00, D 15:00) — 저장 시각은 UTC */
    public TowerTraceView on(int floorNumber, LocalDate kstDate) {
        LocalDateTime from =
                kstDate.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = from.plusDays(1);
        try {
            long completed = towerMissionSeatRepository.countCompletedUsers(floorNumber, from, to);
            Set<TowerTraceView.Point> seats = new LinkedHashSet<>();
            for (TowerMissionSeat seat :
                    towerMissionSeatRepository.findCompletedWithSeat(
                            floorNumber, from, to, PageRequest.of(0, MAX_SEATS))) {
                seats.add(new TowerTraceView.Point(seat.getSeatX(), seat.getSeatY()));
            }
            return new TowerTraceView(completed, new ArrayList<>(seats));
        } catch (RuntimeException e) {
            // 흔적은 덤이다 — 실패해도 입장 스냅샷은 내려간다
            log.warn("탑 실제 흔적 조회 실패: floor={}, error={}", floorNumber, e.getMessage());
            return TowerTraceView.EMPTY;
        }
    }
}
