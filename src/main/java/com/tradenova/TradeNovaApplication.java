package com.tradenova;

import com.tradenova.kis.kisProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(kisProperties.class) //@ConfigurationProperties로 만든 설정 클래스를 Spring Bean으로 등록해라 라는 선언
public class TradeNovaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeNovaApplication.class, args);
    }
}
