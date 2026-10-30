package ir.maziyar_gerami.otp.adapter.out.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

final class RedisScripts {
    private RedisScripts() {}

    static <T> DefaultRedisScript<T> load(String name, Class<T> resultType) {
        var script = new DefaultRedisScript<T>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(resultType);
        return script;
    }
}
