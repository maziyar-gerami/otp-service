package ir.maziyar_gerami.otp.adapter.out.security;

import ir.maziyar_gerami.otp.domain.OtpCode;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityTest {
    @Test
    void generatesConfiguredAsciiDigitsIncludingLeadingZero() {
        var random = mock(SecureRandom.class);
        when(random.nextInt(10)).thenReturn(0, 1, 2, 3, 4, 5);
        assertThat(new SecureRandomOtpGenerator(random).generate(6).value()).isEqualTo("012345");
        verify(random, times(6)).nextInt(10);
        var generator = new SecureRandomOtpGenerator(new SecureRandom());
        for (int length = 4; length <= 10; length++) {
            assertThat(generator.generate(length).value()).matches("[0-9]{" + length + "}");
        }
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(3));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(11));
    }

    @Test
    void plaintextCodecUsesConstantTimePrimitiveAndPreservesLeadingZeros() {
        var codec = new PlainTextOtpCodec();
        var code = new OtpCode("001234");
        assertThat(codec.encode(code)).isEqualTo("001234");
        assertThat(codec.matches(code, "001234")).isTrue();
        assertThat(codec.matches(code, "001235")).isFalse();
        assertThat(codec.matches(code, "0012340")).isFalse();
        assertThat(codec.matches(code, "")).isFalse();
    }
}
