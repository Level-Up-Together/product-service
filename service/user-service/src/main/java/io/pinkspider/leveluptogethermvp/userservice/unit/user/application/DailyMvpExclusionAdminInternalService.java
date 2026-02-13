package io.pinkspider.leveluptogethermvp.userservice.unit.user.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.DailyMvpExclusionAdminRequest;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin.DailyMvpExclusionAdminResponse;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.DailyMvpExclusion;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.DailyMvpExclusionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "userTransactionManager")
public class DailyMvpExclusionAdminInternalService {

    private final DailyMvpExclusionRepository dailyMvpExclusionRepository;

    public List<DailyMvpExclusionAdminResponse> getExclusionsByDate(LocalDate date) {
        return dailyMvpExclusionRepository.findAllByMvpDateOrderByCreatedAtDesc(date).stream()
            .map(DailyMvpExclusionAdminResponse::from)
            .collect(Collectors.toList());
    }

    @Transactional(transactionManager = "userTransactionManager")
    public DailyMvpExclusionAdminResponse addExclusion(DailyMvpExclusionAdminRequest request) {
        if (dailyMvpExclusionRepository.existsByMvpDateAndUserId(request.mvpDate(), request.userId())) {
            throw new CustomException("400", "이미 해당 날짜에 제외된 사용자입니다.");
        }

        DailyMvpExclusion exclusion = DailyMvpExclusion.builder()
            .mvpDate(request.mvpDate())
            .userId(request.userId())
            .reason(request.reason())
            .adminId(request.adminId())
            .build();

        DailyMvpExclusion saved = dailyMvpExclusionRepository.save(exclusion);
        log.info("MVP 제외 추가: date={}, userId={}", request.mvpDate(), request.userId());
        return DailyMvpExclusionAdminResponse.from(saved);
    }

    @Transactional(transactionManager = "userTransactionManager")
    public void removeExclusion(LocalDate date, String userId) {
        if (!dailyMvpExclusionRepository.existsByMvpDateAndUserId(date, userId)) {
            throw new CustomException("404", "해당 제외 항목을 찾을 수 없습니다.");
        }
        dailyMvpExclusionRepository.deleteByMvpDateAndUserId(date, userId);
        log.info("MVP 제외 해제: date={}, userId={}", date, userId);
    }
}
