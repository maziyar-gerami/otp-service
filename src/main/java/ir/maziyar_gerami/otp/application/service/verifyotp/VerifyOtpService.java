package ir.maziyar_gerami.otp.application.service.verifyotp;

import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpResult;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpUseCase;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpCodec;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpStorePort;
import ir.maziyar_gerami.otp.domain.*;

public final class VerifyOtpService implements VerifyOtpUseCase {
    private static final int MAX_CONTENTION_RETRIES = 32;
    private final OtpStorePort store;
    private final OtpCodec codec;
    private final OtpPolicy policy;

    public VerifyOtpService(OtpStorePort store, OtpCodec codec, OtpPolicy policy) {
        this.store = store;
        this.codec = codec;
        this.policy = policy;
    }

    @Override
    public VerifyOtpResult verify(VerifyOtpCommand command) {
        for (int retry = 0; retry < MAX_CONTENTION_RETRIES; retry++) {
            var snapshot = store.find(command.phoneNumber()).orElseThrow(VerifyOtpService::notFound);
            if (command.code().value().length() != snapshot.codeLength()) {
                throw new OtpException(ErrorCode.VALIDATION_ERROR, "Code length does not match the issued OTP length.");
            }
            boolean matched = codec.matches(command.code(), snapshot.encodedCode());
            var outcome = store.completeVerification(command.phoneNumber(), snapshot, matched, policy.maxVerifyAttempts());
            switch (outcome) {
                case VERIFIED -> { return new VerifyOtpResult(true); }
                case INVALID -> throw new OtpException(ErrorCode.OTP_INVALID, "The OTP code is incorrect.");
                case ATTEMPTS_EXCEEDED -> throw new OtpException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                        "Maximum verification attempts reached. Request a new OTP.");
                case NOT_FOUND -> throw notFound();
                case RETRY -> { }
            }
        }
        throw new OtpException(ErrorCode.OTP_SERVICE_UNAVAILABLE, "Verification is busy. Try again.");
    }

    private static OtpException notFound() {
        return new OtpException(ErrorCode.OTP_NOT_FOUND_OR_EXPIRED, "OTP was not found or has expired.");
    }
}
