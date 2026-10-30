package ir.maziyar_gerami.otp.application.service.requestotp;

import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpResult;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpUseCase;
import ir.maziyar_gerami.otp.application.port.in.requestotp.OtpDeliveryMode;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpCodec;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpGeneratorPort;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpSenderPort;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpStorePort;
import ir.maziyar_gerami.otp.application.port.out.ratelimit.RateLimiterPort;
import ir.maziyar_gerami.otp.domain.*;
import java.time.Clock;
import java.util.UUID;
import java.util.Objects;

public final class RequestOtpService implements RequestOtpUseCase {
    private final OtpStorePort store;
    private final OtpSenderPort sender;
    private final OtpGeneratorPort generator;
    private final RateLimiterPort limiter;
    private final OtpCodec codec;
    private final OtpPolicy policy;
    private final Clock clock;
    private final OtpDeliveryMode deliveryMode;

    public RequestOtpService(OtpStorePort store, OtpSenderPort sender, OtpGeneratorPort generator,
                             RateLimiterPort limiter, OtpCodec codec, OtpPolicy policy, Clock clock) {
        this(store, sender, generator, limiter, codec, policy, clock, OtpDeliveryMode.SMS);
    }

    public RequestOtpService(OtpStorePort store, OtpSenderPort sender, OtpGeneratorPort generator,
                             RateLimiterPort limiter, OtpCodec codec, OtpPolicy policy, Clock clock,
                             OtpDeliveryMode deliveryMode) {
        this.store = store;
        this.sender = sender;
        this.generator = generator;
        this.limiter = limiter;
        this.codec = codec;
        this.policy = policy;
        this.clock = clock;
        this.deliveryMode = Objects.requireNonNull(deliveryMode);
    }

    @Override
    public RequestOtpResult request(RequestOtpCommand command) {
        var phone = command.phoneNumber();
        String version = UUID.randomUUID().toString();
        var admission = limiter.acquire(phone, version, policy.resendCooldown(),
                policy.maxRequests(), policy.rateLimitWindow());
        switch (admission.status()) {
            case COOLDOWN -> throw new OtpException(ErrorCode.OTP_COOLDOWN_ACTIVE,
                    "Wait before requesting another OTP.", admission.retryAfterSeconds());
            case RATE_LIMITED -> throw new OtpException(ErrorCode.OTP_RATE_LIMITED,
                    "OTP request limit reached.", admission.retryAfterSeconds());
            case ALLOWED -> { }
        }
        try {
            OtpCode code = generator.generate(policy.codeLength());
            if (!store.save(phone, new StoredOtp(version, codec.encode(code), 0, clock.instant()), policy.ttl())) {
                throw new OtpException(ErrorCode.OTP_SERVICE_UNAVAILABLE, "OTP issuance reservation expired.");
            }
            if (deliveryMode == OtpDeliveryMode.DEVELOPMENT_RESPONSE) {
                return new RequestOtpResult(policy.ttl().toSeconds(), policy.resendCooldown().toSeconds(), code);
            }
            sender.send(phone, code, policy.ttl());
            return new RequestOtpResult(policy.ttl().toSeconds(), policy.resendCooldown().toSeconds());
        } catch (RuntimeException failure) {
            // Do not delete a newer issuance or reset its cooldown; retain the rate-limit charge.
            try {
                store.deleteIfVersion(phone, version);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            try {
                limiter.releaseCooldown(phone, version);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
