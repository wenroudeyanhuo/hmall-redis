---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 16574.
--- DateTime: 2024/11/5 16:27
-- 参数列表

--优惠券id
local voucherId=ARGV[1]
--用户id
local userId=ARGV[2]

-- 数据key
--库存key
local stockKey='seckill:stock:' .. voucherId
--表示拼接操作
--订单key
local orderKey='seckill:order:' .. voucherId

--脚本业务
--判断库存是否充足  get stockkey
--因为redis查到的数据是子符串类型，所以如果要比较还是转一下比较好
if(tonumber(redis.call('get',stockKey))<=0) then
--   库存不足，返回1
    return 1
end
if(redis.call('sismember',orderKey,userId)==1) then
--    存在，说明是重复下单 返回2
    return 2
end
--扣库存
redis.call('incrby',stockKey,-1)
--说明是重复下单
redis.call('sadd',orderKey,userId)
return 0