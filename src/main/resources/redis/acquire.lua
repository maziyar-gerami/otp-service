local cooldown = redis.call('PTTL', KEYS[1])
if cooldown > 0 then return {1, cooldown} end
local count = tonumber(redis.call('GET', KEYS[2]) or '0')
if count >= tonumber(ARGV[3]) then
    return {2, redis.call('PTTL', KEYS[2])}
end
local updated = redis.call('INCR', KEYS[2])
if updated == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[4]) end
redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
return {0, 0}
