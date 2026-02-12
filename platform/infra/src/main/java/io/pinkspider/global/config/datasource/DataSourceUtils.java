package io.pinkspider.global.config.datasource;

/**
 * DataSource 관련 유틸리티 클래스
 */
public final class DataSourceUtils {

    private DataSourceUtils() {
        // 유틸리티 클래스는 인스턴스화 불가
    }

    /**
     * JDBC URL에서 localhost 포트를 동적 포트로 교체합니다.
     * 예: jdbc:postgresql://localhost:15432/user_db -> jdbc:postgresql://localhost:54321/user_db
     *
     * @param jdbcUrl 원본 JDBC URL
     * @param newPort 새로운 포트 번호
     * @return 포트가 교체된 JDBC URL
     */
    public static String replacePortInJdbcUrl(String jdbcUrl, int newPort) {
        return jdbcUrl.replaceAll("localhost:\\d+", "localhost:" + newPort);
    }
}
