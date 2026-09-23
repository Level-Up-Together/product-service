package io.pinkspider.leveluptogethermvp.guildservice.application;

import static io.pinkspider.global.test.TestReflectionUtils.setId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteJoinResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteLinkResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitePreviewResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuildInviteLinkServiceTest {

    @Mock private GuildRepository guildRepository;
    @Mock private GuildMemberRepository guildMemberRepository;
    @Mock private GuildHelper guildHelper;
    @Mock private GuildMemberService guildMemberService;
    @Mock private GuildInviteCodeGenerator inviteCodeGenerator;

    @InjectMocks private GuildInviteLinkService inviteLinkService;

    private String testUserId;
    private Guild testGuild;

    @BeforeEach
    void setUp() {
        testUserId = "test-user-id";
        testGuild =
            Guild.builder()
                .name("테스트 길드")
                .visibility(GuildVisibility.PRIVATE)
                .joinType(GuildJoinType.APPROVAL_REQUIRED)
                .masterId("master-id")
                .maxMembers(50)
                .categoryId(1L)
                .isActive(true)
                .build();
        setId(testGuild, 1L);
    }

    @Nested
    @DisplayName("초대 링크 조회/발급 테스트")
    class GetOrCreateInviteLinkTest {

        @Test
        @DisplayName("이미 코드가 있으면 그대로 반환하고 재발급하지 않는다")
        void returnsExistingCode() {
            // given
            testGuild.setInviteCode("EXISTING123");
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(true);

            // when
            GuildInviteLinkResponse response = inviteLinkService.getOrCreateInviteLink(1L, testUserId);

            // then
            assertThat(response.code()).isEqualTo("EXISTING123");
            assertThat(response.invitePath()).isEqualTo("/guild/invite/EXISTING123");
            verify(inviteCodeGenerator, never()).generateUnique();
            verify(guildRepository, never()).save(any(Guild.class));
        }

        @Test
        @DisplayName("코드가 없으면 지연 발급하고 저장한다")
        void generatesCodeWhenMissing() {
            // given (inviteCode == null)
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(true);
            when(inviteCodeGenerator.generateUnique()).thenReturn("NEWCODE123");

            // when
            GuildInviteLinkResponse response = inviteLinkService.getOrCreateInviteLink(1L, testUserId);

            // then
            assertThat(response.code()).isEqualTo("NEWCODE123");
            assertThat(testGuild.getInviteCode()).isEqualTo("NEWCODE123");
            verify(guildRepository).save(testGuild);
        }

        @Test
        @DisplayName("길드원이 아니면 초대 링크를 사용할 수 없다")
        void nonMemberCannotAccess() {
            // given
            when(guildHelper.findActiveGuildById(1L)).thenReturn(testGuild);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> inviteLinkService.getOrCreateInviteLink(1L, testUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("길드원");
            verify(inviteCodeGenerator, never()).generateUnique();
        }
    }

    @Nested
    @DisplayName("초대 링크 미리보기 테스트")
    class GetPreviewTest {

        @Test
        @DisplayName("유효한 코드면 길드 정보와 합류 가능 여부를 반환한다")
        void validCode() {
            // given
            GuildResponse guildResponse = GuildResponse.builder().name("테스트 길드").build();
            when(guildRepository.findByInviteCode("CODE")).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), anyInt()))
                .thenReturn(guildResponse);

            // when
            GuildInvitePreviewResponse preview = inviteLinkService.getPreview("CODE", testUserId);

            // then
            assertThat(preview.valid()).isTrue();
            assertThat(preview.joinable()).isTrue();
            assertThat(preview.alreadyMember()).isFalse();
            assertThat(preview.reason()).isNull();
            assertThat(preview.guild()).isEqualTo(guildResponse);
        }

        @Test
        @DisplayName("존재하지 않는 코드면 valid=false 로 응답한다")
        void invalidCode() {
            // given
            when(guildRepository.findByInviteCode("BAD")).thenReturn(Optional.empty());

            // when
            GuildInvitePreviewResponse preview = inviteLinkService.getPreview("BAD", testUserId);

            // then
            assertThat(preview.valid()).isFalse();
            assertThat(preview.joinable()).isFalse();
            assertThat(preview.reason()).isEqualTo("INVALID");
        }

        @Test
        @DisplayName("정원이 가득 차면 joinable=false, reason=FULL")
        void fullGuild() {
            // given
            testGuild.setMaxMembers(10);
            when(guildRepository.findByInviteCode("CODE")).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(false);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), anyInt()))
                .thenReturn(GuildResponse.builder().build());

            // when
            GuildInvitePreviewResponse preview = inviteLinkService.getPreview("CODE", testUserId);

            // then
            assertThat(preview.valid()).isTrue();
            assertThat(preview.joinable()).isFalse();
            assertThat(preview.reason()).isEqualTo("FULL");
        }

        @Test
        @DisplayName("이미 멤버면 정원이 가득 차도 joinable=true(길드로 이동 가능)")
        void alreadyMemberIsJoinable() {
            // given
            testGuild.setMaxMembers(10);
            when(guildRepository.findByInviteCode("CODE")).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildMemberRepository.isActiveMember(1L, testUserId)).thenReturn(true);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), anyInt()))
                .thenReturn(GuildResponse.builder().build());

            // when
            GuildInvitePreviewResponse preview = inviteLinkService.getPreview("CODE", testUserId);

            // then
            assertThat(preview.alreadyMember()).isTrue();
            assertThat(preview.joinable()).isTrue();
            assertThat(preview.reason()).isNull();
        }

        @Test
        @DisplayName("비로그인(userId=null)이면 멤버 조회 없이 정원만으로 판정한다")
        void anonymousViewer() {
            // given
            when(guildRepository.findByInviteCode("CODE")).thenReturn(Optional.of(testGuild));
            when(guildMemberRepository.countActiveMembers(1L)).thenReturn(10L);
            when(guildHelper.buildGuildResponseWithCategory(any(Guild.class), anyInt()))
                .thenReturn(GuildResponse.builder().build());

            // when
            GuildInvitePreviewResponse preview = inviteLinkService.getPreview("CODE", null);

            // then
            assertThat(preview.alreadyMember()).isFalse();
            assertThat(preview.joinable()).isTrue();
            verify(guildMemberRepository, never()).isActiveMember(any(), any());
        }
    }

    @Nested
    @DisplayName("초대 링크 합류 테스트")
    class JoinTest {

        @Test
        @DisplayName("유효한 코드로 합류하면 공통 멤버 추가를 호출하고 guildId 를 반환한다")
        void joinSuccess() {
            // given
            when(guildRepository.findByInviteCode("CODE")).thenReturn(Optional.of(testGuild));

            // when
            GuildInviteJoinResponse response = inviteLinkService.join("CODE", testUserId);

            // then
            assertThat(response.guildId()).isEqualTo(1L);
            verify(guildMemberService).addActiveMember(testGuild, testUserId);
        }

        @Test
        @DisplayName("존재하지 않는 코드로 합류하면 예외")
        void joinInvalidCode() {
            // given
            when(guildRepository.findByInviteCode("BAD")).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> inviteLinkService.join("BAD", testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은");
            verify(guildMemberService, never()).addActiveMember(any(), any());
        }
    }
}
