package ir.maziyar_gerami.otp.adapter.in.web;

import ir.maziyar_gerami.otp.adapter.in.web.requestotp.RequestOtpRequest;
import ir.maziyar_gerami.otp.adapter.in.web.requestotp.RequestOtpResponse;
import ir.maziyar_gerami.otp.adapter.in.web.verifyotp.VerifyOtpRequest;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpResult;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpUseCase;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpResult;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpUseCase;
import ir.maziyar_gerami.otp.bootstrap.OtpApplication;
import ir.maziyar_gerami.otp.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OtpController.class)
@ContextConfiguration(classes = OtpApplication.class)
class OtpControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean
    RequestOtpUseCase requestOtp;
    @MockitoBean
    VerifyOtpUseCase verifyOtp;
    static final String PHONE = "+989121234567";
    static final String REQUEST = "{\"phoneNumber\":\"+989121234567\"}";
    static final String VERIFY = "{\"phoneNumber\":\"+989121234567\",\"code\":\"123456\"}";

    @Test
    void requestReturnsTimingAndNeverCode() throws Exception {
        when(requestOtp.request(any())).thenReturn(new RequestOtpResult(120, 60));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60)).andExpect(jsonPath("$.code").doesNotExist());
        verify(requestOtp).request(new RequestOtpCommand(new PhoneNumber(PHONE)));
    }

    @Test
    void developmentResultReturnsExactCodeIncludingLeadingZeros() throws Exception {
        when(requestOtp.request(any())).thenReturn(new RequestOtpResult(120, 60, new OtpCode("001234")));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60))
                .andExpect(jsonPath("$.code").value("001234"))
                .andExpect(header().string("Cache-Control", "no-store"));
        org.assertj.core.api.Assertions.assertThat(new RequestOtpResponse(120, 60, "001234").toString())
                .doesNotContain("001234");
    }

    @Test
    void verifyReturnsSuccess() throws Exception {
        when(verifyOtp.verify(any())).thenReturn(new VerifyOtpResult(true));
        mvc.perform(post("/api/v1/otp/verify").contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        verify(verifyOtp).verify(new VerifyOtpCommand(new PhoneNumber(PHONE), new OtpCode("123456")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"phoneNumber\":null}", "{\"phoneNumber\":\"09121234567\"}",
            "{\"phoneNumber\":\"+01234\"}", "{\"phoneNumber\":\"+1234567890123456\"}", "{",
            "{\"phoneNumber\":\"+989121234567\",\"unexpected\":true}"})
    void invalidRequestReturnsSanitizedProblem(String body) throws Exception {
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(requestOtp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"phoneNumber\":\"+989121234567\",\"code\":\"123\"}",
            "{\"phoneNumber\":\"+989121234567\",\"code\":\"abcdef\"}"})
    void invalidVerifyDoesNotReachUseCase(String body) throws Exception {
        mvc.perform(post("/api/v1/otp/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(verifyOtp);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"OTP_INVALID", "OTP_NOT_FOUND_OR_EXPIRED", "OTP_ATTEMPTS_EXCEEDED", "VALIDATION_ERROR"})
    void verificationErrorsUseStableCodes(ErrorCode code) throws Exception {
        when(verifyOtp.verify(any())).thenThrow(new OtpException(code, "Verification failed."));
        mvc.perform(post("/api/v1/otp/verify").contentType(MediaType.APPLICATION_JSON).content(VERIFY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code.name()))
                .andExpect(jsonPath("$.type").value("urn:otp:error:" + code.name()))
                .andExpect(jsonPath("$.instance").value("/api/v1/otp/verify"));
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"OTP_RATE_LIMITED", "OTP_COOLDOWN_ACTIVE"})
    void throttlingIncludesRetryAfter(ErrorCode code) throws Exception {
        when(requestOtp.request(any())).thenThrow(new OtpException(code, "Wait.", 42));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.code").value(code.name()));
    }

    @Test
    void deliveryFailureIs503() throws Exception {
        when(requestOtp.request(any())).thenThrow(new OtpException(ErrorCode.OTP_DELIVERY_FAILED, "Delivery failed."));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OTP_DELIVERY_FAILED"));
    }

    @Test
    void redisFailureDoesNotLeakConnectionDetails() throws Exception {
        when(requestOtp.request(any())).thenThrow(new DataAccessResourceFailureException("password=secret"));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OTP_SERVICE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void unexpectedFailureIsSanitized() throws Exception {
        when(requestOtp.request(any())).thenThrow(new IllegalStateException("secret"));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("OTP_SERVICE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void methodAndContentTypeErrorsRemainProblems() throws Exception {
        mvc.perform(get("/api/v1/otp/request")).andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(post("/api/v1/otp/request").contentType(MediaType.TEXT_PLAIN).content(REQUEST))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void dtosDoNotExposePhoneOrCodeInToString() {
        org.assertj.core.api.Assertions.assertThat(new RequestOtpRequest(PHONE).toString()).doesNotContain(PHONE);
        org.assertj.core.api.Assertions.assertThat(new VerifyOtpRequest(PHONE, "123456").toString()).doesNotContain(PHONE, "123456");
    }
}
