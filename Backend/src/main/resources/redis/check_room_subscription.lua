-- Read-only check of one Redis instant; this does not reserve membership.
if redis.call('TYPE', KEYS[1]).ok ~= 'hash'
    or redis.call('TYPE', KEYS[2]).ok ~= 'set'
    or redis.call('TYPE', KEYS[3]).ok ~= 'hash' then
    return 0
end
if redis.call('HGET', KEYS[3], ARGV[1]) ~= ARGV[2] then
    return 0
end
return redis.call('SISMEMBER', KEYS[2], ARGV[1])
