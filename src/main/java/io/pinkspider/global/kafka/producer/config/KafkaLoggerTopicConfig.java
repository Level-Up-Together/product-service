package io.pinkspider.global.kafka.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaLoggerTopicConfig {

    @Value("${spring.kafka.logger.topic}")
    String loggerTopic;

    @Bean(name = "loggerTopic")
    public NewTopic loggerTopic() {
        return new NewTopic(loggerTopic, 1, (short) 1);
    }
}
