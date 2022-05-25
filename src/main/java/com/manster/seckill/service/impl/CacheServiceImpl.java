package com.manster.seckill.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.manster.seckill.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * @author wxh
 * @date 2022-05-13 13:17
 */
@Service
public class CacheServiceImpl implements CacheService {

    private Cache<String, Object> commconCache = null;

    @PostConstruct
    public void init() {
        commconCache = CacheBuilder.newBuilder()
                //设置缓存容器的初始容量为10
                .initialCapacity(10)
                //设置缓存中最大可以存储100个key，超过100个之后会按照LRU的策略移除缓存项
                .maximumSize(100)
                //设置写缓存后多少秒过期
                .expireAfterWrite(60, TimeUnit.SECONDS).build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commconCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commconCache.getIfPresent(key);
    }

    @Override
    public void removeCommonCache(String key) {
        commconCache.invalidate(key);
    }
}
