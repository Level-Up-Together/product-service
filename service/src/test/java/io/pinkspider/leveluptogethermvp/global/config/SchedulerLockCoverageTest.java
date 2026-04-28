package io.pinkspider.leveluptogethermvp.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 멀티 인스턴스 환경에서 @Scheduled 메서드는 항상 @SchedulerLock과 함께 선언되어야 한다.
 *
 * <p>이 테스트는 새로운 스케줄러가 추가될 때 ShedLock 어노테이션을 빠뜨리는 회귀를 방지한다.
 *
 * <p>예외가 필요한 경우(예: 인스턴스별 로컬 정리 작업) 화이트리스트로 등록할 수 있다.
 */
@DisplayName("SchedulerLock 커버리지 테스트")
class SchedulerLockCoverageTest {

    /**
     * @SchedulerLock 적용이 의도적으로 제외된 클래스+메서드 화이트리스트.
     * 형식: "FQCN#methodName"
     */
    private static final Set<String> EXEMPT_METHODS = Set.of(
        // 현재 예외 없음. 향후 인스턴스별 로컬 작업이 필요하면 여기에 추가.
    );

    @Test
    @DisplayName("@Scheduled가 붙은 모든 메서드에는 @SchedulerLock이 적용되어야 한다")
    void allScheduledMethods_haveSchedulerLock() {
        List<Method> scheduledMethods = findAllScheduledMethods();

        assertThat(scheduledMethods).isNotEmpty(); // 안전장치: 스캐닝 자체가 실패한 게 아닌지 확인

        List<String> missing = new ArrayList<>();
        for (Method method : scheduledMethods) {
            String key = method.getDeclaringClass().getName() + "#" + method.getName();
            if (EXEMPT_METHODS.contains(key)) {
                continue;
            }
            if (!method.isAnnotationPresent(SchedulerLock.class)) {
                missing.add(key);
            }
        }

        assertThat(missing)
            .as("@Scheduled 메서드는 @SchedulerLock도 함께 선언되어야 합니다 (멀티 인스턴스 동시 실행 방지). "
                + "예외가 필요하면 SchedulerLockCoverageTest.EXEMPT_METHODS에 등록하세요.")
            .isEmpty();
    }

    @Test
    @DisplayName("@SchedulerLock의 name은 클래스+메서드 단위로 고유해야 한다 (락 충돌 방지)")
    void schedulerLockNames_areUnique() {
        List<Method> scheduledMethods = findAllScheduledMethods();

        List<String> names = new ArrayList<>();
        for (Method method : scheduledMethods) {
            SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
            if (lock != null) {
                names.add(lock.name());
            }
        }

        long distinct = names.stream().distinct().count();
        assertThat(distinct)
            .as("@SchedulerLock(name=...) 값이 중복되면 다른 스케줄러끼리 락이 충돌합니다. names=%s", names)
            .isEqualTo(names.size());
    }

    private List<Method> findAllScheduledMethods() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<Method> result = new ArrayList<>();
        // 프로젝트의 모든 패키지를 스캔
        Set<org.springframework.beans.factory.config.BeanDefinition> components =
            scanner.findCandidateComponents("io.pinkspider.leveluptogethermvp");

        for (org.springframework.beans.factory.config.BeanDefinition component : components) {
            String className = component.getBeanClassName();
            if (className == null) continue;
            try {
                Class<?> clazz = Class.forName(className);
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Scheduled.class)) {
                        result.add(method);
                    }
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // 무시 — 일부 클래스는 테스트 컨텍스트에서 로드 안 될 수 있음
            }
        }

        // resource 직접 스캔으로 동일 결과 보강 (ClassPathScanning이 누락하는 경우 대비)
        try {
            MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();
            Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(null)
                .getResources("classpath*:io/pinkspider/leveluptogethermvp/**/*Scheduler.class");
            for (Resource resource : resources) {
                try {
                    MetadataReader reader = metadataReaderFactory.getMetadataReader(resource);
                    String className = reader.getClassMetadata().getClassName();
                    Class<?> clazz = Class.forName(className);
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (method.isAnnotationPresent(Scheduled.class) && !result.contains(method)) {
                            result.add(method);
                        }
                    }
                } catch (Exception ignored) {
                    // 무시
                }
            }
        } catch (IOException ignored) {
            // 무시
        }

        return result;
    }
}
