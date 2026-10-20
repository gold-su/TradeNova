package com.tradenova.beta;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BetaAccessPolicyPropertyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=prod")
            .withUserConfiguration(TestConfig.class);

    @Test
    void railwayStyleEnvironmentVariableDisablesPublicBeta() {
        withRailwayEnvironment("false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BetaAccessPolicy.class).isPublicBetaEnabled()).isFalse();
        });
    }

    @Test
    void railwayStyleEnvironmentVariableEnablesPublicBeta() {
        withRailwayEnvironment("true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(BetaAccessPolicy.class).isPublicBetaEnabled()).isTrue();
        });
    }

    @Test
    void productionConfigurationDefaultsToClosed() throws IOException {
        PropertySource<?> prod = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .get(0);

        assertThat(prod.getProperty("app.beta.public-enabled"))
                .isEqualTo("${APP_BETA_PUBLIC_ENABLED:false}");
    }

    private ApplicationContextRunner withRailwayEnvironment(String enabled) {
        return contextRunner.withInitializer(context -> context.getEnvironment()
                .getPropertySources()
                .addFirst(new SystemEnvironmentPropertySource(
                        "railwayBetaEnvironment",
                        Map.of("APP_BETA_PUBLIC_ENABLED", enabled)
                )));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(BetaAccessPolicy.class)
    static class TestConfig {
    }
}