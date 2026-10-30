package ir.maziyar_gerami.otp.application.port.out;

import ir.maziyar_gerami.otp.domain.PhoneNumber;
import java.time.Duration;

public interface RateLimiterPort {
    Admission acquire(PhoneNumber phone, String reservation, Duration cooldown, int maxRequests, Duration window);
    void releaseCooldown(PhoneNumber phone, String reservation);

    enum Status { ALLOWED, COOLDOWN, RATE_LIMITED }

    record Admission(Status status, long retryAfterSeconds) {}
}
