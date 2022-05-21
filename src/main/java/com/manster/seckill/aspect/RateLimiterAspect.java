package com.manster.seckill.aspect;

import com.google.common.util.concurrent.RateLimiter;
import com.manster.seckill.annotation.MyRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author wxh
 * @date 2022-05-21 14:40
 * <p>
 * 限流切面
 */

//@Slf4j是用作日志输出的，一般会在项目每个类的开头加入该注解，添加了该注释之后，就可以在代码中直接饮用log.info( ) 打印日志了
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {
    //⽤来存放不同接⼝的RateLimiter(key为接⼝名称，value为RateLimite)，实现：不同的接口，不同的流量控制
    private static final ConcurrentMap<String, RateLimiter> RATE_LIMITER_CACHE = new ConcurrentHashMap<>();

    @Pointcut("@annotation(com.manster.seckill.annotation.MyRateLimiter)")
    public void rateLimit() {

    }

    //环绕通知
    @Around("rateLimit()")
    public Object pointcut(ProceedingJoinPoint point) throws Throwable {
        //获取拦截的方法
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        // 通过 AnnotationUtils.findAnnotation 获取 RateLimiter 注解信息
        MyRateLimiter rateLimiter = AnnotationUtils.findAnnotation(method, MyRateLimiter.class);
        if (rateLimiter != null && rateLimiter.qps() > MyRateLimiter.NOT_LIMITED) {
            double qps = rateLimiter.qps();
//            String functionName = signature.getName();这么写也可以
            String functionName = method.getName();
            if (RATE_LIMITER_CACHE.get(functionName) == null) {
                // 初始化 QPS
                RATE_LIMITER_CACHE.put(functionName, RateLimiter.create(qps));
            }
            log.debug("[{}]的QPS设置为：{}", functionName, RATE_LIMITER_CACHE.get(functionName).getRate());
            //尝试获取令牌
            if (RATE_LIMITER_CACHE.get(functionName) != null &&
                    !RATE_LIMITER_CACHE.get(functionName).tryAcquire(rateLimiter.timeout(), rateLimiter.timeUnit())) {
//                throw new BusinessException(EmBusinessError.RATELIMIT, "手速太快了，慢点吧~");
                //这里抛出的是runtime异常
                throw new RuntimeException("手速太快了，慢点儿吧~");
            }
        }
        //point.proceed()相当于执行目标方法
        return point.proceed();
    }
}
