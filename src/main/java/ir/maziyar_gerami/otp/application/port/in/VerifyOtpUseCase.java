package ir.maziyar_gerami.otp.application.port.in;

public interface VerifyOtpUseCase {
    VerifyOtpResult verify(VerifyOtpCommand command);
}
