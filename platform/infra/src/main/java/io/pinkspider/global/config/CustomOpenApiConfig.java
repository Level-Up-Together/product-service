package io.pinkspider.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// RestDoc 생성을 위한 설정파일 이다.
@Configuration
@RefreshScope
public class CustomOpenApiConfig {

    @Value("${api-doc-meta-info.title}")
    private String title;

    @Value("${api-doc-meta-info.version}")
    private String version;

    @Value("${api-doc-meta-info.tags}")
    private String tags;

    @Value("${api-doc-meta-info.description}")
    private String description;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().components(
                new Components().addSecuritySchemes("basicScheme",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
            .info(new Info()
                .title(title)
                .version(version)
                .description(description))
            .addTagsItem(new Tag().name(tags));
    }
}
