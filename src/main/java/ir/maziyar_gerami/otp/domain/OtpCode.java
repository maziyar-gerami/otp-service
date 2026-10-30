package ir.maziyar_gerami.otp.domain;

public record OtpCode(String value) {

    public OtpCode {
        if (value == null || !value.matches("[0-9]{4,10}"))
            throw new IllegalArgumentException("OTP must contain 4 to 10 ASCII digits.");

    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
