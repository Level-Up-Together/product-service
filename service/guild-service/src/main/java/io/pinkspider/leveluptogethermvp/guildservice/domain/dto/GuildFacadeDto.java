package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

/**
 * 외부 서비스용 길드 조회 DTO 모음
 * GuildQueryFacadeService에서 반환하는 record 타입
 */
public class GuildFacadeDto {

    /**
     * 기본 길드 정보 (랭킹, 채팅 등에서 사용)
     */
    public record GuildBasicInfo(
        Long id,
        String name,
        String imageUrl,
        Integer currentLevel
    ) {}

    /**
     * 길드 + 멤버 수 (랭킹, 홈 MVP에서 사용)
     */
    public record GuildWithMemberCount(
        Long id,
        String name,
        String imageUrl,
        Integer currentLevel,
        int memberCount
    ) {}

    /**
     * 사용자 길드 멤버십 (마이페이지, 시즌랭킹, 업적에서 사용)
     */
    public record GuildMembershipInfo(
        Long guildId,
        String guildName,
        String guildImageUrl,
        Integer guildLevel,
        boolean isMaster,
        boolean isSubMaster
    ) {}

    /**
     * 사용자 길드 상세 (Admin Internal API에서 사용)
     */
    public record UserGuildAdminInfo(
        Long guildId,
        String guildName,
        String guildImageUrl,
        Integer guildLevel,
        String role,
        java.time.LocalDateTime joinedAt,
        Integer memberCount,
        Integer maxMembers
    ) {}

    /**
     * 권한 체크 결과 (미션 삭제 권한에서 사용)
     */
    public record GuildPermissionCheck(
        boolean isActiveMember,
        boolean isMaster,
        boolean isSubMaster
    ) {
        public boolean isMasterOrSubMaster() {
            return isMaster || isSubMaster;
        }
    }
}
