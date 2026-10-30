package ir.maziyar_gerami.otp.adapter.out.redis;

import ir.maziyar_gerami.otp.domain.PhoneNumber;

public final class RedisKeys {
    private final String prefix;

    public RedisKeys(String prefix) {
        if (prefix == null || !prefix.matches("[a-zA-Z0-9:_-]+")) {
            throw new IllegalArgumentException("Redis prefix contains unsupported characters.");
        }
        this.prefix = prefix;
    }

    public String otp(PhoneNumber phone) { return key(phone, "code"); }
    public String cooldown(PhoneNumber phone) { return key(phone, "cooldown"); }
    public String rate(PhoneNumber phone) { return key(phone, "rate"); }

    private String key(PhoneNumber phone, String suffix) {
        // A shared hash tag keeps all keys for a phone in one Redis Cluster slot.
        return prefix + "{" + phone.value() + "}:" + suffix;
    }
}
