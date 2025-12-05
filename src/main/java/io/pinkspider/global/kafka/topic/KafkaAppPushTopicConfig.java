package io.pinkspider.global.kafka.topic;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaAppPushTopicConfig {

    @Value("${spring.kafka.app-push.topic}")
    String appPushTopic;

    @Bean(name = "appPushTopic")
    public NewTopic appPushTopic() {
        return new NewTopic(appPushTopic, 3, (short) 1);
    }
}
