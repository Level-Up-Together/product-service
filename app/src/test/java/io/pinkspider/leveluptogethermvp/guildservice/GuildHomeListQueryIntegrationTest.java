package io.pinkspider.leveluptogethermvp.guildservice;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.chatservice.domain.entity.GuildChatMessage;
import io.pinkspider.leveluptogethermvp.chatservice.domain.enums.ChatMessageType;
import io.pinkspider.leveluptogethermvp.chatservice.infrastructure.GuildChatMessageRepository;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.domain.policy.GuildOrderKey;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.DailyMissionInstance;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.Mission;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionExecution;
import io.pinkspider.leveluptogethermvp.missionservice.domain.entity.MissionParticipant;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.DailyMissionInstanceRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionParticipantRepository;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * LUT-853: 길드 홈 목록(최근 활동 → 레벨 순서, '메시지'·'미션' 표시)에 쓰는 JPQL 검증.
 *
 * <p>조건이 모두 JPQL 안에 있어 Mockito 단위 테스트로는 검증되지 않는다. 실제 SQL 실행 결과를 H2 로 확인한다.
 *
 * <ul>
 *   <li>{@code GuildRepository.findPublicGuildOrderKeys} — 공개·활성 길드의 정렬 값(record 로 받는 생성자 식)
 *   <li>{@code GuildChatMessageRepository.findGuildIdsWithMessagesSince} — 방금 대화: 사람이 쓴 종류만, 삭제 제외
 *   <li>{@code MissionExecutionRepository}·{@code
 *       DailyMissionInstanceRepository.findGuildIdsWithActivitySince} — 방금 길드 미션: 시작 또는 직접 완료, 자동
 *       종료 제외
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class GuildHomeListQueryIntegrationTest {

    private static final String PREFIX = "lut853-";

    @Nested
    @DisplayName("findPublicGuildOrderKeys — 추천 길드 정렬 값")
    @Transactional(transactionManager = "guildTransactionManager")
    class PublicGuildOrderKeys {

        @Autowired private GuildRepository guildRepository;

        private Guild saveGuild(String name, GuildVisibility visibility, int level, int point) {
            return guildRepository.save(
                    Guild.builder()
                            .name(PREFIX + name)
                            .visibility(visibility)
                            .masterId(PREFIX + "master-" + name)
                            .categoryId(1L)
                            .currentLevel(level)
                            .totalPoint(point)
                            .build());
        }

        @Test
        @DisplayName("공개·활성 길드만, ID·마스터·레벨·포인트를 그대로 읽는다 (비공개·해체 길드 제외)")
        void readsPublicActiveGuildsOnly() {
            Guild publicGuild = saveGuild("public", GuildVisibility.PUBLIC, 7, 120);
            Guild privateGuild = saveGuild("private", GuildVisibility.PRIVATE, 9, 300);
            Guild dissolved = saveGuild("dissolved", GuildVisibility.PUBLIC, 5, 80);
            dissolved.deactivate();
            guildRepository.saveAndFlush(dissolved);

            List<GuildOrderKey> keys = guildRepository.findPublicGuildOrderKeys();

            assertThat(keys)
                    .contains(
                            new GuildOrderKey(
                                    publicGuild.getId(), PREFIX + "master-public", 7, 120));
            assertThat(keys)
                    .extracting(GuildOrderKey::id)
                    .doesNotContain(privateGuild.getId(), dissolved.getId());
        }
    }

    @Nested
    @DisplayName("findGuildIdsWithMessagesSince — 방금 대화")
    @Transactional(transactionManager = "chatTransactionManager")
    class RecentChat {

        private static final long OLD_ONLY = 9_853_001L;
        private static final long TEXT = 9_853_002L;
        private static final long IMAGE = 9_853_003L;
        private static final long SYSTEM_ONLY = 9_853_004L;
        private static final long DELETED_ONLY = 9_853_005L;
        private static final List<ChatMessageType> CONVERSATION =
                List.of(ChatMessageType.TEXT, ChatMessageType.IMAGE);

        @Autowired private GuildChatMessageRepository chatMessageRepository;

        private GuildChatMessage saveMessage(long guildId, ChatMessageType type) {
            return chatMessageRepository.saveAndFlush(
                    GuildChatMessage.builder()
                            .guildId(guildId)
                            .senderId(type.isSystemMessage() ? null : PREFIX + "sender")
                            .senderNickname(type.isSystemMessage() ? null : "보낸이")
                            .messageType(type)
                            .content(PREFIX + type.name())
                            .build());
        }

        @Test
        @DisplayName("그 시각 이후 사람이 쓴 TEXT·IMAGE 가 있는 길드만 — 이전 메시지·시스템 메시지·삭제된 메시지는 세지 않는다")
        void countsConversationSinceOnly() throws InterruptedException {
            // created_at 은 저장 시각으로 찍힌다 — 기준 시각(since) 앞뒤로 나눠 저장한다
            saveMessage(OLD_ONLY, ChatMessageType.TEXT);
            Thread.sleep(30);
            LocalDateTime since = LocalDateTime.now();
            Thread.sleep(30);
            saveMessage(TEXT, ChatMessageType.TEXT);
            saveMessage(IMAGE, ChatMessageType.IMAGE);
            saveMessage(SYSTEM_ONLY, ChatMessageType.SYSTEM_JOIN);
            saveMessage(SYSTEM_ONLY, ChatMessageType.SYSTEM_MISSION);
            GuildChatMessage deleted = saveMessage(DELETED_ONLY, ChatMessageType.TEXT);
            deleted.delete();
            chatMessageRepository.saveAndFlush(deleted);

            List<Long> guildIds =
                    chatMessageRepository.findGuildIdsWithMessagesSince(since, CONVERSATION);

            assertThat(guildIds)
                    .contains(TEXT, IMAGE)
                    .doesNotContain(OLD_ONLY, SYSTEM_ONLY, DELETED_ONLY);
            assertThat(guildIds).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("findGuildIdsWithActivitySince — 방금 길드 미션")
    @Transactional(transactionManager = "missionTransactionManager")
    class RecentGuildMission {

        @Autowired private MissionRepository missionRepository;
        @Autowired private MissionParticipantRepository participantRepository;
        @Autowired private MissionExecutionRepository executionRepository;
        @Autowired private DailyMissionInstanceRepository instanceRepository;

        private final LocalDateTime now = LocalDateTime.now();
        private final LocalDateTime since = now.minusMinutes(30);
        private final LocalDateTime recent = now.minusMinutes(5);
        private final LocalDateTime old = now.minusHours(3);
        private final LocalDate baseDate = since.toLocalDate();

        private MissionParticipant participantOf(String guildId, boolean pinned) {
            Mission mission =
                    missionRepository.save(
                            Mission.builder()
                                    .title(PREFIX + (guildId == null ? "personal" : guildId))
                                    .status(MissionStatus.IN_PROGRESS)
                                    .visibility(MissionVisibility.PUBLIC)
                                    .type(
                                            guildId == null
                                                    ? MissionType.PERSONAL
                                                    : MissionType.GUILD)
                                    .guildId(guildId)
                                    .creatorId(PREFIX + "creator")
                                    .isPinned(pinned)
                                    .build());
            return participantRepository.save(
                    MissionParticipant.builder()
                            .mission(mission)
                            .userId(PREFIX + "user")
                            .status(ParticipantStatus.IN_PROGRESS)
                            .build());
        }

        private void saveExecution(
                String guildId,
                ExecutionStatus status,
                LocalDateTime startedAt,
                LocalDateTime completedAt,
                boolean autoCompleted) {
            MissionExecution execution =
                    MissionExecution.builder()
                            .participant(participantOf(guildId, false))
                            .executionDate(baseDate)
                            .status(status)
                            .build();
            execution.setStartedAt(startedAt);
            execution.setCompletedAt(completedAt);
            execution.setIsAutoCompleted(autoCompleted);
            executionRepository.saveAndFlush(execution);
        }

        private void saveInstance(
                String guildId,
                LocalDate date,
                ExecutionStatus status,
                LocalDateTime startedAt,
                LocalDateTime completedAt,
                boolean autoCompleted) {
            DailyMissionInstance instance =
                    DailyMissionInstance.createFrom(participantOf(guildId, true), date, 1);
            instance.setStatus(status);
            instance.setStartedAt(startedAt);
            instance.setCompletedAt(completedAt);
            instance.setIsAutoCompleted(autoCompleted);
            instanceRepository.saveAndFlush(instance);
        }

        @Test
        @DisplayName("일반 길드 미션: 방금 시작했거나 직접 완료한 길드만 — 자동 종료·예전 기록·개인 미션은 세지 않는다")
        void regularExecutions() {
            saveExecution("9853101", ExecutionStatus.IN_PROGRESS, recent, null, false);
            saveExecution("9853102", ExecutionStatus.COMPLETED, old, recent, false);
            saveExecution("9853103", ExecutionStatus.COMPLETED, old, recent, true); // 4시간 자동 종료
            saveExecution("9853104", ExecutionStatus.COMPLETED, old, old.plusMinutes(30), false);
            saveExecution(null, ExecutionStatus.IN_PROGRESS, recent, null, false); // 개인 미션

            List<String> guildIds = executionRepository.findGuildIdsWithActivitySince(since);

            assertThat(guildIds)
                    .contains("9853101", "9853102")
                    .doesNotContain("9853103", "9853104")
                    .doesNotContainNull();
            assertThat(guildIds).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("고정 길드 미션: 방금 시작·직접 완료한 길드만, 자정을 넘겨 완료한 어제 인스턴스까지 본다")
        void pinnedInstances() {
            LocalDate fromDate = baseDate.minusDays(1);
            saveInstance("9853201", baseDate, ExecutionStatus.IN_PROGRESS, recent, null, false);
            // 어제 인스턴스를 자정 넘겨 방금 완료
            saveInstance(
                    "9853202",
                    baseDate.minusDays(1),
                    ExecutionStatus.COMPLETED,
                    old,
                    recent,
                    false);
            // 날짜 범위(fromDate) 밖
            saveInstance(
                    "9853203",
                    baseDate.minusDays(3),
                    ExecutionStatus.IN_PROGRESS,
                    recent,
                    null,
                    false);
            saveInstance(
                    "9853204", baseDate, ExecutionStatus.COMPLETED, old, recent, true); // 자동 종료
            saveInstance("9853205", baseDate, ExecutionStatus.IN_PROGRESS, old, null, false);
            saveInstance(null, baseDate, ExecutionStatus.IN_PROGRESS, recent, null, false); // 개인 미션

            List<String> guildIds =
                    instanceRepository.findGuildIdsWithActivitySince(since, fromDate);

            assertThat(guildIds)
                    .contains("9853201", "9853202")
                    .doesNotContain("9853203", "9853204", "9853205")
                    .doesNotContainNull();
            assertThat(guildIds).doesNotHaveDuplicates();
        }
    }
}
