package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.UserTermAgreementsRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTermAgreementsService {

    private final UserTermAgreementsRepository userTermAgreementsRepository;

    public UserTermAgreement findAllByUserIdAndTermVersionId(String userId, Long termVersionId) {
        return userTermAgreementsRepository.findAllByUserIdAndTermVersionId(userId, termVersionId)
            .orElse(null);
    }

    public void save(UserTermAgreement userTermAgreement) {
        userTermAgreementsRepository.save(userTermAgreement);
    }
}
