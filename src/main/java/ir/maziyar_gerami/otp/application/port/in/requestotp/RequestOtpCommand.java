package ir.maziyar_gerami.otp.application.port.in.requestotp;

import ir.maziyar_gerami.otp.domain.PhoneNumber;

public record RequestOtpCommand(PhoneNumber phoneNumber, Integer codeLength) {
    public RequestOtpCommand(PhoneNumber phoneNumber) {
        this(phoneNumber, null);
    }
}
