package ir.maziyar_gerami.otp.application.port.in.requestotp;

public interface RequestOtpUseCase {
    RequestOtpResult request(RequestOtpCommand command);
}
