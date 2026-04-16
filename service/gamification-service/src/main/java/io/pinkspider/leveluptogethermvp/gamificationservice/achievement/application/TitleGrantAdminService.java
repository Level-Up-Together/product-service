package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminPageResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminRequest;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.TitleGrantAdminResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Title;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.TitleRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserTitleRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.infrastructure.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class TitleGrantAdminService {

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;
    private final UserRepository userRepository;

    @Transactional(transactionManager = "gamificationTransactionManager")
    public TitleGrantAdminResponse grantTitle(TitleGrantAdminRequest request, Long adminId) {
        String userId = request.getUserId();
        Long titleId = request.getTitleId();

        // 칭호 존재 확인
        Title title = titleRepository.findById(titleId)
            .orElseThrow(() -> new CustomException("404", "칭호를 찾을 수 없습니다: " + titleId));

        // 이미 보유 여부 확인
        if (userTitleRepository.existsByUserIdAndTitleId(userId, titleId)) {
            throw new CustomException("400", "이미 보유한 칭호입니다.");
        }

        // 칭호 부여
        UserTitle userTitle = UserTitle.builder()
            .userId(userId)
            .title(title)
            .acquiredAt(LocalDateTime.now())
            .grantedBy(adminId)
            .grantReason(request.getReason())
            .build();

        UserTitle saved = userTitleRepository.save(userTitle);
        log.info("관리자 칭호 부여: userId={}, titleId={}, adminId={}", userId, titleId, adminId);

        String nickname = getUserNickname(userId);
        return TitleGrantAdminResponse.from(saved, nickname);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void revokeTitle(Long userTitleId, Long adminId) {
        UserTitle userTitle = userTitleRepository.findById(userTitleId)
            .orElseThrow(() -> new CustomException("404", "부여된 칭호를 찾을 수 없습니다: " + userTitleId));

        log.info("관리자 칭호 회수: userTitleId={}, userId={}, titleName={}, adminId={}",
            userTitleId, userTitle.getUserId(), userTitle.getTitle().getName(), adminId);

        userTitleRepository.delete(userTitle);
    }

    public TitleGrantAdminPageResponse getGrantHistory(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "acquiredAt"));
        Page<UserTitle> grantPage = userTitleRepository.findGrantHistory(keyword, pageable);

        // 사용자 닉네임 배치 조회
        List<String> userIds = grantPage.getContent().stream()
            .map(UserTitle::getUserId)
            .distinct()
            .toList();
        Map<String, String> nicknameMap = getUserNicknameMap(userIds);

        Page<TitleGrantAdminResponse> responsePage = grantPage.map(
            ut -> TitleGrantAdminResponse.from(ut, nicknameMap.get(ut.getUserId()))
        );

        return TitleGrantAdminPageResponse.from(responsePage);
    }

    private String getUserNickname(String userId) {
        return userRepository.findById(userId)
            .map(Users::getNickname)
            .orElse(null);
    }

    private Map<String, String> getUserNicknameMap(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(Users::getId, Users::getNickname, (a, b) -> a));
    }
}
