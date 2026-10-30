package ir.maziyar_gerami.otp.domain;

public record PhoneNumber(String value) {
    public PhoneNumber {
        if (value == null || !value.matches("\\+[1-9][0-9]{1,14}")) {
            throw new IllegalArgumentException("Phone number must use E.164 format.");
        }
    }

    public String masked() {
        return "+***" + value.substring(Math.max(1, value.length() - 2));
    }

    @Override
    public String toString() {
        return masked();
    }
}
