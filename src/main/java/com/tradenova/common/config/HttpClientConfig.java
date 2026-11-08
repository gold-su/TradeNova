package com.tradenova.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 외부 HTTP 호출용 Bean 설정
 */
@Configuration
@EnableConfigurationProperties(OpenAiHttpProperties.class)
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            OpenAiHttpProperties properties
    ) {
        return builder
                .setConnectTimeout(properties.getConnectTimeout())
                .setReadTimeout(properties.getReadTimeout())
                .build();
    }
}
