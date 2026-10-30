package ir.maziyar_gerami.otp.adapter.out.sms;

import ir.maziyar_gerami.otp.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

class HttpOtpSenderTest {
    MockWebServer server;
    HttpOtpSender sender;
    final PhoneNumber phone = new PhoneNumber("+989121234567");
    final OtpCode code = new OtpCode("001234");

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        sender = senderWithTimeout(Duration.ofSeconds(5));
    }

    private HttpOtpSender senderWithTimeout(Duration timeout) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        return new HttpOtpSender(RestClient.builder().baseUrl(server.url("/").toString())
                .defaultHeader("Authorization", "Bearer test-key").requestFactory(factory).build(), "/messages");
    }

    @AfterEach
    void tearDown() throws Exception { server.shutdown(); }

    @Test
    void sendsProviderAgnosticPayloadAndAuthorization() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(202));
        sender.send(phone, code, Duration.ofSeconds(120));
        var request = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/messages");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        var json = new ObjectMapper().readTree(request.getBody().readUtf8());
        assertThat(json.path("phoneNumber").asText()).isEqualTo(phone.value());
        assertThat(json.path("code").asText()).isEqualTo("001234");
        assertThat(json.path("expiresInSeconds").asLong()).isEqualTo(120);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 401, 429, 500})
    void providerErrorsBecomeSanitizedDeliveryFailures(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("secret " + code.value()));
        assertFailure();
    }

    @Test
    void timeoutBecomesDeliveryFailure() {
        sender = senderWithTimeout(Duration.ofMillis(200));
        server.enqueue(new MockResponse().setHeadersDelay(1, TimeUnit.SECONDS));
        assertFailure();
    }

    private void assertFailure() {
        assertThatThrownBy(() -> sender.send(phone, code, Duration.ofSeconds(120)))
                .isInstanceOfSatisfying(OtpException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OTP_DELIVERY_FAILED);
                    assertThat(e.getMessage()).doesNotContain("secret", phone.value(), code.value());
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void devSenderMasksPhoneAndIsExplicitAboutCodeLogging() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingOtpSender.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            new LoggingOtpSender().send(phone, code, Duration.ofSeconds(120));
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("DEV ONLY", "+***67", "001234")
                    .doesNotContain(phone.value());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
