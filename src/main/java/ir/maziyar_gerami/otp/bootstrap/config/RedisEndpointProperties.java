package ir.maziyar_gerami.otp.bootstrap.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Validate the endpoint while Spring Boot owns Redis client auto-configuration.
@Validated
@ConfigurationProperties("spring.data.redis")
public record RedisEndpointProperties(@NotBlank String host, @Min(1) @Max(65535) int port, String password) {
    @Override
    public String toString() { return "RedisEndpointProperties[REDACTED]"; }
}
