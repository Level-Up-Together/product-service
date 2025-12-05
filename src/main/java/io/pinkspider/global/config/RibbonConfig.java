package io.pinkspider.global.config;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.RoundRobinRule;
import org.springframework.context.annotation.Bean;

public class RibbonConfig {

    @Bean
    public IRule ribbonRule(IClientConfig config) {
//        new RandomRule();
//        new WeightedResponseTimeRule();
//        new AvailabilityFilteringRule();
        return new RoundRobinRule();
    }

}
