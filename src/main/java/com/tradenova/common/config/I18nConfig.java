package com.tradenova.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration //설정 클래스라는 뜻, Spring 시작 시 같이 로딩됨
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource(); //메시지 파일을 읽는 구현체, 파일 변경 시 재로딩 가능 (개발 중 편함)
        messageSource.setBasename("classpath:messages"); //기준 파일 이름
        messageSource.setDefaultEncoding("UTF-8"); //한글 깨짐 방지
        messageSource.setFallbackToSystemLocale(false); //서버 os 언어로 자동 fallback X, 명시한 메시지 파일만 사용. 글로벌 서비스에서 중요
        return messageSource;
    }
}
