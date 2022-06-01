package com.manster.seckill.aspect;

import cn.hutool.core.util.StrUtil;
import com.manster.seckill.Enum.LimitType;
import com.manster.seckill.annotation.RateLimiter;
import com.manster.seckill.util.IpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author wxh
 * @date 2022-05-31 21:42
 * 限流切面
 */
@Slf4j
@Aspect
@Component
//在我们写controller或者Service层的时候，需要注入很多的mapper接口或者另外的service接口
// 这时候就会写很多的@Autowired注解，代码看起来很乱lombok提供了一个注解：
//@RequiredArgsConstructor(onConstructor =@_(@Autowired))
//写在类上可以代替@Autowired注解，需要注意的是在注入时需要用final定义，或者使用@notnull注解
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RedisRateLimiterAspect {
    private final static String SEPARATOR = ":";
    private final static String REDIS_LIMIT_KEY_PREFIX = "limit:";
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private RedisScript<Long> limitScript;

    @Pointcut("@annotation(com.manster.seckill.annotation.RateLimiter)")
    public void rateLimit() {

    }

    @Before("rateLimit()")
    public void doBefore(JoinPoint point) throws Throwable {
        //获取拦截的方法
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        // 通过 AnnotationUtils.findAnnotation 获取 RateLimiter 注解信息
        RateLimiter rateLimiter = AnnotationUtils.findAnnotation(method, RateLimiter.class);
        if(rateLimiter!=null){
            String key = rateLimiter.key();
            // 默认用类名+方法名做限流的 key 前缀
            if (StrUtil.isBlank(key)) {
                key = method.getDeclaringClass().getName() + StrUtil.DOT + method.getName();
            }
            // 若注解为IP限流，最终限流的 key 为 前缀 + IP地址
            // TODO: 此时需要考虑局域网多用户访问的情况，因此 key 后续需要加上方法参数更加合理
            if (rateLimiter.limitType() == LimitType.IP) {
                key = key + SEPARATOR + IpUtils.getIpAddr();
            }

            long timeout = rateLimiter.timeout();
            long max = rateLimiter.max();
            TimeUnit timeUnit = rateLimiter.timeUnit();

            boolean limited = shouldLimited(key, max, timeout, timeUnit);
            if (limited) {
                throw new RuntimeException("手速太快了，慢点儿吧~");
            }
        }
    }

    private boolean shouldLimited(String key, long max, long timeout, TimeUnit timeUnit) {
        // 最终的 key 格式为：
        // limit:自定义key:(IP)
        // limit:类名.方法名:(IP)
        key = REDIS_LIMIT_KEY_PREFIX + key;
        // 统一使用单位毫秒
        long ttl = timeUnit.toMillis(timeout);
        // 当前时间毫秒数
        long now = Instant.now().toEpochMilli();
        long expired = now - ttl;
        // 这里如果使用的是StringRedisTemplate参数就必须转为 String,否则会报错 java.lang.Long cannot be cast to java.lang.String
        Long executeTimes = redisTemplate.execute(limitScript, Collections.singletonList(key), now , ttl , expired , max );
        if (executeTimes != null) {
            if (executeTimes == 0) {
                log.error("【{}】在单位时间 {} 毫秒内已达到访问上限，当前接口上限 {}", key, ttl, max);
                return true;
            } else {
                log.info("【{}】在单位时间 {} 毫秒内访问 {} 次", key, ttl, executeTimes);
                return false;
            }
        }
        return false;
    }
}
