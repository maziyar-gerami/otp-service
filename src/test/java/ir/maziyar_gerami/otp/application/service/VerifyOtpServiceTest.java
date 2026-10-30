package ir.maziyar_gerami.otp.application.service;

import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpResult;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpCodec;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpStorePort;
import ir.maziyar_gerami.otp.application.service.verifyotp.VerifyOtpService;
import ir.maziyar_gerami.otp.domain.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerifyOtpServiceTest {
    @Mock
    OtpStorePort store;
    @Mock
    OtpCodec codec;
    final PhoneNumber phone = new PhoneNumber("+989121234567");
    final OtpCode code = new OtpCode("123456");
    final StoredOtp otp = new StoredOtp("version", "encoded", 0, Instant.EPOCH);
    final VerifyOtpCommand command = new VerifyOtpCommand(phone, code);
    VerifyOtpService service;

    @BeforeEach
    void setUp() {
        service = new VerifyOtpService(store, codec, new OtpPolicy(6, Duration.ofSeconds(120), 3,
                Duration.ofSeconds(60), 3, Duration.ofMinutes(10)));
    }

    @Test
    void consumesCorrectCode() {
        when(store.find(phone)).thenReturn(Optional.of(otp));
        when(codec.matches(code, "encoded")).thenReturn(true);
        when(store.completeVerification(phone, otp, true, 3)).thenReturn(OtpStorePort.VerificationOutcome.VERIFIED);
        assertThat(service.verify(command)).isEqualTo(new VerifyOtpResult(true));
        verify(store).completeVerification(phone, otp, true, 3);
    }

    @Test
    void missingOrExpiredFailsBeforeComparison() {
        when(store.find(phone)).thenReturn(Optional.empty());
        assertError(ErrorCode.OTP_NOT_FOUND_OR_EXPIRED);
        verifyNoInteractions(codec);
    }

    @ParameterizedTest
    @EnumSource(value = OtpStorePort.VerificationOutcome.class, names = {"INVALID", "ATTEMPTS_EXCEEDED", "NOT_FOUND"})
    void mapsAtomicFailure(OtpStorePort.VerificationOutcome outcome) {
        when(store.find(phone)).thenReturn(Optional.of(otp));
        when(codec.matches(code, "encoded")).thenReturn(false);
        when(store.completeVerification(phone, otp, false, 3)).thenReturn(outcome);
        assertError(switch (outcome) {
            case INVALID -> ErrorCode.OTP_INVALID;
            case ATTEMPTS_EXCEEDED -> ErrorCode.OTP_ATTEMPTS_EXCEEDED;
            default -> ErrorCode.OTP_NOT_FOUND_OR_EXPIRED;
        });
    }

    @Test
    void retriesOnStaleSnapshotAndRecompares() {
        var replacement = new StoredOtp("new", "new-encoded", 0, Instant.EPOCH);
        when(store.find(phone)).thenReturn(Optional.of(otp), Optional.of(replacement));
        when(codec.matches(code, "encoded")).thenReturn(false);
        when(codec.matches(code, "new-encoded")).thenReturn(true);
        when(store.completeVerification(phone, otp, false, 3)).thenReturn(OtpStorePort.VerificationOutcome.RETRY);
        when(store.completeVerification(phone, replacement, true, 3)).thenReturn(OtpStorePort.VerificationOutcome.VERIFIED);
        assertThat(service.verify(command).verified()).isTrue();
        verify(codec).matches(code, "new-encoded");
    }

    @Test
    void boundsContentionRetries() {
        when(store.find(phone)).thenReturn(Optional.of(otp));
        when(store.completeVerification(phone, otp, false, 3)).thenReturn(OtpStorePort.VerificationOutcome.RETRY);
        assertError(ErrorCode.OTP_SERVICE_UNAVAILABLE);
        verify(store, times(32)).find(phone);
    }

    @Test
    void wrongLengthIsValidationErrorAndDoesNotChargeAttempts() {
        assertThatThrownBy(() -> service.verify(new VerifyOtpCommand(phone, new OtpCode("1234"))))
                .isInstanceOfSatisfying(OtpException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(store, codec);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> service.verify(command)).isInstanceOfSatisfying(OtpException.class,
                e -> assertThat(e.code()).isEqualTo(expected));
    }
}
