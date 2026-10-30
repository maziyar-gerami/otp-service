package ir.maziyar_gerami.otp.adapter.out.redis;

import ir.maziyar_gerami.otp.application.port.out.otp.OtpStorePort;
import ir.maziyar_gerami.otp.domain.PhoneNumber;
import ir.maziyar_gerami.otp.domain.StoredOtp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisOtpStore implements OtpStorePort {
    private static final DefaultRedisScript<Long> SAVE = script("save-otp");
    private static final DefaultRedisScript<Long> VERIFY = script("verify-otp");
    private static final DefaultRedisScript<Long> DELETE = script("delete-version");
    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    public RedisOtpStore(StringRedisTemplate redis, RedisKeys keys) {
        this.redis = redis;
        this.keys = keys;
    }

    @Override
    public boolean save(PhoneNumber phone, StoredOtp otp, Duration ttl) {
        Long result = redis.execute(SAVE, List.of(keys.otp(phone), keys.cooldown(phone)),
                otp.version(), otp.encodedCode(), Integer.toString(otp.attempts()),
                otp.issuedAt().toString(), Long.toString(ttl.toMillis()));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public Optional<StoredOtp> find(PhoneNumber phone) {
        var values = redis.<String, String>opsForHash().entries(keys.otp(phone));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StoredOtp(values.get("version"), values.get("code"),
                Integer.parseInt(values.get("attempts")), Instant.parse(values.get("issuedAt"))));
    }

    @Override
    public VerificationOutcome completeVerification(PhoneNumber phone, StoredOtp snapshot,
                                                     boolean matched, int maxAttempts) {
        Long result = redis.execute(VERIFY, List.of(keys.otp(phone)), snapshot.version(),
                Integer.toString(snapshot.attempts()), matched ? "1" : "0", Integer.toString(maxAttempts));
        if (result == null) {
            throw new IllegalStateException("Redis verification returned no result.");
        }
        return switch (result.intValue()) {
            case 1 -> VerificationOutcome.VERIFIED;
            case 2 -> VerificationOutcome.INVALID;
            case 3 -> VerificationOutcome.ATTEMPTS_EXCEEDED;
            case 0 -> VerificationOutcome.NOT_FOUND;
            case 4 -> VerificationOutcome.RETRY;
            default -> throw new IllegalStateException("Unknown Redis verification result.");
        };
    }

    @Override
    public void deleteIfVersion(PhoneNumber phone, String version) {
        redis.execute(DELETE, List.of(keys.otp(phone)), version);
    }

    private static DefaultRedisScript<Long> script(String name) {
        return RedisScripts.load(name, Long.class);
    }
}
