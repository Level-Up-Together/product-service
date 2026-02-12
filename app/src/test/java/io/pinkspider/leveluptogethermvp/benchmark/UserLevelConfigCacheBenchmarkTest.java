package io.pinkspider.leveluptogethermvp.benchmark;

import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.infrastructure.UserLevelConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * UserLevelConfig 조회 성능 벤치마크 테스트
 *
 * 실행 방법:
 * ./gradlew test --tests "*UserLevelConfigCacheBenchmarkTest" -Dspring.profiles.active=local
 *
 * 주의: 실제 DB/Redis 연결이 필요합니다. @Disabled를 제거하고 실행하세요.
 */
@SpringBootTest
@ActiveProfiles("local")
@Disabled("벤치마크 테스트는 수동으로 실행")
class UserLevelConfigCacheBenchmarkTest {

    @Autowired
    private UserLevelConfigRepository userLevelConfigRepository;

    @Autowired
    private UserLevelConfigCacheService userLevelConfigCacheService;

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int CONCURRENT_THREADS = 10;

    @Test
    @DisplayName("단일 스레드 - DB 직접 조회 vs Redis 캐시 조회 성능 비교")
    void benchmark_singleThread_dbVsCache() {
        System.out.println("\n========================================");
        System.out.println("단일 스레드 벤치마크 테스트");
        System.out.println("========================================\n");

        // Warmup
        System.out.println("워밍업 중...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            userLevelConfigRepository.findAllByOrderByLevelAsc();
            userLevelConfigCacheService.getAllLevelConfigs();
        }

        // DB 직접 조회 벤치마크
        System.out.println("\n[1] DB 직접 조회 (findAllByOrderByLevelAsc)");
        List<Long> dbTimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            List<UserLevelConfig> configs = userLevelConfigRepository.findAllByOrderByLevelAsc();
            long end = System.nanoTime();
            dbTimes.add(end - start);
        }
        printStats("DB 직접 조회", dbTimes);

