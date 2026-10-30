package ir.maziyar_gerami.otp.adapter.out.redis;

import ir.maziyar_gerami.otp.application.port.out.RateLimiterPort;
import ir.maziyar_gerami.otp.domain.PhoneNumber;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisRateLimiter implements RateLimiterPort {
    private static final DefaultRedisScript<List> ACQUIRE =
            RedisScripts.load("acquire", List.class);
    private static final DefaultRedisScript<Long> RELEASE =
            RedisScripts.load("release-cooldown", Long.class);
    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    public RedisRateLimiter(StringRedisTemplate redis, RedisKeys keys) {
        this.redis = redis;
        this.keys = keys;
    }

    @Override
    public Admission acquire(PhoneNumber phone, String reservation, Duration cooldown, int maxRequests, Duration window) {
        List<?> result = redis.execute(ACQUIRE, List.of(keys.cooldown(phone), keys.rate(phone)),
                reservation, Long.toString(cooldown.toMillis()), Integer.toString(maxRequests),
                Long.toString(window.toMillis()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis admission returned no result.");
        }
        int status = ((Number) result.get(0)).intValue();
        long millis = ((Number) result.get(1)).longValue();
        Status admission = switch (status) {
            case 0 -> Status.ALLOWED;
            case 1 -> Status.COOLDOWN;
            case 2 -> Status.RATE_LIMITED;
            default -> throw new IllegalStateException("Unknown Redis admission result.");
        };
        return new Admission(admission, admission == Status.ALLOWED ? 0 : Math.max(1, (millis + 999) / 1000));
    }

    @Override
    public void releaseCooldown(PhoneNumber phone, String reservation) {
        redis.execute(RELEASE, List.of(keys.cooldown(phone)), reservation);
    }
}
