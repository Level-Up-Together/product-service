package io.pinkspider.leveluptogethermvp.missionservice.application;

import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionCompleteResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.RecommendedMissionStartResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LUT-803: 체크(SIMPLE) 추천 미션 원탭 완료 — 내 미션 생성 + 오늘 수행 시작 + 완료를 한 요청에서 한다.
 *
 * <p>체크 미션은 "수행 중" 을 거치지 않는다. 원탭 시작({@code /start}, LUT-782)은 미션을 만들면서 수행을 시작해, 화면의 확인("다 했으면 눌러
 * 주세요")이 떠 있는 동안 체크 미션이 수행 중으로 잡히고 타이머가 돌았다. 그래서 화면은 확인을 누른 뒤에 이 완료를 한 번만 부른다 — "나중에 할게요" 면 아무것도
 * 부르지 않아 미션이 생기지 않는다.
 *
 * <p><b>이 클래스는 트랜잭션을 열지 않는다.</b> 완료는 Saga 이고 각 step 이 {@code REQUIRES_NEW} 로 따로 커밋하므로, 만든 미션과 시작한
 * 수행이 <b>먼저 커밋돼 있어야</b> Saga 가 읽을 수 있다. 그래서 순서는 ① 생성 + 시작(한 트랜잭션, 커밋) → ② 완료 Saga 이고, ②가 실패하면 ①을
 * 되돌린다(수행 취소 + 미션 삭제) — 수행 중인 미션이 남지 않는다. ①과 ② 사이(한 요청 안, 수십 ms)에는 수행 중 상태가 잠깐 커밋돼 있다. 기존 체크 미션
 * 흐름(시작 API → 완료 API)과 같은 과정이고 시작·종료 이벤트도 똑같이 나간다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendedMissionCompleteService {

    private final RecommendedMissionService recommendedMissionService;
    private final MissionExecutionService executionService;
    private final MissionService missionService;

    /**
     * @throws
     *     io.pinkspider.leveluptogethermvp.missionservice.api.exception.RecommendedMissionException
     *     없거나 꺼진 추천(404 050501) · 체크 미션이 아님(400 050503)
     * @throws IllegalStateException 진행 중인 미션이 있음 등 — 기존 시작·완료와 같은 오류
     */
    public RecommendedMissionCompleteResponse complete(
            String userId, Long recommendedId, String locale) {
        recommendedMissionService.requireActiveSimple(recommendedId);

        // ① 생성 + 시작 — 실패하면(진행 중인 미션 있음·생성 한도 등) 아무것도 남지 않는다
        RecommendedMissionStartResponse started =
                recommendedMissionService.start(userId, recommendedId, locale);
        Long missionId = started.mission().getId();
        LocalDate executionDate = started.execution().getExecutionDate();

        // ② 완료 — 체크 일일 한도·경험치는 기존 완료와 같은 Saga 가 판정한다. 피드는 만들지 않는다(공개범위 미지정 = PRIVATE)
        MissionExecutionResponse completed;
        try {
            completed =
                    executionService.completeExecution(
                            missionId, userId, executionDate, null, (FeedVisibility) null);
        } catch (RuntimeException e) {
            log.warn(
                    "추천 미션 원탭 완료 실패 — 만든 미션을 되돌린다: recommendedId={}, missionId={}, userId={}",
                    recommendedId,
                    missionId,
                    userId,
                    e);
            discard(missionId, userId, executionDate);
            throw e;
        }
        return new RecommendedMissionCompleteResponse(started.mission(), completed);
    }

    /** 완료에 실패한 미션을 없앤다 — 수행 중으로 남지 않게 수행을 취소하고 미션을 지운다. 되돌리기가 실패해도 원래 오류를 가리지 않는다 */
    private void discard(Long missionId, String userId, LocalDate executionDate) {
        try {
            executionService.skipExecution(missionId, userId, executionDate);
        } catch (RuntimeException e) {
            log.error("추천 미션 원탭 완료 되돌리기 — 수행 취소 실패: missionId={}, userId={}", missionId, userId, e);
        }
        try {
            missionService.deleteMission(missionId, userId);
        } catch (RuntimeException e) {
            log.error("추천 미션 원탭 완료 되돌리기 — 미션 삭제 실패: missionId={}, userId={}", missionId, userId, e);
        }
    }
}
