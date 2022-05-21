package com.manster.seckill.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解，添加了 {@link AliasFor} 必须通过 {@link AnnotationUtils} 获取，才会生效
 *
 * @author wxh
 * @date 2022-05-21 14:33
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyRateLimiter {
    int NOT_LIMITED = 0;

    @AliasFor("qps") double value() default NOT_LIMITED;

    @AliasFor("value") double qps() default NOT_LIMITED;

    //超时时长，即获取令牌最大等待时间
    int timeout() default 0;

    //超时时间的单位
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
