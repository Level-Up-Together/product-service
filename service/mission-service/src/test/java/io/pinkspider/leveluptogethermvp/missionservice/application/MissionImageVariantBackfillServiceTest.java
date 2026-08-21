package io.pinkspider.leveluptogethermvp.missionservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionImageVariantBackfillResultResponse;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionImageRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionImageVariantBackfillServiceTest {

    @Mock
    private MissionExecutionImageRepository missionExecutionImageRepository;

    @Mock
    private MissionExecutionRepository missionExecutionRepository;

    @Mock
    private MissionImageStorageService missionImageStorageService;

    @InjectMocks
    private MissionImageVariantBackfillService backfillService;

    private static final String URL_A = "/uploads/missions/u1/1/a.jpg";
    private static final String URL_B = "/uploads/missions/u1/1/b.jpg";
    private static final String URL_C = "/uploads/missions/u2/2/c.jpg";

    @Test
    @DisplayName("두 소스(다중 이미지+대표 이미지)를 합산하고 중복은 1회만 처리한다")
    void backfill_mergesAndDeduplicatesSources() {
        when(missionExecutionImageRepository.findDistinctImageUrls())
            .thenReturn(List.of(URL_A, URL_B));
        // 대표 이미지에는 URL_A 가 중복으로 존재 + 레거시 URL_C
        when(missionExecutionRepository.findDistinctImageUrls())
            .thenReturn(List.of(URL_A, URL_C));
        when(missionImageStorageService.backfillVariants(URL_A)).thenReturn(2);
        when(missionImageStorageService.backfillVariants(URL_B)).thenReturn(0);
        when(missionImageStorageService.backfillVariants(URL_C)).thenReturn(1);

        MissionImageVariantBackfillResultResponse result = backfillService.backfillVariants(null);

        assertThat(result.totalUrls()).isEqualTo(3);
        assertThat(result.scanned()).isEqualTo(3);
        assertThat(result.variantsCreated()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(missionImageStorageService, times(1)).backfillVariants(URL_A);
    }

    @Test
    @DisplayName("개별 URL 실패는 집계만 하고 나머지는 계속 처리한다")
    void backfill_countsFailuresAndContinues() {
        when(missionExecutionImageRepository.findDistinctImageUrls())
            .thenReturn(List.of(URL_A, URL_B));
        when(missionExecutionRepository.findDistinctImageUrls()).thenReturn(List.of());
        when(missionImageStorageService.backfillVariants(URL_A))
            .thenThrow(new IllegalStateException("원본 파일 없음"));
        when(missionImageStorageService.backfillVariants(URL_B)).thenReturn(2);

        MissionImageVariantBackfillResultResponse result = backfillService.backfillVariants(null);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.variantsCreated()).isEqualTo(2);
        assertThat(result.scanned()).isEqualTo(2);
    }

    @Test
    @DisplayName("limit 지정 시 해당 개수까지만 처리한다 (분할 실행)")
    void backfill_respectsLimit() {
        when(missionExecutionImageRepository.findDistinctImageUrls())
            .thenReturn(List.of(URL_A, URL_B, URL_C));
        when(missionExecutionRepository.findDistinctImageUrls()).thenReturn(List.of());
        when(missionImageStorageService.backfillVariants(URL_A)).thenReturn(2);
        when(missionImageStorageService.backfillVariants(URL_B)).thenReturn(2);

        MissionImageVariantBackfillResultResponse result = backfillService.backfillVariants(2);

        assertThat(result.totalUrls()).isEqualTo(3);
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.variantsCreated()).isEqualTo(4);
        verify(missionImageStorageService, times(0)).backfillVariants(URL_C);
    }
}
