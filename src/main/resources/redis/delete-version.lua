if redis.call('HGET', KEYS[1], 'version') == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
