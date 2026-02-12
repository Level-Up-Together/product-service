package io.pinkspider.leveluptogethermvp.config;

import static org.mockito.Mockito.mockStatic;

import io.pinkspider.global.component.CommonCodeHelper;
import io.pinkspider.global.component.metaredis.CryptoMetaDataLoader;
import io.pinkspider.global.domain.redis.CryptoMetaData;
import io.pinkspider.util.MockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io.pinkspider.global.filter.*"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*SecurityConfig"),  // 추가
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*WebSecurityConfig")  // 추가
    },
    excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        SecurityAutoConfiguration.class,  // 추가
        UserDetailsServiceAutoConfiguration.class,  // 추가
        SecurityFilterAutoConfiguration.class  // 추가
    }
)
@Import(ControllerTestConfig.class)
@AutoConfigureRestDocs
@AutoConfigureMockMvc(addFilters = false)
public class BaseTestController {

    @Autowired
    protected MockMvc mockMvc;

    protected MockedStatic<CryptoMetaDataLoader> mockedCryptoMetaDataLoader = null;
    protected MockedStatic<CommonCodeHelper> mockStaticCommonCodeHelper = null;

    @BeforeEach
    public void setUp() {
        CryptoMetaData cryptoMetaData = MockUtil.readJsonFileToClass("fixture/core/CryptoMetaData.json", CryptoMetaData.class);

        mockedCryptoMetaDataLoader = mockStatic(CryptoMetaDataLoader.class);
        mockedCryptoMetaDataLoader.when(CryptoMetaDataLoader::getCryptoMetaDataDto).thenReturn(cryptoMetaData);

        mockStaticCommonCodeHelper = mockStatic(CommonCodeHelper.class);
    }

    @AfterEach
    public void terminate() {
        mockedCryptoMetaDataLoader.close();
        mockStaticCommonCodeHelper.close();
    }
}
