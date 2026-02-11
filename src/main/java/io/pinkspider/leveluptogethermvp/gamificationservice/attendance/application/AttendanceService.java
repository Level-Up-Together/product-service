package io.pinkspider.leveluptogethermvp.gamificationservice.attendance.application;

import static io.pinkspider.leveluptogethermvp.metaservice.domain.entity.MissionCategory.DEFAULT_CATEGORY_NAME;

import io.pinkspider.global.event.AttendanceStreakEvent;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AttendanceRecord;
import io.pinkspider.global.cache.AttendanceRewardConfigCacheService;
import io.pinkspider.global.enums.ExpSourceType;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AttendanceRecordRepository;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.AttendanceCheckInResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.AttendanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.attendance.domain.dto.MonthlyAttendanceResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(transactionManager = "gamificationTransactionManager", readOnly = true)
public class AttendanceService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceRewardConfigCacheService rewardConfigCacheService;
    private final UserExperienceService userExperienceService;
    private final ApplicationEventPublisher eventPublisher;

    private static final int DEFAULT_DAILY_EXP = 10;
    private static final Set<Integer> STREAK_MILESTONES = Set.of(7, 14, 30, 60, 90);

    @Transactional(transactionManager = "gamificationTransactionManager")
    public AttendanceCheckInResponse checkIn(String userId) {
        LocalDate today = LocalDate.now();

        // 이미 출석했는지 확인
        Optional<AttendanceRecord> existingRecord =
            attendanceRecordRepository.findByUserIdAndAttendanceDate(userId, today);

        if (existingRecord.isPresent()) {
            return AttendanceCheckInResponse.alreadyCheckedIn(
                AttendanceResponse.from(existingRecord.get()));
        }

        // 연속 출석 일수 계산
        int consecutiveDays = calculateConsecutiveDays(userId, today);

        // 출석 기록 생성
        AttendanceRecord record = AttendanceRecord.create(userId, today, consecutiveDays);

        // 기본 보상 경험치
        int baseExp = getBaseRewardExp();
        record.setRewardExp(baseExp);

        // 보너스 경험치 계산
        List<String> bonusReasons = new ArrayList<>();
        int bonusExp = calculateBonusExp(consecutiveDays, bonusReasons);
        record.setBonusRewardExp(bonusExp);

        AttendanceRecord savedRecord;
        try {
            savedRecord = attendanceRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 레이스 컨디션: 다른 요청이 먼저 출석 처리한 경우
            log.debug("출석 체크 중복 감지, 기존 레코드 조회: userId={}, date={}", userId, today);
            return attendanceRecordRepository.findByUserIdAndAttendanceDate(userId, today)
                .map(existing -> AttendanceCheckInResponse.alreadyCheckedIn(AttendanceResponse.from(existing)))
                .orElseThrow(() -> new IllegalStateException(
                    "AttendanceRecord not found after duplicate key error: userId=" + userId + ", date=" + today));
        }

        // 경험치 지급
        int totalExp = baseExp + bonusExp;
        if (totalExp > 0) {
            userExperienceService.addExperience(
                userId,
                totalExp,
                ExpSourceType.EVENT,
                savedRecord.getId(),
                "출석 체크 보상" + (consecutiveDays > 1 ? " (" + consecutiveDays + "일 연속)" : ""),
                DEFAULT_CATEGORY_NAME
            );
        }

        // 연속 출석 마일스톤 달성 시 피드 프로젝션 이벤트 발행
        if (STREAK_MILESTONES.contains(consecutiveDays)) {
            eventPublisher.publishEvent(new AttendanceStreakEvent(userId, consecutiveDays));
        }

        log.info("출석 체크 완료: userId={}, consecutiveDays={}, totalExp={}",
            userId, consecutiveDays, totalExp);

        return AttendanceCheckInResponse.success(
            AttendanceResponse.from(savedRecord),
            baseExp,
            bonusExp,
            bonusReasons
        );
    }

    public boolean hasCheckedInToday(String userId) {
        return attendanceRecordRepository.existsByUserIdAndAttendanceDate(userId, LocalDate.now());
    }

    public MonthlyAttendanceResponse getMonthlyAttendance(String userId, String yearMonth) {
        String targetYearMonth = yearMonth != null ? yearMonth :
            LocalDate.now().getYear() + "-" + String.format("%02d", LocalDate.now().getMonthValue());

        List<AttendanceRecord> records =
            attendanceRecordRepository.findByUserIdAndYearMonthOrderByDayOfMonthAsc(userId, targetYearMonth);

        Set<Integer> attendedDays = records.stream()
            .map(AttendanceRecord::getDayOfMonth)
            .collect(Collectors.toSet());

        int totalExpEarned = records.stream()
            .mapToInt(AttendanceRecord::getTotalRewardExp)
            .sum();

        // 해당 월의 총 일수
        String[] parts = targetYearMonth.split("-");
        YearMonth ym = YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        int totalDays = ym.lengthOfMonth();

        // 현재 연속 출석 일수
        int currentStreak = getCurrentStreak(userId);

        // 최대 연속 출석 일수
        int maxStreak = attendanceRecordRepository.findMaxConsecutiveDaysByUserId(userId).orElse(0);

        return MonthlyAttendanceResponse.builder()
            .yearMonth(targetYearMonth)
            .totalDays(totalDays)
            .attendedDays(records.size())
            .currentStreak(currentStreak)
            .maxStreak(maxStreak)
            .attendedDayList(attendedDays)
            .totalExpEarned(totalExpEarned)
            .records(records.stream().map(AttendanceResponse::from).toList())
            .build();
    }

    public int getCurrentStreak(String userId) {
        Optional<AttendanceRecord> latestRecord =
            attendanceRecordRepository.findLatestByUserId(userId);

        if (latestRecord.isEmpty()) {
            return 0;
        }

        AttendanceRecord record = latestRecord.get();
        LocalDate today = LocalDate.now();

        // 어제나 오늘 출석했으면 연속 출석 유지
        if (record.getAttendanceDate().equals(today) ||
            record.getAttendanceDate().equals(today.minusDays(1))) {
            return record.getConsecutiveDays();
        }

        return 0;
    }

    private int calculateConsecutiveDays(String userId, LocalDate today) {
        Optional<AttendanceRecord> yesterdayRecord =
            attendanceRecordRepository.findByUserIdAndAttendanceDate(userId, today.minusDays(1));

        if (yesterdayRecord.isPresent()) {
            return yesterdayRecord.get().getConsecutiveDays() + 1;
        }

        return 1;
    }

    private int getBaseRewardExp() {
        AttendanceRewardConfig config = rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.DAILY);
        return config != null ? config.getRewardExp() : DEFAULT_DAILY_EXP;
    }

    private int calculateBonusExp(int consecutiveDays, List<String> bonusReasons) {
        int totalBonus = 0;

        // 연속 출석 보너스 체크
        if (consecutiveDays >= 3) {
            AttendanceRewardConfig bonus3 =
                rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.CONSECUTIVE_3);
            if (bonus3 != null && consecutiveDays == 3) {
                totalBonus += bonus3.getRewardExp();
                bonusReasons.add("3일 연속 출석 보너스!");
            }
        }

        if (consecutiveDays >= 7) {
            AttendanceRewardConfig bonus7 =
                rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.CONSECUTIVE_7);
            if (bonus7 != null && consecutiveDays == 7) {
                totalBonus += bonus7.getRewardExp();
                bonusReasons.add("7일 연속 출석 보너스!");
            }
        }

        if (consecutiveDays >= 14) {
            AttendanceRewardConfig bonus14 =
                rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.CONSECUTIVE_14);
            if (bonus14 != null && consecutiveDays == 14) {
                totalBonus += bonus14.getRewardExp();
                bonusReasons.add("14일 연속 출석 보너스!");
            }
        }

        if (consecutiveDays >= 30) {
            AttendanceRewardConfig bonus30 =
                rewardConfigCacheService.getConfigByRewardType(AttendanceRewardType.CONSECUTIVE_30);
            if (bonus30 != null && consecutiveDays == 30) {
                totalBonus += bonus30.getRewardExp();
                bonusReasons.add("30일 연속 출석 보너스! 대단해요!");
            }
        }

        return totalBonus;
    }

    public void initializeDefaultRewardConfigs() {
        rewardConfigCacheService.initializeDefaultRewardConfigs();
    }
}
