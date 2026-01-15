package com.tradenova.kis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis")
//record 객체는 불변, 생성자 및 getter 자동 생성을 해주기 때문에 설정 값 용도로 최적이다.
public record kisProperties( //application.yml의 kis.* 설정을 이 클래스에 자동 매핑
        String baseUrl,
        String appkey,
        String appsecret,
        String custtype
) { }
