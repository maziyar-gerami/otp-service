package ir.maziyar_gerami.otp.adapter.out.redis;

import ir.maziyar_gerami.otp.adapter.out.security.PlainTextOtpCodec;
import ir.maziyar_gerami.otp.application.port.in.VerifyOtpCommand;
import ir.maziyar_gerami.otp.application.port.out.*;
import ir.maziyar_gerami.otp.application.service.VerifyOtpService;
import ir.maziyar_gerami.otp.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RedisAdaptersIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    static LettuceConnectionFactory connection;
    static StringRedisTemplate redis;
    RedisKeys keys;
    RedisOtpStore store;
    RedisRateLimiter limiter;
    final PhoneNumber phone = new PhoneNumber("+989121234567");
    final Duration minute = Duration.ofMinutes(1);

    @BeforeAll
    static void connect() {
        connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void close() { connection.destroy(); }

    @BeforeEach
    void setUp() {
        keys = new RedisKeys("test:" + UUID.randomUUID() + ":");
        store = new RedisOtpStore(redis, keys);
        limiter = new RedisRateLimiter(redis, keys);
    }

    StoredOtp issue(String version, Duration ttl) {
        assertThat(limiter.acquire(phone, version, minute, 100, minute).status()).isEqualTo(RateLimiterPort.Status.ALLOWED);
        var otp = new StoredOtp(version, "123456", 0, Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(store.save(phone, otp, ttl)).isTrue();
        return otp;
    }

    VerifyOtpService verifier(int attempts) {
        return new VerifyOtpService(store, new PlainTextOtpCodec(),
                new OtpPolicy(6, minute, attempts, minute, 100, minute));
    }

    @Test
    void storesPlaintextWithTtlAndExpires() {
        var otp = issue("v1", Duration.ofMillis(300));
        assertThat(store.find(phone)).contains(otp);
        assertThat(redis.getExpire(keys.otp(phone), TimeUnit.MILLISECONDS)).isBetween(1L, 300L);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(store.find(phone)).isEmpty());
        assertThat(store.completeVerification(phone, otp, true, 3)).isEqualTo(OtpStorePort.VerificationOutcome.NOT_FOUND);
    }

    @Test
    void correctVerificationDeletesImmediatelyAndIsSingleUse() {
        issue("v1", minute);
        assertThat(verifier(3).verify(new VerifyOtpCommand(phone, new OtpCode("123456"))).verified()).isTrue();
        assertThat(store.find(phone)).isEmpty();
        assertThatThrownBy(() -> verifier(3).verify(new VerifyOtpCommand(phone, new OtpCode("123456"))))
                .isInstanceOfSatisfying(OtpException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.OTP_NOT_FOUND_OR_EXPIRED));
    }

    @Test
    void wrongAttemptsDoNotExtendTtlAndDeleteAtLimit() {
        var otp = issue("v1", Duration.ofSeconds(5));
        long before = redis.getExpire(keys.otp(phone), TimeUnit.MILLISECONDS);
        assertThat(store.completeVerification(phone, otp, false, 3)).isEqualTo(OtpStorePort.VerificationOutcome.INVALID);
        var second = store.find(phone).orElseThrow();
        assertThat(second.attempts()).isEqualTo(1);
        assertThat(redis.getExpire(keys.otp(phone), TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(before);
        assertThat(store.completeVerification(phone, second, false, 3)).isEqualTo(OtpStorePort.VerificationOutcome.INVALID);
        assertThat(store.completeVerification(phone, store.find(phone).orElseThrow(), false, 3))
                .isEqualTo(OtpStorePort.VerificationOutcome.ATTEMPTS_EXCEEDED);
        assertThat(store.find(phone)).isEmpty();
    }

    @Test
    void replacementResetsAttemptsAndStaleOperationsCannotTouchIt() {
        var old = issue("old", minute);
        store.completeVerification(phone, old, false, 3);
        limiter.releaseCooldown(phone, "old");
        var replacement = issue("new", minute);
        store.deleteIfVersion(phone, "old");
        limiter.releaseCooldown(phone, "old");
        assertThat(store.find(phone)).contains(replacement);
        assertThat(store.completeVerification(phone, old, true, 3)).isEqualTo(OtpStorePort.VerificationOutcome.RETRY);
        assertThat(limiter.acquire(phone, "third", minute, 100, minute).status()).isEqualTo(RateLimiterPort.Status.COOLDOWN);
        store.deleteIfVersion(phone, "new");
        assertThat(store.find(phone)).isEmpty();
    }

    @Test
    void staleAttemptSnapshotRequiresFreshComparison() {
        var otp = issue("v1", minute);
        store.completeVerification(phone, otp, false, 3);
        assertThat(store.completeVerification(phone, otp, true, 3)).isEqualTo(OtpStorePort.VerificationOutcome.RETRY);
        assertThat(store.find(phone).orElseThrow().attempts()).isEqualTo(1);
    }

    @Test
    void cooldownAndRateWindowHaveIndependentTtls() {
        var cooldown = Duration.ofMillis(150);
        var window = Duration.ofMillis(800);
        assertThat(limiter.acquire(phone, "one", cooldown, 2, window).status()).isEqualTo(RateLimiterPort.Status.ALLOWED);
        var blocked = limiter.acquire(phone, "other", cooldown, 2, window);
        assertThat(blocked.status()).isEqualTo(RateLimiterPort.Status.COOLDOWN);
        assertThat(blocked.retryAfterSeconds()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(2)).until(() -> !Boolean.TRUE.equals(redis.hasKey(keys.cooldown(phone))));
        assertThat(limiter.acquire(phone, "two", cooldown, 2, window).status()).isEqualTo(RateLimiterPort.Status.ALLOWED);
        limiter.releaseCooldown(phone, "two");
        var limited = limiter.acquire(phone, "three", cooldown, 2, window);
        assertThat(limited.status()).isEqualTo(RateLimiterPort.Status.RATE_LIMITED);
        assertThat(limited.retryAfterSeconds()).isEqualTo(1);
        await().atMost(Duration.ofSeconds(3)).until(() -> !Boolean.TRUE.equals(redis.hasKey(keys.rate(phone))));
        assertThat(limiter.acquire(phone, "next-window", cooldown, 2, window).status()).isEqualTo(RateLimiterPort.Status.ALLOWED);
    }

    @Test
    void expiredOrStaleReservationCannotSave() {
        var otp = new StoredOtp("no-reservation", "123456", 0, Instant.EPOCH);
        assertThat(store.save(phone, otp, minute)).isFalse();
        assertThat(store.find(phone)).isEmpty();
        issue("owner", minute);
        assertThat(store.save(phone, otp, minute)).isFalse();
    }

    @Test
    void concurrentRequestsAllowOnlyOneDuringCooldown() throws Exception {
        var outcomes = concurrent(32, () -> limiter.acquire(phone, UUID.randomUUID().toString(), minute, 3, minute).status());
        assertThat(outcomes.stream().filter(x -> x == RateLimiterPort.Status.ALLOWED).count()).isEqualTo(1);
        assertThat(redis.opsForValue().get(keys.rate(phone))).isEqualTo("1");
        assertThat(redis.getExpire(keys.rate(phone))).isPositive();
    }

    @Test
    void concurrentRateAdmissionsNeverExceedLimit() throws Exception {
        var outcomes = concurrent(32, () -> {
            String version = UUID.randomUUID().toString();
            var admission = limiter.acquire(phone, version, minute, 3, minute);
            if (admission.status() == RateLimiterPort.Status.ALLOWED) {
                limiter.releaseCooldown(phone, version);
            }
            return admission.status();
        });
        assertThat(outcomes.stream().filter(x -> x == RateLimiterPort.Status.ALLOWED).count()).isLessThanOrEqualTo(3);
        assertThat(Integer.parseInt(redis.opsForValue().get(keys.rate(phone)))).isLessThanOrEqualTo(3);
    }

    @Test
    void concurrentCorrectVerificationsHaveExactlyOneWinner() throws Exception {
        issue("v1", minute);
        var outcomes = concurrent(24, () -> {
            try {
                return verifier(3).verify(new VerifyOtpCommand(phone, new OtpCode("123456"))).verified();
            } catch (OtpException e) {
                return e.code();
            }
        });
        assertThat(outcomes.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1);
        assertThat(outcomes).allMatch(x -> Boolean.TRUE.equals(x) || x == ErrorCode.OTP_NOT_FOUND_OR_EXPIRED);
        assertThat(store.find(phone)).isEmpty();
    }

    @Test
    void concurrentWrongVerificationsDoNotLoseAttemptIncrements() throws Exception {
        issue("v1", minute);
        var outcomes = concurrent(24, () -> {
            try {
                verifier(100).verify(new VerifyOtpCommand(phone, new OtpCode("000000")));
                return "unexpected success";
            } catch (OtpException e) {
                return e.code();
            }
        });
        assertThat(outcomes).containsOnly(ErrorCode.OTP_INVALID);
        assertThat(store.find(phone).orElseThrow().attempts()).isEqualTo(24);
    }

    private static List<Object> concurrent(int count, Callable<Object> action) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = IntStream.range(0, count).mapToObj(i -> executor.submit(() -> {
                start.await();
                return action.call();
            })).toList();
            start.countDown();
            var values = new ArrayList<Object>();
            for (var future : futures) {
                values.add(future.get(10, TimeUnit.SECONDS));
            }
            return values;
        }
    }

    @Test
    void keysShareClusterHashTagAndValidatePrefix() {
        assertThat(keys.otp(phone)).contains("{+989121234567}");
        assertThat(keys.cooldown(phone)).contains("{+989121234567}");
        assertThat(keys.rate(phone)).contains("{+989121234567}");
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisKeys("bad{prefix"));
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisKeys(null));
    }
}
