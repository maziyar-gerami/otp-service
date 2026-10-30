package ir.maziyar_gerami.otp.application.service;

import ir.maziyar_gerami.otp.application.port.in.*;
import ir.maziyar_gerami.otp.application.port.out.*;
import ir.maziyar_gerami.otp.domain.*;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestOtpServiceTest {
    @Mock OtpStorePort store;
    @Mock OtpSenderPort sender;
    @Mock OtpGeneratorPort generator;
    @Mock RateLimiterPort limiter;
    @Mock OtpCodec codec;
    final PhoneNumber phone = new PhoneNumber("+989121234567");
    final OtpCode code = new OtpCode("123456");
    final OtpPolicy policy = new OtpPolicy(6, Duration.ofSeconds(120), 3,
            Duration.ofSeconds(60), 3, Duration.ofMinutes(10));
    final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    RequestOtpService service;

    @BeforeEach
    void setUp() {
        service = new RequestOtpService(store, sender, generator, limiter, codec,
                policy, Clock.fixed(now, ZoneOffset.UTC));
    }

    void allow() {
        when(limiter.acquire(eq(phone), anyString(), eq(policy.resendCooldown()),
                eq(3), eq(policy.rateLimitWindow())))
                .thenReturn(new RateLimiterPort.Admission(RateLimiterPort.Status.ALLOWED, 0));
    }

    void prepareCode() {
        when(generator.generate(6)).thenReturn(code);
        when(codec.encode(code)).thenReturn("encoded");
    }

    @Test
    void savesBeforeSendingAndReturnsOnlyTiming() {
        allow();
        prepareCode();
        when(store.save(eq(phone), any(), eq(policy.ttl()))).thenReturn(true);
        assertThat(service.request(new RequestOtpCommand(phone))).isEqualTo(new RequestOtpResult(120, 60));
        var captor = ArgumentCaptor.forClass(StoredOtp.class);
        var ordered = inOrder(store, sender);
        ordered.verify(store).save(eq(phone), captor.capture(), eq(policy.ttl()));
        ordered.verify(sender).send(phone, code, policy.ttl());
        assertThat(captor.getValue().issuedAt()).isEqualTo(now);
        assertThat(captor.getValue().attempts()).isZero();
        assertThat(captor.getValue().encodedCode()).isEqualTo("encoded");
        assertThat(captor.getValue().version()).isNotBlank();
    }

    @Test
    void cooldownPreventsGenerationStorageAndSms() {
        when(limiter.acquire(eq(phone), anyString(), any(), anyInt(), any()))
                .thenReturn(new RateLimiterPort.Admission(RateLimiterPort.Status.COOLDOWN, 42));
        assertThatThrownBy(() -> service.request(new RequestOtpCommand(phone)))
                .isInstanceOfSatisfying(OtpException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.OTP_COOLDOWN_ACTIVE);
                    assertThat(e.retryAfterSeconds()).isEqualTo(42);
                });
        verifyNoInteractions(store, sender, generator, codec);
    }

    @Test
    void rateLimitPreventsSms() {
        when(limiter.acquire(eq(phone), anyString(), any(), anyInt(), any()))
                .thenReturn(new RateLimiterPort.Admission(RateLimiterPort.Status.RATE_LIMITED, 300));
        assertThatThrownBy(() -> service.request(new RequestOtpCommand(phone)))
                .isInstanceOfSatisfying(OtpException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OTP_RATE_LIMITED));
        verifyNoInteractions(store, sender, generator, codec);
    }

    @Test
    void senderFailureCleansUpOnlyItsOwnVersion() {
        allow();
        prepareCode();
        when(store.save(eq(phone), any(), any())).thenReturn(true);
        var failure = new OtpException(ErrorCode.OTP_DELIVERY_FAILED, "Delivery failed");
        doThrow(failure).when(sender).send(phone, code, policy.ttl());
        assertThatThrownBy(() -> service.request(new RequestOtpCommand(phone))).isSameAs(failure);
        var captor = ArgumentCaptor.forClass(StoredOtp.class);
        verify(store).save(eq(phone), captor.capture(), any());
        verify(store).deleteIfVersion(phone, captor.getValue().version());
        verify(limiter).releaseCooldown(phone, captor.getValue().version());
    }

    @Test
    void expiredReservationDoesNotSend() {
        allow();
        prepareCode();
        when(store.save(eq(phone), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.request(new RequestOtpCommand(phone)))
                .isInstanceOfSatisfying(OtpException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OTP_SERVICE_UNAVAILABLE));
        verifyNoInteractions(sender);
        verify(limiter).releaseCooldown(eq(phone), anyString());
    }

    @Test
    void cleanupFailuresDoNotHideOriginalFailure() {
        allow();
        var failure = new IllegalStateException("generation failed");
        when(generator.generate(6)).thenThrow(failure);
        doThrow(new IllegalStateException("store down")).when(store).deleteIfVersion(eq(phone), anyString());
        doThrow(new IllegalStateException("limiter down")).when(limiter).releaseCooldown(eq(phone), anyString());
        assertThatThrownBy(() -> service.request(new RequestOtpCommand(phone))).isSameAs(failure);
        assertThat(failure.getSuppressed()).hasSize(2);
    }
}
