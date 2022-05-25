package com.manster.seckill.service;

/**
 * @author wxh
 * @date 2022-05-13 13:15
 */
//封装本地缓存操作类
public interface CacheService {
    //村方法
    void setCommonCache(String key,Object value);

    //取方法
    Object getFromCommonCache(String key);

    //删掉缓存
    void removeCommonCache(String key);
}
