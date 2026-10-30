package ir.maziyar_gerami.otp.domain;

import java.time.Instant;
import java.util.Objects;

public record StoredOtp(String version, String encodedCode, int attempts, Instant issuedAt) {

    public StoredOtp {
        Objects.requireNonNull(version);
        Objects.requireNonNull(encodedCode);
        Objects.requireNonNull(issuedAt);
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempts cannot be negative.");
        }
    }

    @Override
    public String toString() {
        return "StoredOtp[version=" + version + ", attempts=" + attempts + "]";
    }
}
