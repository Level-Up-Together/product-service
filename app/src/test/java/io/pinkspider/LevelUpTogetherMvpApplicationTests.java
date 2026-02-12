package io.pinkspider;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LevelUpTogetherMvpApplicationTests {

    @Test
    @DisplayName("Spring Main 메소드 = 즉 spring boot 구동이 되는지 확인")
    public void contextLoads() {
        assertTrue(true);
    }
}
