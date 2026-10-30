package ir.maziyar_gerami.otp.e2e;

import ir.maziyar_gerami.otp.bootstrap.OtpApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(classes = {OtpApplication.class, OtpFlowIT.FixedClockConfiguration.class})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class OtpFlowIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    static final MockWebServer SMS = startSms();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static MockWebServer startSms() {
        try {
            var server = new MockWebServer();
            server.start();
            return server;
        } catch (java.io.IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("otp.key-prefix", () -> "e2e:");
        registry.add("otp.ttl", () -> "2s");
        registry.add("otp.resend-cooldown", () -> "1s");
        registry.add("sms.base-url", () -> SMS.url("/").toString());
        registry.add("sms.api-key", () -> "integration-test-key");
    }

    @AfterAll
    static void closeSms() throws Exception { SMS.shutdown(); }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary
        Clock testClock() { return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC); }
    }

    String request(String phone) throws Exception {
        return request(phone, null);
    }

    String request(String phone, Integer codeLength) throws Exception {
        SMS.enqueue(new MockResponse().setResponseCode(202));
        var body = new java.util.HashMap<String, Object>();
        body.put("phoneNumber", phone);
        if (codeLength != null) {
            body.put("codeLength", codeLength);
        }
        mvc.perform(post("/api/v1/otp/request").contentType("application/json")
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(2))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(1)).andExpect(jsonPath("$.code").doesNotExist());
        var sms = SMS.takeRequest(3, TimeUnit.SECONDS);
        assertThat(sms).isNotNull();
        return json.readTree(sms.getBody().readUtf8()).path("code").asText();
    }

    org.springframework.test.web.servlet.ResultActions verify(String phone, String code) throws Exception {
        return mvc.perform(post("/api/v1/otp/verify").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("phoneNumber", phone, "code", code))));
    }

    @Test
    void productionSendsRequestedLengthBySmsAndVerifiesAgainstStoredLength() throws Exception {
        String phone = "+989121234508";
        String code = request(phone, 8);
        assertThat(code).matches("[0-9]{8}");
        verify(phone, "123456").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(phone, code).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void fullFlowIsSingleUseAndProductionLogsAreRedacted() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                ir.maziyar_gerami.otp.adapter.out.sms.HttpOtpSender.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String phone = "+989121234501";
            String code = request(phone);
            assertThat(code).matches("[0-9]{6}");
            verify(phone, code).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
            verify(phone, code).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OTP_NOT_FOUND_OR_EXPIRED"));
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(code, phone));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void expiryUsesRealRedisTtl() throws Exception {
        String phone = "+989121234502";
        String code = request(phone);
        // Do not verify while waiting: the first correct verification would consume the OTP.
        await().pollDelay(Duration.ofMillis(2100)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                verify(phone, code).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("OTP_NOT_FOUND_OR_EXPIRED")));
    }

    @Test
    void thirdWrongAttemptDeletesOtp() throws Exception {
        String phone = "+989121234503";
        String code = request(phone);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 2; attempt++) {
            verify(phone, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        verify(phone, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        verify(phone, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_NOT_FOUND_OR_EXPIRED"));
    }

    @Test
    void cooldownHasRetryHeader() throws Exception {
        String phone = "+989121234504";
        request(phone);
        mvc.perform(post("/api/v1/otp/request").contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("phoneNumber", phone))))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("OTP_COOLDOWN_ACTIVE"));
    }

    @Test
    void failedDeliveryRemovesCodeAndAllowsImmediateRetry() throws Exception {
        String phone = "+989121234505";
        SMS.enqueue(new MockResponse().setResponseCode(500).setBody("provider secret"));
        mvc.perform(post("/api/v1/otp/request").contentType("application/json")
                        .content(json.writeValueAsString(java.util.Map.of("phoneNumber", phone))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OTP_DELIVERY_FAILED"));
        var failed = SMS.takeRequest(3, TimeUnit.SECONDS);
        String failedCode = json.readTree(failed.getBody().readUtf8()).path("code").asText();
        verify(phone, failedCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_NOT_FOUND_OR_EXPIRED"));
        String next = request(phone);
        verify(phone, next).andExpect(status().isOk());
    }

    @Test
    void actuatorIncludesRedisAndOpenApiIsAvailable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk()).andExpect(jsonPath("$.app.name").value("OTP Service"));
        mvc.perform(get("/actuator/metrics")).andExpect(status().isOk()).andExpect(jsonPath("$.names").isArray());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/v1/otp/request']").exists());
    }
}
