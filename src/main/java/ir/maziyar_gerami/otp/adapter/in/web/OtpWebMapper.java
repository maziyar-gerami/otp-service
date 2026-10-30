package ir.maziyar_gerami.otp.adapter.in.web;

import ir.maziyar_gerami.otp.adapter.in.web.requestotp.RequestOtpRequest;
import ir.maziyar_gerami.otp.adapter.in.web.requestotp.RequestOtpResponse;
import ir.maziyar_gerami.otp.adapter.in.web.verifyotp.VerifyOtpRequest;
import ir.maziyar_gerami.otp.adapter.in.web.verifyotp.VerifyOtpResponse;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpResult;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpCommand;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpResult;
import ir.maziyar_gerami.otp.domain.*;

public final class OtpWebMapper {
    private OtpWebMapper() {}

    public static RequestOtpCommand command(RequestOtpRequest request) {
        return new RequestOtpCommand(new PhoneNumber(request.phoneNumber()), request.codeLength());
    }

    public static VerifyOtpCommand command(VerifyOtpRequest request) {
        return new VerifyOtpCommand(new PhoneNumber(request.phoneNumber()), new OtpCode(request.code()));
    }

    public static RequestOtpResponse response(RequestOtpResult result) {
        return new RequestOtpResponse(result.expiresInSeconds(), result.resendAvailableInSeconds(),
                result.code() == null ? null : result.code().value());
    }

    public static VerifyOtpResponse response(VerifyOtpResult result) {
        return new VerifyOtpResponse(result.verified());
    }
}
