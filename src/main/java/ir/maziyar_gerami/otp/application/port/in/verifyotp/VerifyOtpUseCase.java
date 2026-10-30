package ir.maziyar_gerami.otp.application.port.in.verifyotp;

public interface VerifyOtpUseCase {
    VerifyOtpResult verify(VerifyOtpCommand command);
}
