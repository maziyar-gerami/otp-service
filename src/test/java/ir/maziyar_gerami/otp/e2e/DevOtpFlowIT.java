package ir.maziyar_gerami.otp.e2e;

import ir.maziyar_gerami.otp.application.port.out.otp.OtpGeneratorPort;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpSenderPort;
import ir.maziyar_gerami.otp.bootstrap.OtpApplication;
import ir.maziyar_gerami.otp.domain.OtpCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(classes = OtpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevOtpFlowIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    @Autowired MockMvc mvc;
    @MockitoBean OtpSenderPort sender;
    @MockitoBean OtpGeneratorPort generator;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("otp.key-prefix", () -> "dev-e2e:");
        registry.add("otp.code-length", () -> "8");
        registry.add("sms.api-key", () -> "");
    }

    @Test
    void requestLengthOverridesConfiguredDefaultAndReturnedCodeVerifies() throws Exception {
        when(generator.generate(6)).thenReturn(new OtpCode("001234"));
        String request = "{\"phoneNumber\":\"+989121234507\",\"codeLength\":6}";
        mvc.perform(post("/api/v1/otp/request").contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("001234"));
        mvc.perform(post("/api/v1/otp/verify").contentType("application/json")
                        .content("{\"phoneNumber\":\"+989121234507\",\"code\":\"001234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        verify(generator).generate(6);
        verifyNoInteractions(sender);
    }

    @Test
    void returnsExactConfiguredLengthCodeWithoutSmsAndVerifiesItOnlyOnce() throws Exception {
        when(generator.generate(8)).thenReturn(new OtpCode("00123456"));
        String request = "{\"phoneNumber\":\"+989121234506\"}";
        String verification = "{\"phoneNumber\":\"+989121234506\",\"code\":\"00123456\"}";

        mvc.perform(post("/api/v1/otp/request").contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60))
                .andExpect(jsonPath("$.code").value("00123456"))
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(post("/api/v1/otp/verify").contentType("application/json").content(verification))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post("/api/v1/otp/verify").contentType("application/json").content(verification))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_NOT_FOUND_OR_EXPIRED"));
        mvc.perform(post("/api/v1/otp/request").contentType("application/json").content(request))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_COOLDOWN_ACTIVE"));

        verify(generator).generate(8);
        verifyNoInteractions(sender);
    }
}
