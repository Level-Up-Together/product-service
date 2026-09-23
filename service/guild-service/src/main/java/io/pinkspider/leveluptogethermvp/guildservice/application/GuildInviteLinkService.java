package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteJoinResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInviteLinkResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildInvitePreviewResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildMemberRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 길드 초대 링크 서비스 (LUT-519).
 *
 * <p>길드당 1개·불변인 초대 코드로 (1) 길드원의 링크 조회/지연 발급, (2) 비로그인 미리보기, (3) 인증 합류를
 * 처리한다. 합류는 승인 없이 즉시 이뤄지며(초대=승인) 공통 멤버 추가 경로({@link
 * GuildMemberService#addActiveMember})를 타 업적·길드 고정 미션 자동 참여(LUT-518)로 이어진다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(transactionManager = "guildTransactionManager", readOnly = true)
public class GuildInviteLinkService {

    private final GuildRepository guildRepository;
    private final GuildMemberRepository guildMemberRepository;
    private final GuildHelper guildHelper;
    private final GuildMemberService guildMemberService;
    private final GuildInviteCodeGenerator inviteCodeGenerator;

    /** 길드원 전용 — 초대 링크 코드 조회(없으면 지연 발급). 코드는 길드가 삭제될 때까지 불변. */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildInviteLinkResponse getOrCreateInviteLink(Long guildId, String userId) {
        Guild guild = guildHelper.findActiveGuildById(guildId);
        if (!guildMemberRepository.isActiveMember(guildId, userId)) {
            throw new IllegalStateException("길드원만 초대 링크를 사용할 수 있습니다.");
        }

        String code = guild.getInviteCode();
        if (code == null || code.isBlank()) {
            code = inviteCodeGenerator.generateUnique();
            guild.setInviteCode(code);
            guildRepository.save(guild);
            log.info("길드 초대 코드 지연 발급: guildId={}", guildId);
        }
        return GuildInviteLinkResponse.builder().code(code).invitePath("/guild/invite/" + code).build();
    }

    /** 비로그인 허용 — 초대 링크 미리보기. 잘못된/삭제된 코드는 예외가 아니라 valid=false 로 응답. */
    public GuildInvitePreviewResponse getPreview(String code, String userId) {
        Guild guild =
            guildRepository
                .findByInviteCode(code)
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .orElse(null);
        if (guild == null) {
            return GuildInvitePreviewResponse.invalid();
        }

        int memberCount = (int) guildMemberRepository.countActiveMembers(guild.getId());
        boolean alreadyMember =
            userId != null && guildMemberRepository.isActiveMember(guild.getId(), userId);
        boolean full = memberCount >= guild.getMaxMembers();
        boolean joinable = alreadyMember || !full;
        String reason = (!alreadyMember && full) ? "FULL" : null;

        GuildResponse guildResponse = guildHelper.buildGuildResponseWithCategory(guild, memberCount);
        return GuildInvitePreviewResponse.builder()
            .valid(true)
            .joinable(joinable)
            .alreadyMember(alreadyMember)
            .reason(reason)
            .guild(guildResponse)
            .build();
    }

    /** 인증 — 초대 링크로 합류 (멱등: 이미 멤버면 그대로 성공). 비공개 길드도 승인 없이 즉시 합류. */
    @Transactional(transactionManager = "guildTransactionManager")
    public GuildInviteJoinResponse join(String code, String userId) {
        Guild guild =
            guildRepository
                .findByInviteCode(code)
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 초대 링크입니다."));

        guildMemberService.addActiveMember(guild, userId);
        return GuildInviteJoinResponse.builder().guildId(guild.getId()).build();
    }
}
