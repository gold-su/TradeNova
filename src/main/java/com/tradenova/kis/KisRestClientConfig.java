package com.tradenova.kis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration //Spring Bean 설정 코드가 들어 있다는 어노테이션
public class KisRestClientConfig {

    @Bean //이 메서드의 반환값을 Spring Bean 으로 등록해라
    RestClient kisRestClient(kisProperties props) { //kis url bean 등록해서 편하게 쓰려는 용도로 만듬
        //baseUrl을 통일해두면 각 API 호출이 편함
        return RestClient.builder()
                .baseUrl(props.baseUrl()) //KIS API는 모든 요청이 같은 base URL
                .build();
    }
}
