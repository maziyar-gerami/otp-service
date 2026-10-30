package ir.maziyar_gerami.otp.application.port.out.otp;

import ir.maziyar_gerami.otp.domain.PhoneNumber;
import ir.maziyar_gerami.otp.domain.StoredOtp;
import java.time.Duration;
import java.util.Optional;

public interface OtpStorePort {
    // Save only while this issuance still owns its cooldown reservation.
    boolean save(PhoneNumber phone, StoredOtp otp, Duration ttl);
    Optional<StoredOtp> find(PhoneNumber phone);
    VerificationOutcome completeVerification(PhoneNumber phone, StoredOtp snapshot,
                                             boolean matched, int maxAttempts);
    void deleteIfVersion(PhoneNumber phone, String version);

    enum VerificationOutcome {
        VERIFIED, INVALID, ATTEMPTS_EXCEEDED, NOT_FOUND, RETRY
    }
}
