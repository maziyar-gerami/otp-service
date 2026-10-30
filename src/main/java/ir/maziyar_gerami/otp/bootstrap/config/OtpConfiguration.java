package ir.maziyar_gerami.otp.bootstrap.config;

import ir.maziyar_gerami.otp.adapter.out.redis.*;
import ir.maziyar_gerami.otp.adapter.out.security.*;
import ir.maziyar_gerami.otp.adapter.out.sms.*;
import ir.maziyar_gerami.otp.application.port.in.requestotp.RequestOtpUseCase;
import ir.maziyar_gerami.otp.application.port.in.verifyotp.VerifyOtpUseCase;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpCodec;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpGeneratorPort;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpSenderPort;
import ir.maziyar_gerami.otp.application.port.out.otp.OtpStorePort;
import ir.maziyar_gerami.otp.application.port.out.ratelimit.RateLimiterPort;
import ir.maziyar_gerami.otp.application.service.requestotp.RequestOtpService;
import ir.maziyar_gerami.otp.application.service.verifyotp.VerifyOtpService;
import ir.maziyar_gerami.otp.domain.OtpPolicy;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OtpProperties.class, SmsProperties.class, RedisEndpointProperties.class})
public class OtpConfiguration {
    @Bean
    public Clock clock() { return Clock.systemUTC(); }

    @Bean
    public OtpPolicy otpPolicy(OtpProperties properties) { return properties.toPolicy(); }

    @Bean
    public RedisKeys redisKeys(OtpProperties properties) { return new RedisKeys(properties.keyPrefix()); }

    @Bean
    public OtpStorePort otpStore(StringRedisTemplate redis, RedisKeys keys) { return new RedisOtpStore(redis, keys); }

    @Bean
    public RateLimiterPort rateLimiter(StringRedisTemplate redis, RedisKeys keys) { return new RedisRateLimiter(redis, keys); }

    @Bean
    public OtpGeneratorPort otpGenerator() { return new SecureRandomOtpGenerator(new SecureRandom()); }

    @Bean
    public OtpCodec otpCodec() { return new PlainTextOtpCodec(); }

    @Bean
    @Profile("dev & !prod")
    public OtpSenderPort loggingOtpSender() { return new LoggingOtpSender(); }

    @Bean
    @Profile("!dev | prod")
    public OtpSenderPort httpOtpSender(RestClient.Builder builder, SmsProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.apiKey().contains("\r") || properties.apiKey().contains("\n")) {
            throw new IllegalArgumentException("SMS_API_KEY is required for the HTTP SMS adapter.");
        }
        var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return new HttpOtpSender(builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(factory).defaultHeader("Authorization", "Bearer " + properties.apiKey()).build(),
                properties.sendPath());
    }

    @Bean
    public RequestOtpUseCase requestOtp(OtpStorePort store, OtpSenderPort sender, OtpGeneratorPort generator,
                                        RateLimiterPort limiter, OtpCodec codec, OtpPolicy policy, Clock clock) {
        return new RequestOtpService(store, sender, generator, limiter, codec, policy, clock);
    }

    @Bean
    public VerifyOtpUseCase verifyOtp(OtpStorePort store, OtpCodec codec, OtpPolicy policy) {
        return new VerifyOtpService(store, codec, policy);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("OTP Service").version("v1")
                .description("Single-use SMS OTP issuance and verification."));
    }
}
