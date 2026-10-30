-- Commit only if this issuance still owns its reservation.
if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
redis.call('HSET', KEYS[1], 'version', ARGV[1], 'code', ARGV[2],
           'attempts', ARGV[3], 'issuedAt', ARGV[4], 'codeLength', ARGV[6])
redis.call('PEXPIRE', KEYS[1], ARGV[5])
return 1
