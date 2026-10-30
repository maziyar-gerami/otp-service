package ir.maziyar_gerami.otp.bootstrap.config;

import ir.maziyar_gerami.otp.domain.OtpPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("otp")
public record OtpProperties(@Min(4) @Max(10) int codeLength, @NotNull Duration ttl,
                            @Min(1) @Max(100) int maxVerifyAttempts, @NotNull Duration resendCooldown,
                            @NotNull @Valid RateLimit rateLimit,
                            @NotBlank @Pattern(regexp = "[a-zA-Z0-9:_-]+") String keyPrefix) {
    public record RateLimit(@Min(1) @Max(10000) int maxRequests, @NotNull Duration window) {
        @AssertTrue(message = "rate-limit.window must be whole seconds between 1 second and 7 days")
        public boolean isWindowValid() { return validDuration(window); }
    }

    @AssertTrue(message = "OTP durations must be whole seconds between 1 second and 7 days")
    public boolean isDurationsValid() { return validDuration(ttl) && validDuration(resendCooldown); }

    private static boolean validDuration(Duration value) {
        return value != null && value.compareTo(Duration.ofSeconds(1)) >= 0
                && value.compareTo(Duration.ofDays(7)) <= 0 && value.getNano() == 0;
    }

    public OtpPolicy toPolicy() {
        return new OtpPolicy(codeLength, ttl, maxVerifyAttempts, resendCooldown,
                rateLimit.maxRequests(), rateLimit.window());
    }
}
