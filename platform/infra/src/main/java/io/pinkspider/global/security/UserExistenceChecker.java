package io.pinkspider.global.security;

/**
 * 사용자 존재 여부 확인 인터페이스.
 * MSA 전환 시 각 서비스가 독립적으로 구현할 수 있도록 global에 정의.
 */
public interface UserExistenceChecker {

    boolean existsById(String userId);
}
