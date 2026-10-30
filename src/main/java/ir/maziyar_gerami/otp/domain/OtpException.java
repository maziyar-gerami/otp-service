package ir.maziyar_gerami.otp.domain;

public class OtpException extends RuntimeException {
    private final ErrorCode code;
    private final long retryAfterSeconds;

    public OtpException(ErrorCode code, String message) {
        this(code, message, 0);
    }

    public OtpException(ErrorCode code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
