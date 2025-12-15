package io.pinkspider.leveluptogethermvp.userservice.quest.api;

import static com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName;
import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper;
import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.epages.restdocs.apispec.SimpleType;
import io.pinkspider.leveluptogethermvp.config.ControllerTestConfig;
import io.pinkspider.leveluptogethermvp.userservice.quest.application.QuestService;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.QuestProgressResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.QuestResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.dto.UserQuestResponse;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestActionType;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestCategory;
import io.pinkspider.leveluptogethermvp.userservice.quest.domain.enums.QuestType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = QuestController.class,
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private QuestService questService;

    private static final String MOCK_USER_ID = "test-user-123";

    private QuestResponse createMockQuestResponse(Long id, String name, QuestType type) {
        return QuestResponse.builder()
            .id(id)
            .name(name)
            .description(name + " 설명")
            .questType(type)
            .category(QuestCategory.ATTENDANCE)
            .actionType(QuestActionType.CHECK_IN)
            .requiredCount(1)
            .rewardExp(10)
            .rewardPoints(5)
            .build();
    }

    private UserQuestResponse createMockUserQuestResponse(Long id, QuestResponse quest, boolean completed) {
        return UserQuestResponse.builder()
            .id(id)
            .quest(quest)
            .periodKey("2025-01-06")
            .currentCount(completed ? 1 : 0)
            .requiredCount(1)
            .progress(completed ? 100 : 0)
            .isCompleted(completed)
            .completedAt(completed ? LocalDateTime.now() : null)
            .isRewardClaimed(false)
            .canClaimReward(completed)
            .build();
    }

    @Test
    @DisplayName("GET /api/v1/quests : 전체 퀘스트 목록")
    void getAllQuestsTest() throws Exception {
        // given
        List<QuestResponse> responses = List.of(
            createMockQuestResponse(1L, "출석 체크", QuestType.DAILY),
            createMockQuestResponse(2L, "주간 미션 3개 완료", QuestType.WEEKLY)
        );

        when(questService.getAllQuests()).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/quests")
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("퀘스트-01. 전체 퀘스트 목록",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Quest")
                        .description("전체 퀘스트 목록 조회")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value[]").type(JsonFieldType.ARRAY).description("퀘스트 목록"),
                            fieldWithPath("value[].id").type(JsonFieldType.NUMBER).description("퀘스트 ID"),
                            fieldWithPath("value[].name").type(JsonFieldType.STRING).description("퀘스트 이름"),
                            fieldWithPath("value[].description").type(JsonFieldType.STRING).description("퀘스트 설명").optional(),
                            fieldWithPath("value[].quest_type").type(JsonFieldType.STRING).description("퀘스트 타입 (DAILY, WEEKLY)"),
                            fieldWithPath("value[].category").type(JsonFieldType.STRING).description("퀘스트 카테고리"),
                            fieldWithPath("value[].action_type").type(JsonFieldType.STRING).description("액션 타입"),
                            fieldWithPath("value[].required_count").type(JsonFieldType.NUMBER).description("필요 횟수"),
                            fieldWithPath("value[].reward_exp").type(JsonFieldType.NUMBER).description("보상 경험치"),
                            fieldWithPath("value[].reward_points").type(JsonFieldType.NUMBER).description("보상 포인트").optional(),
                            fieldWithPath("value[].icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional()
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/quests/daily : 일일 퀘스트 조회")
    void getDailyQuestsTest() throws Exception {
        // given
        QuestResponse quest = createMockQuestResponse(1L, "출석 체크", QuestType.DAILY);
        UserQuestResponse userQuest = createMockUserQuestResponse(1L, quest, true);

        QuestProgressResponse response = QuestProgressResponse.builder()
            .periodKey("2025-01-06")
            .totalQuests(5)
            .completedQuests(3)
            .claimableQuests(2)
            .progressPercentage(60)
            .quests(List.of(userQuest))
            .build();

        when(questService.getDailyQuests(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/quests/daily")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("퀘스트-02. 일일 퀘스트 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Quest")
                        .description("오늘의 일일 퀘스트 진행 상태 (JWT 토큰 인증 필요)")
                        .responseFields(
                            fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
                            fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                            fieldWithPath("value").type(JsonFieldType.OBJECT).description("퀘스트 진행 정보"),
                            fieldWithPath("value.period_key").type(JsonFieldType.STRING).description("기간 키"),
                            fieldWithPath("value.total_quests").type(JsonFieldType.NUMBER).description("총 퀘스트 수"),
                            fieldWithPath("value.completed_quests").type(JsonFieldType.NUMBER).description("완료된 퀘스트 수"),
                            fieldWithPath("value.claimable_quests").type(JsonFieldType.NUMBER).description("수령 가능한 보상 수"),
                            fieldWithPath("value.progress_percentage").type(JsonFieldType.NUMBER).description("진행률 (%)"),
                            fieldWithPath("value.quests[]").type(JsonFieldType.ARRAY).description("퀘스트 목록"),
                            fieldWithPath("value.quests[].id").type(JsonFieldType.NUMBER).description("사용자 퀘스트 ID"),
                            fieldWithPath("value.quests[].quest").type(JsonFieldType.OBJECT).description("퀘스트 정보"),
                            fieldWithPath("value.quests[].quest.id").type(JsonFieldType.NUMBER).description("퀘스트 ID"),
                            fieldWithPath("value.quests[].quest.name").type(JsonFieldType.STRING).description("퀘스트 이름"),
                            fieldWithPath("value.quests[].quest.description").type(JsonFieldType.STRING).description("퀘스트 설명").optional(),
                            fieldWithPath("value.quests[].quest.quest_type").type(JsonFieldType.STRING).description("퀘스트 타입"),
                            fieldWithPath("value.quests[].quest.category").type(JsonFieldType.STRING).description("카테고리"),
                            fieldWithPath("value.quests[].quest.action_type").type(JsonFieldType.STRING).description("액션 타입"),
                            fieldWithPath("value.quests[].quest.required_count").type(JsonFieldType.NUMBER).description("필요 횟수"),
                            fieldWithPath("value.quests[].quest.reward_exp").type(JsonFieldType.NUMBER).description("보상 경험치"),
                            fieldWithPath("value.quests[].quest.reward_points").type(JsonFieldType.NUMBER).description("보상 포인트").optional(),
                            fieldWithPath("value.quests[].quest.icon_url").type(JsonFieldType.STRING).description("아이콘 URL").optional(),
                            fieldWithPath("value.quests[].period_key").type(JsonFieldType.STRING).description("기간 키"),
                            fieldWithPath("value.quests[].current_count").type(JsonFieldType.NUMBER).description("현재 진행 값"),
                            fieldWithPath("value.quests[].required_count").type(JsonFieldType.NUMBER).description("필요 횟수"),
                            fieldWithPath("value.quests[].progress").type(JsonFieldType.NUMBER).description("진행률 (%)"),
                            fieldWithPath("value.quests[].is_completed").type(JsonFieldType.BOOLEAN).description("완료 여부"),
                            fieldWithPath("value.quests[].completed_at").type(JsonFieldType.STRING).description("완료 일시").optional(),
                            fieldWithPath("value.quests[].is_reward_claimed").type(JsonFieldType.BOOLEAN).description("보상 수령 여부"),
                            fieldWithPath("value.quests[].reward_claimed_at").type(JsonFieldType.STRING).description("보상 수령 일시").optional(),
                            fieldWithPath("value.quests[].can_claim_reward").type(JsonFieldType.BOOLEAN).description("보상 수령 가능 여부")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/quests/weekly : 주간 퀘스트 조회")
    void getWeeklyQuestsTest() throws Exception {
        // given
        QuestResponse quest = createMockQuestResponse(2L, "주간 미션 3개 완료", QuestType.WEEKLY);
        UserQuestResponse userQuest = createMockUserQuestResponse(2L, quest, true);

        QuestProgressResponse response = QuestProgressResponse.builder()
            .periodKey("2025-W01")
            .totalQuests(3)
            .completedQuests(1)
            .claimableQuests(1)
            .progressPercentage(33)
            .quests(List.of(userQuest))
            .build();

        when(questService.getWeeklyQuests(anyString())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/quests/weekly")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("퀘스트-03. 주간 퀘스트 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Quest")
                        .description("이번 주 주간 퀘스트 진행 상태 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/quests/claimable : 수령 가능한 보상 조회")
    void getClaimableQuestsTest() throws Exception {
        // given
        QuestResponse quest = createMockQuestResponse(1L, "출석 체크", QuestType.DAILY);
        List<UserQuestResponse> responses = List.of(
            createMockUserQuestResponse(1L, quest, true)
        );

        when(questService.getClaimableQuests(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.get("/api/v1/quests/claimable")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("퀘스트-04. 수령 가능한 보상 조회",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Quest")
                        .description("수령 가능한 퀘스트 보상 목록 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/quests/{userQuestId}/claim : 보상 수령")
    void claimRewardTest() throws Exception {
        // given
        QuestResponse quest = createMockQuestResponse(1L, "출석 체크", QuestType.DAILY);
        UserQuestResponse response = UserQuestResponse.builder()
            .id(1L)
            .quest(quest)
            .periodKey("2025-01-06")
            .currentCount(1)
            .requiredCount(1)
            .progress(100)
            .isCompleted(true)
            .completedAt(LocalDateTime.now())
            .isRewardClaimed(true)
            .rewardClaimedAt(LocalDateTime.now())
            .canClaimReward(false)
            .build();

        when(questService.claimReward(anyString(), anyLong())).thenReturn(response);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/quests/{userQuestId}/claim", 1L)
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("퀘스트-05. 보상 수령",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Quest")
                        .description("완료된 퀘스트 보상 수령 (JWT 토큰 인증 필요)")
                        .pathParameters(
                            parameterWithName("userQuestId").type(SimpleType.NUMBER).description("사용자 퀘스트 ID")
                        )
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/quests/claim-all : 보상 일괄 수령")
    void claimAllRewardsTest() throws Exception {
        // given
        QuestResponse quest1 = createMockQuestResponse(1L, "출석 체크", QuestType.DAILY);
        QuestResponse quest2 = createMockQuestResponse(2L, "미션 완료", QuestType.DAILY);

        List<UserQuestResponse> responses = List.of(
            UserQuestResponse.builder()
                .id(1L)
                .quest(quest1)
                .periodKey("2025-01-06")
                .isCompleted(true)
                .isRewardClaimed(true)
                .canClaimReward(false)
                .build(),
            UserQuestResponse.builder()
                .id(2L)
                .quest(quest2)
                .periodKey("2025-01-06")
                .isCompleted(true)
                .isRewardClaimed(true)
                .canClaimReward(false)
                .build()
        );

        when(questService.claimAllRewards(anyString())).thenReturn(responses);

        // when
        ResultActions resultActions = mockMvc.perform(
            RestDocumentationRequestBuilders.post("/api/v1/quests/claim-all")
                .with(user(MOCK_USER_ID))
                .contentType(MediaType.APPLICATION_JSON)
        ).andDo(
            MockMvcRestDocumentationWrapper.document("퀘스트-06. 보상 일괄 수령",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                resource(
                    ResourceSnippetParameters.builder()
                        .tag("Quest")
                        .description("수령 가능한 모든 퀘스트 보상 일괄 수령 (JWT 토큰 인증 필요)")
                        .build()
                )
            )
        );

        // then
        resultActions.andExpect(MockMvcResultMatchers.status().isOk());
    }
}
