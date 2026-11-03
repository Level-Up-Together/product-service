package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AttendanceRecord;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AttendanceRecordRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-539: attendance_record 첫 행 생성을 호출자와 **분리된 트랜잭션**에서 수행한다.
 *
 * <p>같은 날 출석 더블탭/동시 요청은 (user_id, attendance_date) 유니크 제약이 막지만, 예전처럼 호출자 트랜잭션에서 {@code saveAndFlush
 * + catch} 하면 제약 위반이 Hibernate 세션을 오염시켜(`don't flush the Session after an exception occurs`) 트랜잭션이
 * rollback-only 가 되고, 중복을 잡아 "이미 출석" 응답을 만들어도 커밋 시점에 UnexpectedRollbackException 으로 500 이 났다.
 * insert 를 REQUIRES_NEW 로 분리하면 제약 위반은 이 내부 트랜잭션만 롤백시킨다. 구조는 {@link
 * io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsCreator} 와 동일.
 *
 * <p>중복 예외는 삼키지 않고 호출자에게 던진다. 호출자는 예외를 받으면 자신의 컨텍스트에서 재조회해 상대 요청이 만든 행을 쓴다.
 *
 * <p>트레이드오프: 이 행은 호출자보다 먼저 커밋되므로, 이후 경험치 지급 단계가 실패해도 출석 행은 남는다 (같은 DB 로컬 연산이라 실패 확률은 극히 낮다).
 */
@Service
@RequiredArgsConstructor
public class AttendanceRecordCreator {

    private final AttendanceRecordRepository attendanceRecordRepository;

    /**
     * 출석 행을 별도 트랜잭션에서 생성한다. 호출자는 반환값 대신 (userId, date) 로 재조회해 managed 엔티티를 쓸 것.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 다른 요청이 먼저 출석 처리한 경우
     */
    @Transactional(
            transactionManager = "gamificationTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void create(
            String userId, LocalDate date, int consecutiveDays, int rewardExp, int bonusRewardExp) {
        AttendanceRecord record = AttendanceRecord.create(userId, date, consecutiveDays);
        record.setRewardExp(rewardExp);
        record.setBonusRewardExp(bonusRewardExp);
        attendanceRecordRepository.saveAndFlush(record);
    }
}
