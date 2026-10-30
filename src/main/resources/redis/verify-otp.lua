-- Comparison happens in Java. Apply its result to exactly the compared snapshot.
if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
if redis.call('HGET', KEYS[1], 'version') ~= ARGV[1] or
   redis.call('HGET', KEYS[1], 'attempts') ~= ARGV[2] then return 4 end
if ARGV[3] == '1' then
    redis.call('DEL', KEYS[1])
    return 1
end
local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if attempts >= tonumber(ARGV[4]) then
    redis.call('DEL', KEYS[1])
    return 3
end
return 2
