package io.pinkspider.leveluptogethermvp.userservice.terms.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.userservice.terms.infrastructure.TermVersionRepository;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TermVersionService {

    private final TermVersionRepository termVersionRepository;

    public TermVersion findById(Long termVersionId) {
        return termVersionRepository.findById(termVersionId)
            .orElseThrow(() -> new CustomException("", ""));
    }

    public void save(TermVersion termVersion) {
        termVersionRepository.save(termVersion);
    }
}
