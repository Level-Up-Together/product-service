package io.pinkspider.leveluptogethermvp.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * LUT-741: OpenAPI 명세(`./gradlew openapi3`)는 epages {@code
 * MockMvcRestDocumentationWrapper.document} 로 만든 문서 조각만 담는다. 스프링 기본 document() 로 쓰면 테스트는 통과하지만 그
 * API 는 Swagger·웹 명세 사본에서 빠진다 — 탑 API 33개·기기 토큰 등 46개가 그렇게 빠져 있었다.
 *
 * <p>이 테스트는 테스트 소스를 읽어 두 가지를 막는다. 예외가 필요하면 화이트리스트에 파일 이름을 등록한다.
 */
@DisplayName("REST Docs → OpenAPI 커버리지 테스트")
class RestDocsOpenApiCoverageTest {

    /** 스프링 기본 문서화 함수 — 이 소스에 그대로 쓰면 자기 자신이 걸리므로 나눠 둔다 */
    private static final String PLAIN_DOCUMENT = "MockMvcRestDocumentation" + ".document";

    private static final String PLAIN_DOCUMENT_ALL = "MockMvcRestDocumentation" + ".*";

    private static final String OPENAPI_DOCUMENT = "MockMvcRestDocumentationWrapper.document(";

    /** 문서화 없이 두는 컨트롤러 테스트 화이트리스트. 형식: 파일 이름 (예: "FooControllerTest.java") */
    private static final Set<String> EXEMPT_CONTROLLER_TESTS =
            Set.of(
                    // 현재 예외 없음.
                    );

    @Test
    @DisplayName("스프링 기본 document() 를 쓰는 테스트가 없다 — epages MockMvcRestDocumentationWrapper 를 쓴다")
    void noPlainRestDocsDocument() {
        List<String> offenders =
                testSources()
                        .filter(
                                path -> {
                                    String source = read(path);
                                    return source.contains(PLAIN_DOCUMENT)
                                            || source.contains(PLAIN_DOCUMENT_ALL);
                                })
                        .map(path -> path.getFileName().toString())
                        .toList();

        assertThat(offenders)
                .as(
                        "다음 테스트가 스프링 기본 document() 를 쓴다 — OpenAPI 명세에 들어가지 않는다."
                            + " MockMvcRestDocumentationWrapper.document(id,"
                            + " ResourceSnippetParameters.builder().tag(..).description(..), 조각...)"
                            + " 로 바꾸세요")
                .isEmpty();
    }

    @Test
    @DisplayName("@WebMvcTest 컨트롤러 테스트는 OpenAPI 문서 조각을 하나 이상 만든다")
    void everyControllerTestDocumentsForOpenApi() {
        List<Path> controllerTests =
                testSources()
                        .filter(
                                path ->
                                        path.getFileName()
                                                .toString()
                                                .endsWith("ControllerTest.java"))
                        .filter(path -> read(path).contains("@WebMvcTest"))
                        .toList();

        assertThat(controllerTests.size()).isGreaterThan(50); // 안전장치: 소스를 못 찾은 게 아닌지 확인

        List<String> missing =
                controllerTests.stream()
                        .filter(
                                path ->
                                        !EXEMPT_CONTROLLER_TESTS.contains(
                                                path.getFileName().toString()))
                        .filter(path -> !read(path).contains(OPENAPI_DOCUMENT))
                        .map(path -> path.getFileName().toString())
                        .toList();

        assertThat(missing)
                .as(
                        "다음 컨트롤러 테스트에 OpenAPI 문서 조각이 없다 — 그 API 가 명세에서 빠진다."
                                + " .andDo(MockMvcRestDocumentationWrapper.document(...)) 를 추가하세요")
                .isEmpty();
    }

    /**
     * service 모듈의 테스트 소스 (`src/test/java`, `*-service/src/test/java`, `shared-test/src/test/java`)
     */
    private static Stream<Path> testSources() {
        try (Stream<Path> walk = Files.walk(Path.of("."))) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src/test/java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
