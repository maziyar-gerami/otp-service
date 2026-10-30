package ir.maziyar_gerami.otp.bootstrap.config;

import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sms")
public record SmsProperties(@NotNull URI baseUrl,
                            @NotBlank @Pattern(regexp = "/[a-zA-Z0-9/_-]*") String sendPath,
                            String apiKey, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
    @AssertTrue(message = "SMS URL must be an absolute HTTP(S) URL without credentials, query or fragment")
    public boolean isUrlValid() {
        return baseUrl != null && ("https".equals(baseUrl.getScheme()) || "http".equals(baseUrl.getScheme()))
                && baseUrl.getHost() != null && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null && baseUrl.getFragment() == null;
    }

    @AssertTrue(message = "SMS timeouts must be between 1ms and 30s")
    public boolean isTimeoutsValid() {
        return validTimeout(connectTimeout) && validTimeout(readTimeout);
    }

    private static boolean validTimeout(Duration duration) {
        return duration != null && duration.compareTo(Duration.ofMillis(1)) >= 0
                && duration.compareTo(Duration.ofSeconds(30)) <= 0;
    }

    @Override
    public String toString() {
        return "SmsProperties[REDACTED]";
    }
}