        // Redis 캐시 조회 벤치마크
        System.out.println("\n[2] Redis 캐시 조회 (getAllLevelConfigs)");
        List<Long> cacheTimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            List<UserLevelConfig> configs = userLevelConfigCacheService.getAllLevelConfigs();
            long end = System.nanoTime();
            cacheTimes.add(end - start);
        }
        printStats("Redis 캐시 조회", cacheTimes);

        // 비교 결과
        printComparison(dbTimes, cacheTimes);
    }

    @Test
    @DisplayName("단일 스레드 - findByLevel 성능 비교")
    void benchmark_singleThread_findByLevel() {
        System.out.println("\n========================================");
        System.out.println("findByLevel 벤치마크 테스트");
        System.out.println("========================================\n");

        // Warmup
        System.out.println("워밍업 중...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            userLevelConfigRepository.findByLevel(1);
            userLevelConfigCacheService.getLevelConfigByLevel(1);
        }

        // DB 직접 조회
        System.out.println("\n[1] DB 직접 조회 (findByLevel)");
        List<Long> dbTimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            int level = (i % 10) + 1; // 1-10 레벨 순환
            long start = System.nanoTime();
            userLevelConfigRepository.findByLevel(level);
            long end = System.nanoTime();
            dbTimes.add(end - start);
        }
        printStats("DB findByLevel", dbTimes);

        // Redis 캐시 조회
        System.out.println("\n[2] Redis 캐시 조회 (getLevelConfigByLevel)");
        List<Long> cacheTimes = new ArrayList<>();
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            int level = (i % 10) + 1;
            long start = System.nanoTime();
            userLevelConfigCacheService.getLevelConfigByLevel(level);
            long end = System.nanoTime();
            cacheTimes.add(end - start);
        }
        printStats("Cache getLevelConfigByLevel", cacheTimes);

        printComparison(dbTimes, cacheTimes);
    }

    @Test
    @DisplayName("멀티 스레드 - 동시 요청 성능 비교")
    void benchmark_multiThread_concurrent() throws InterruptedException {
        System.out.println("\n========================================");
        System.out.println("멀티 스레드 벤치마크 테스트 (" + CONCURRENT_THREADS + " threads)");
        System.out.println("========================================\n");

        int requestsPerThread = BENCHMARK_ITERATIONS / CONCURRENT_THREADS;

        // DB 직접 조회 - 동시 요청
        System.out.println("[1] DB 직접 조회 - 동시 요청");
        long dbTotalTime = runConcurrentBenchmark(requestsPerThread, () -> {
            userLevelConfigRepository.findAllByOrderByLevelAsc();
        });
        double dbAvgMs = dbTotalTime / 1_000_000.0 / BENCHMARK_ITERATIONS;
        System.out.printf("   총 시간: %.2f ms, 평균: %.4f ms/req, 처리량: %.0f req/s%n",
            dbTotalTime / 1_000_000.0, dbAvgMs, BENCHMARK_ITERATIONS / (dbTotalTime / 1_000_000_000.0));

        // Redis 캐시 조회 - 동시 요청
        System.out.println("\n[2] Redis 캐시 조회 - 동시 요청");
        long cacheTotalTime = runConcurrentBenchmark(requestsPerThread, () -> {
            userLevelConfigCacheService.getAllLevelConfigs();
        });
        double cacheAvgMs = cacheTotalTime / 1_000_000.0 / BENCHMARK_ITERATIONS;
        System.out.printf("   총 시간: %.2f ms, 평균: %.4f ms/req, 처리량: %.0f req/s%n",
            cacheTotalTime / 1_000_000.0, cacheAvgMs, BENCHMARK_ITERATIONS / (cacheTotalTime / 1_000_000_000.0));

        // 비교
        System.out.println("\n[비교 결과]");
        double improvement = ((double) dbTotalTime - cacheTotalTime) / dbTotalTime * 100;
        if (improvement > 0) {
            System.out.printf("   Redis 캐시가 %.1f%% 더 빠름%n", improvement);
        } else {
            System.out.printf("   DB 조회가 %.1f%% 더 빠름%n", -improvement);
        }
    }

    private long runConcurrentBenchmark(int requestsPerThread, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);

        long startTime = System.nanoTime();

        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        task.run();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        executor.shutdown();
        return endTime - startTime;
    }

    private void printStats(String label, List<Long> timesNano) {
        LongSummaryStatistics stats = timesNano.stream()
            .mapToLong(Long::longValue)
            .summaryStatistics();

        // 정렬하여 백분위수 계산
        List<Long> sorted = timesNano.stream().sorted().toList();
        long p50 = sorted.get((int) (sorted.size() * 0.50));
        long p90 = sorted.get((int) (sorted.size() * 0.90));
        long p99 = sorted.get((int) (sorted.size() * 0.99));

        System.out.printf("   iterations: %d%n", stats.getCount());
        System.out.printf("   avg: %.4f ms%n", stats.getAverage() / 1_000_000);
        System.out.printf("   min: %.4f ms%n", stats.getMin() / 1_000_000.0);
        System.out.printf("   max: %.4f ms%n", stats.getMax() / 1_000_000.0);
        System.out.printf("   p50: %.4f ms%n", p50 / 1_000_000.0);
        System.out.printf("   p90: %.4f ms%n", p90 / 1_000_000.0);
        System.out.printf("   p99: %.4f ms%n", p99 / 1_000_000.0);
    }

    private void printComparison(List<Long> dbTimes, List<Long> cacheTimes) {
        double dbAvg = dbTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double cacheAvg = cacheTimes.stream().mapToLong(Long::longValue).average().orElse(0);

        System.out.println("\n========================================");
        System.out.println("비교 결과");
        System.out.println("========================================");
        System.out.printf("DB 평균:    %.4f ms%n", dbAvg / 1_000_000);
        System.out.printf("Cache 평균: %.4f ms%n", cacheAvg / 1_000_000);

        double improvement = (dbAvg - cacheAvg) / dbAvg * 100;
        if (improvement > 0) {
            System.out.printf("개선율: %.1f%% (캐시가 더 빠름)%n", improvement);
            System.out.printf("속도 배수: %.2fx 빠름%n", dbAvg / cacheAvg);
        } else {
            System.out.printf("결과: DB가 %.1f%% 더 빠름%n", -improvement);
        }
        System.out.println("========================================\n");
    }
}
