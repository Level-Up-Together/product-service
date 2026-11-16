package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

/** LUT-790: 유저 닉네임과 탈퇴 여부 (user_db) — 탈퇴 유저의 닉네임은 익명화된 값 */
public record UserNickname(String nickname, boolean withdrawn) {}
