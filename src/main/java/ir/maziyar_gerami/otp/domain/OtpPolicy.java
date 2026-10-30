package ir.maziyar_gerami.otp.domain;

import java.time.Duration;
import java.util.Objects;

public record OtpPolicy(int codeLength, Duration ttl, int maxVerifyAttempts,
                        Duration resendCooldown, int maxRequests, Duration rateLimitWindow) {
    public OtpPolicy {
        if (codeLength < 4 || codeLength > 10 || maxVerifyAttempts < 1 || maxRequests < 1) {
            throw new IllegalArgumentException("Invalid OTP policy limits.");
        }
        requireSeconds(ttl);
        requireSeconds(resendCooldown);
        requireSeconds(rateLimitWindow);
    }

    private static void requireSeconds(Duration duration) {
        Objects.requireNonNull(duration, "Duration is required.");
        if (duration.isNegative() || duration.isZero() || duration.getNano() != 0
                || duration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Durations must be whole seconds between 1 second and 7 days.");
        }
    }
}
