package ir.maziyar_gerami.otp.domain;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class DomainTest {
    @Test
    void validValuesAreImmutableAndRedacted() {
        var phone = new PhoneNumber("+989121234567");
        assertThat(phone.value()).isEqualTo("+989121234567");
        assertThat(phone.masked()).isEqualTo("+***67");
        assertThat(phone.toString()).doesNotContain("91212345");
        assertThat(new PhoneNumber("+12").masked()).isEqualTo("+***12");
        var code = new OtpCode("001234");
        assertThat(code.value()).isEqualTo("001234");
        assertThat(code.toString()).isEqualTo("[REDACTED]");
        var otp = new StoredOtp("version", code.value(), 0, Instant.EPOCH);
        assertThat(otp.toString()).doesNotContain(code.value());
        assertThat(otp.issuedAt()).isEqualTo(Instant.EPOCH);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"09121234567", "+012345", "+1", "+1234567890123456", "+98 912", "+１２３"})
    void rejectsInvalidPhone(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new PhoneNumber(value));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "12345678901", "１２３４５６", "abcdef", "123 45"})
    void rejectsInvalidCode(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new OtpCode(value));
    }

    @Test
    void validatesPolicyBounds() {
        assertThat(policy(6, Duration.ofSeconds(120), 3, 3).codeLength()).isEqualTo(6);
        for (int length : new int[]{3, 11}) {
            assertThatIllegalArgumentException().isThrownBy(() -> policy(length, Duration.ofSeconds(1), 3, 3));
        }
        for (Duration ttl : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1),
                Duration.ofMillis(1500), Duration.ofDays(8)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> policy(6, ttl, 3, 3));
        }
        assertThatNullPointerException().isThrownBy(() -> policy(6, null, 3, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> policy(6, Duration.ofSeconds(1), 0, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> policy(6, Duration.ofSeconds(1), 3, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new StoredOtp("v", "123456", -1, Instant.EPOCH));
    }

    private OtpPolicy policy(int length, Duration ttl, int attempts, int requests) {
        return new OtpPolicy(length, ttl, attempts, Duration.ofSeconds(60), requests, Duration.ofMinutes(10));
    }

    @Test
    void exceptionsExposeStableCodeAndRetryDelay() {
        var exception = new OtpException(ErrorCode.OTP_RATE_LIMITED, "Limited", 60);
        assertThat(exception.code()).isEqualTo(ErrorCode.OTP_RATE_LIMITED);
        assertThat(exception.retryAfterSeconds()).isEqualTo(60);
        assertThat(exception.getMessage()).isEqualTo("Limited");
        assertThat(new OtpException(ErrorCode.OTP_INVALID, "Invalid").retryAfterSeconds()).isZero();
    }
}
