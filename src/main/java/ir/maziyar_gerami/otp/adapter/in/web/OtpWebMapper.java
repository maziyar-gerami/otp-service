package ir.maziyar_gerami.otp.adapter.in.web;

import ir.maziyar_gerami.otp.application.port.in.*;
import ir.maziyar_gerami.otp.domain.*;

public final class OtpWebMapper {
    private OtpWebMapper() {}

    public static RequestOtpCommand command(RequestOtpRequest request) {
        return new RequestOtpCommand(new PhoneNumber(request.phoneNumber()));
    }

    public static VerifyOtpCommand command(VerifyOtpRequest request) {
        return new VerifyOtpCommand(new PhoneNumber(request.phoneNumber()), new OtpCode(request.code()));
    }

    public static RequestOtpResponse response(RequestOtpResult result) {
        return new RequestOtpResponse(result.expiresInSeconds(), result.resendAvailableInSeconds());
    }

    public static VerifyOtpResponse response(VerifyOtpResult result) {
        return new VerifyOtpResponse(result.verified());
    }
}
