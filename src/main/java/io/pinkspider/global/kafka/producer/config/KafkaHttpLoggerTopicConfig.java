package io.pinkspider.global.kafka.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaHttpLoggerTopicConfig {

    @Value("${spring.kafka.http-logger.topic}")
    String httpLoggerTopic;

    @Bean(name = "httpLoggerTopic")
    public NewTopic httpLoggerTopic() {
        return new NewTopic(httpLoggerTopic, 1, (short) 1);
    }
}
