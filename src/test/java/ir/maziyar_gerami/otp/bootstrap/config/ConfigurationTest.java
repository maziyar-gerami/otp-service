package ir.maziyar_gerami.otp.bootstrap.config;

import ir.maziyar_gerami.otp.adapter.out.sms.*;
import ir.maziyar_gerami.otp.application.port.out.OtpSenderPort;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConfigurationTest {
    private ApplicationContextRunner runner(String profile) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(OtpConfiguration.class, Infrastructure.class)
                .withPropertyValues("spring.profiles.active=" + profile, "spring.data.redis.host=localhost",
                        "spring.data.redis.port=6379", "otp.code-length=6", "otp.ttl=120s",
                        "otp.max-verify-attempts=3", "otp.resend-cooldown=60s", "otp.rate-limit.max-requests=3",
                        "otp.rate-limit.window=10m", "otp.key-prefix=otp:", "sms.base-url=http://localhost:9090",
                        "sms.send-path=/messages", "sms.connect-timeout=2s", "sms.read-timeout=5s", "sms.api-key=test-key");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Infrastructure {
        @Bean StringRedisTemplate redis() { return mock(StringRedisTemplate.class); }
        @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
    }

    @Test
    void devUsesLoggingSender() {
        runner("dev").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OtpSenderPort.class);
            assertThat(context.getBean(OtpSenderPort.class)).isInstanceOf(LoggingOtpSender.class);
            assertThat(context.getBean(OtpProperties.class).toPolicy().ttl()).isEqualTo(Duration.ofSeconds(120));
        });
    }

    @Test
    void prodAlwaysUsesHttpIncludingWhenDevAlsoActive() {
        for (String profile : new String[]{"prod", "dev,prod"}) {
            runner(profile).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(OtpSenderPort.class);
                assertThat(context.getBean(OtpSenderPort.class)).isInstanceOf(HttpOtpSender.class);
            });
        }
    }

    @Test
    void invalidPropertiesFailStartup() {
        for (String property : new String[]{"otp.code-length=3", "otp.ttl=0s", "otp.ttl=1500ms",
                "otp.resend-cooldown=-1s", "otp.rate-limit.max-requests=0", "otp.rate-limit.window=8d",
                "otp.key-prefix=bad{prefix", "sms.base-url=ftp://localhost", "sms.read-timeout=0s",
                "sms.send-path=https://other.example", "spring.data.redis.host=", "spring.data.redis.port=65536"}) {
            runner("dev").withPropertyValues(property).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void prodRequiresNonemptyApiKey() {
        for (String key : new String[]{"", "bad\rkey", "bad\nkey"}) {
            runner("prod").withPropertyValues("sms.api-key=" + key).run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void validatesNullsDurationBoundsAndUrls() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new OtpProperties(6, null, 3, Duration.ofSeconds(60), null, ""))).isNotEmpty();
            assertThat(validator.validate(new OtpProperties.RateLimit(3, null))).isNotEmpty();
            for (URI url : new URI[]{URI.create("/relative"), URI.create("http://user:pass@localhost"),
                    URI.create("http://localhost?secret=yes"), URI.create("http://localhost/#secret")}) {
                assertThat(validator.validate(new SmsProperties(url, "/messages", "key", Duration.ofSeconds(1),
                        Duration.ofSeconds(1)))).isNotEmpty();
            }
            assertThat(validator.validate(new SmsProperties(null, "", "key", null, Duration.ofSeconds(31)))).isNotEmpty();
            assertThat(new SmsProperties(URI.create("https://example.org"), "/messages", "secret",
                    Duration.ofSeconds(1), Duration.ofSeconds(1)).toString()).doesNotContain("secret");
            assertThat(new RedisEndpointProperties("localhost", 6379, "secret").toString()).doesNotContain("secret");
        }
    }
}
