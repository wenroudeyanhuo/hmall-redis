package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeByList() {
        String shopTypeJson = stringRedisTemplate.opsForValue().get("shop:list");

        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3.存在，直接返回
            List<ShopType> shopTypes = JSONUtil.parseArray(shopTypeJson).toList(ShopType.class);
            return Result.ok(shopTypes);
        }

        // 4.不存在，查询数据库
        List<ShopType> list = list();

        // 5.不存在，返回错误
        if (list == null) {
            return Result.fail("店铺类型不存在");
        }

        // 6.存在，写入redis
        stringRedisTemplate.opsForValue().set("shop:list", JSONUtil.toJsonStr(list));

        // 返回
        return Result.ok(list);


    }
}
