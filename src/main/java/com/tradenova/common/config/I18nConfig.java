package com.tradenova.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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

    // @Valid가 이 MessageSource를 사용하게 연결
    @Bean
    public LocalValidatorFactoryBean getValidator(MessageSource messageSource) { //Validator를 스프링 빈으로 등록, MessageSource를 주입받음
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean(); //Spring 에서 Bean Validation을 담당하는 핵심 객체
        bean.setValidationMessageSource(messageSource);//@NotBlank(message = "{user.email.required}") << 이런 메시지를 messages.properties에서 찾도록 연결
        return bean; //이 Validator 가 전역 기본 Validator 가 됨
    }
}
