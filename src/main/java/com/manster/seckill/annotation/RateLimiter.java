package com.manster.seckill.annotation;

import com.manster.seckill.Enum.LimitType;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * @author wxh
 * @date 2022-05-31 21:19
 *
 * 限流注解，添加了 {@link AliasFor} 必须通过 {@link AnnotationUtils} 获取，才会生效
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
// redis限流方案
// 哪个接口需要限流，就在哪个接口上添加 @RateLimiter 注解，然后配置相关参数即可
public @interface RateLimiter {
    long DEFAULT_REQUEST = 10;
    /**
     * 限流key
     * 这个仅仅是一个前缀，将来完整的 key 是这个前缀再加上接口方法的完整路径，共同组成限流 key，这个 key 将被存入到 Redis 中
     */
    String key() default "";

    /**
     * 超时时长，默认1分钟
     */
    long timeout() default 1;

    /**
     * 超时时间单位，默认 分钟
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * 限流次数，最大请求数
     */
    @AliasFor("value") long max() default DEFAULT_REQUEST;
    /**
     * max 最大请求数
     */
    @AliasFor("max") long value() default DEFAULT_REQUEST;

    /**
     * 限流类型
     */
    LimitType limitType() default LimitType.DEFAULT;
}
