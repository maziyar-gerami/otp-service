package ir.maziyar_gerami.otp.domain;

import java.time.Instant;
import java.util.Objects;

public record StoredOtp(String version, String encodedCode, int attempts, Instant issuedAt, int codeLength) {

    public StoredOtp {
        Objects.requireNonNull(version);
        Objects.requireNonNull(encodedCode);
        Objects.requireNonNull(issuedAt);
        if (attempts < 0) {
            throw new IllegalArgumentException("Attempts cannot be negative.");
        }
        if (codeLength < 4 || codeLength > 10) {
            throw new IllegalArgumentException("OTP length must be between 4 and 10.");
        }
    }

    @Override
    public String toString() {
        return "StoredOtp[version=" + version + ", attempts=" + attempts + "]";
    }
}
