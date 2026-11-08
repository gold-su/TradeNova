package com.tradenova.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientConfigTest {

    @Test
    void appliesConfigurableConnectAndReadTimeouts() {
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        RestTemplate expected = mock(RestTemplate.class);
        OpenAiHttpProperties properties = new OpenAiHttpProperties();
        properties.setConnectTimeout(Duration.ofSeconds(7));
        properties.setReadTimeout(Duration.ofSeconds(75));
        when(builder.setConnectTimeout(Duration.ofSeconds(7))).thenReturn(builder);
        when(builder.setReadTimeout(Duration.ofSeconds(75))).thenReturn(builder);
        when(builder.build()).thenReturn(expected);

        RestTemplate actual = new HttpClientConfig().restTemplate(builder, properties);

        assertSame(expected, actual);
        verify(builder).setConnectTimeout(Duration.ofSeconds(7));
        verify(builder).setReadTimeout(Duration.ofSeconds(75));
    }
}
