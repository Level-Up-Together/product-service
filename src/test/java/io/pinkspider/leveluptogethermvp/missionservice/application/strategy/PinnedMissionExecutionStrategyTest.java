package io.pinkspider.leveluptogethermvp.missionservice.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.missionservice.application.DailyMissionInstanceService;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.DailyMissionInstanceResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.dto.MissionExecutionResponse;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class PinnedMissionExecutionStrategyTest {

    @Mock
    private DailyMissionInstanceService dailyMissionInstanceService;

    @InjectMocks
    private PinnedMissionExecutionStrategy strategy;

    private String testUserId;
    private Long testMissionId;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-123";
        testMissionId = 1L;
        testDate = LocalDate.now();
    }

    private DailyMissionInstanceResponse createMockResponse(ExecutionStatus status) {
        DailyMissionInstanceResponse mockResponse = mock(DailyMissionInstanceResponse.class);
        // MissionExecutionResponse.fromDailyInstance()에서 호출하는 필드만 stub
        when(mockResponse.getId()).thenReturn(1L);
        when(mockResponse.getParticipantId()).thenReturn(1L);
        when(mockResponse.getMissionId()).thenReturn(testMissionId);
        when(mockResponse.getMissionTitle()).thenReturn("고정 미션");
        when(mockResponse.getMissionCategoryName()).thenReturn("건강");
        when(mockResponse.getUserId()).thenReturn(testUserId);
        when(mockResponse.getInstanceDate()).thenReturn(testDate);
        when(mockResponse.getStatus()).thenReturn(status);
        when(mockResponse.getStartedAt()).thenReturn(LocalDateTime.now());
        when(mockResponse.getCompletedAt()).thenReturn(status == ExecutionStatus.COMPLETED ? LocalDateTime.now() : null);
        when(mockResponse.getDurationMinutes()).thenReturn(status == ExecutionStatus.COMPLETED ? 30 : null);
        when(mockResponse.getExpEarned()).thenReturn(status == ExecutionStatus.COMPLETED ? 50 : 0);
        when(mockResponse.getNote()).thenReturn(null);
        when(mockResponse.getImageUrl()).thenReturn(null);
        when(mockResponse.getFeedId()).thenReturn(null);
        when(mockResponse.getCreatedAt()).thenReturn(LocalDateTime.now());
        return mockResponse;
    }

    @Test
    @DisplayName("startExecution은 DailyMissionInstanceService로 위임한다")
    void startExecution_delegatesToDailyMissionInstanceService() {
        // given
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.IN_PROGRESS);

        when(dailyMissionInstanceService.startInstanceByMission(testMissionId, testUserId, testDate))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.startExecution(testMissionId, testUserId, testDate);

        // then
        verify(dailyMissionInstanceService).startInstanceByMission(testMissionId, testUserId, testDate);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMissionId()).isEqualTo(testMissionId);
        assertThat(response.getUserId()).isEqualTo(testUserId);
    }

    @Test
    @DisplayName("skipExecution은 DailyMissionInstanceService로 위임한다")
    void skipExecution_delegatesToDailyMissionInstanceService() {
        // given
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.PENDING);

        when(dailyMissionInstanceService.skipInstanceByMission(testMissionId, testUserId, testDate))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.skipExecution(testMissionId, testUserId, testDate);

        // then
        verify(dailyMissionInstanceService).skipInstanceByMission(testMissionId, testUserId, testDate);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMissionId()).isEqualTo(testMissionId);
    }

    @Test
    @DisplayName("completeExecution은 DailyMissionInstanceService로 위임한다")
    void completeExecution_delegatesToDailyMissionInstanceService() {
        // given
        String note = "완료!";
        boolean shareToFeed = true;
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.COMPLETED);
        when(mockResponse.getNote()).thenReturn(note);

        when(dailyMissionInstanceService.completeInstanceByMission(testMissionId, testUserId, testDate, note, shareToFeed))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.completeExecution(testMissionId, testUserId, testDate, note, shareToFeed);

        // then
        verify(dailyMissionInstanceService).completeInstanceByMission(testMissionId, testUserId, testDate, note, shareToFeed);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    @DisplayName("uploadExecutionImage는 DailyMissionInstanceService로 위임한다")
    void uploadExecutionImage_delegatesToDailyMissionInstanceService() {
        // given
        MultipartFile mockFile = mock(MultipartFile.class);
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.COMPLETED);
        when(mockResponse.getImageUrl()).thenReturn("https://example.com/image.jpg");

        when(dailyMissionInstanceService.uploadImageByMission(testMissionId, testUserId, testDate, mockFile))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.uploadExecutionImage(testMissionId, testUserId, testDate, mockFile);

        // then
        verify(dailyMissionInstanceService).uploadImageByMission(testMissionId, testUserId, testDate, mockFile);
        assertThat(response).isNotNull();
        assertThat(response.getImageUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("deleteExecutionImage는 DailyMissionInstanceService로 위임한다")
    void deleteExecutionImage_delegatesToDailyMissionInstanceService() {
        // given
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.COMPLETED);

        when(dailyMissionInstanceService.deleteImageByMission(testMissionId, testUserId, testDate))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.deleteExecutionImage(testMissionId, testUserId, testDate);

        // then
        verify(dailyMissionInstanceService).deleteImageByMission(testMissionId, testUserId, testDate);
        assertThat(response).isNotNull();
        assertThat(response.getImageUrl()).isNull();
    }

    @Test
    @DisplayName("shareExecutionToFeed는 DailyMissionInstanceService로 위임한다")
    void shareExecutionToFeed_delegatesToDailyMissionInstanceService() {
        // given
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.COMPLETED);
        when(mockResponse.getFeedId()).thenReturn(100L);

        when(dailyMissionInstanceService.shareToFeedByMission(testMissionId, testUserId, testDate))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.shareExecutionToFeed(testMissionId, testUserId, testDate);

        // then
        verify(dailyMissionInstanceService).shareToFeedByMission(testMissionId, testUserId, testDate);
        assertThat(response).isNotNull();
        assertThat(response.getFeedId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getExecutionByDate는 DailyMissionInstanceService로 위임한다")
    void getExecutionByDate_delegatesToDailyMissionInstanceService() {
        // given
        DailyMissionInstanceResponse mockResponse = createMockResponse(ExecutionStatus.PENDING);

        when(dailyMissionInstanceService.getInstanceByMission(testMissionId, testUserId, testDate))
            .thenReturn(mockResponse);

        // when
        MissionExecutionResponse response = strategy.getExecutionByDate(testMissionId, testUserId, testDate);

        // then
        verify(dailyMissionInstanceService).getInstanceByMission(testMissionId, testUserId, testDate);
        assertThat(response).isNotNull();
        assertThat(response.getMissionId()).isEqualTo(testMissionId);
        assertThat(response.getExecutionDate()).isEqualTo(testDate);
    }
}
