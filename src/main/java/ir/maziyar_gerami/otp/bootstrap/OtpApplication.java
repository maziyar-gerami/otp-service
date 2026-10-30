package ir.maziyar_gerami.otp.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ir.maziyar_gerami.otp")
public class OtpApplication {
    public static void main(String[] args) {
        SpringApplication.run(OtpApplication.class, args);
    }
}
