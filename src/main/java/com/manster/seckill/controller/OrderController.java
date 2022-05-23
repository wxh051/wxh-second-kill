package com.manster.seckill.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.manster.seckill.error.BusinessException;
import com.manster.seckill.error.EmBusinessError;
import com.manster.seckill.mq.MqProducer;
import com.manster.seckill.response.CommonReturnType;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.OrderService;
import com.manster.seckill.service.PromoService;
import com.manster.seckill.service.model.UserModel;
import com.manster.seckill.util.CodeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @Author manster
 * @Date 2021/5/26
 **/
@RestController
@RequestMapping("/order")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class OrderController extends BaseController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private ItemService itemService;

    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter orderCreateTateLimiter;

    @PostConstruct
    public void init() {
        //new了一个仅有20个工作线程的线程池
        executorService = Executors.newFixedThreadPool(20);

        //permitsPerSecond设置为300,TPS（当时压测，两台服务器已经做到了700的流量，也就是每台服务器350，这里取一个保护的服务器的数值300）
        orderCreateTateLimiter=RateLimiter.create(300);
    }

    //生成验证码
    //不需要返回任何参数。因为已通过response写入，并且set到redis上
    //这里是post请求。但是测试时，通过get请求在浏览器直接敲get请求是没有这个context_type的，所以这里得把consumes = {CONTENT_TYPE_FORMED}去掉
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        //根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登录，不能生成验证码");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登录，不能生成验证码");
        }
        Map<String, Object> map = CodeUtil.generateCodeAndPic();

        redisTemplate.opsForValue().set("verify_code_" + userModel.getId(), map.get("code"));
        redisTemplate.expire("verify_code_" + userModel.getId(), 10, TimeUnit.MINUTES);

        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }

    //获取秒杀地址
    @RequestMapping(value = "/path",method = RequestMethod.GET)
    @ResponseBody
    public CommonReturnType getPath(@RequestParam(name = "itemId") Integer itemId) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登录，不能生成验证码");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登录，不能生成验证码");
        }
        String str=promoService.createPath(userModel,itemId);
        return CommonReturnType.create(str);

    }

    //生成秒杀令牌
    //amount不需要，promoID必传。
    @PostMapping(value = "/{path}/generatetoken", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType generatetoken(@PathVariable String path,
                                          @RequestParam(name = "itemId") Integer itemId,
                                          @RequestParam(name = "token", required = false) String token,
                                          @RequestParam(name = "promoId") Integer promoId,
                                          @RequestParam(name = "verifyCode") String verifyCode) throws BusinessException {
        //根据token获取用户信息
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登录，不能下单");
        }
        //获取用户的登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        //会话过期
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "会话过期，请再次登录");
        }

        //检验接口
        boolean check=promoService.checkPath(userModel,itemId,path);
        if(!check){
            throw new BusinessException(EmBusinessError.REQUEST_ILLEGAL);
        }

        //通过verifycode验证验证码的有效性
        String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
        if(StringUtils.isEmpty(redisVerifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"请求非法");
        }
        if(!redisVerifyCode.equalsIgnoreCase(verifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"请求非法，验证码错误");
        }

        //获取访问秒杀令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());

        //到了这里就代表一些非法请求
        if (promoToken == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        }

        //返回对应结果
        return CommonReturnType.create(promoToken);
    }

    //订单创建
    //promoToken不是必传，普通商品下单，可以不要
    @PostMapping(value = "/createOrder", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType createItem(@RequestParam(name = "itemId") Integer itemId,
                                       @RequestParam(name = "amount") Integer amount,
                                       @RequestParam(name = "token", required = false) String token,
                                       @RequestParam(name = "promoId", required = false) Integer promoId,
                                       @RequestParam(name = "promoToken", required = false) String promoToken) throws BusinessException {

        //返回失败，代表没有拿到令牌
        //看底层代码是通过这一秒令牌没了他等待一秒后优先抢占下一秒冲回去的令牌
        if(!orderCreateTateLimiter.tryAcquire()){
            throw new BusinessException(EmBusinessError.RATELIMIT);
        }
//
//        判断是否登录
//        Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
//        if(isLogin == null || !isLogin){
//            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "请登录后下单");
//        }

//        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "请登录后下单");
        }
        //获取用户的登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        //会话过期
        if (userModel == null) {
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "会话过期，请再次登录");
        }

        //校验秒杀令牌是否正确
        if (promoId != null) {
            String inRedisPromoToken =
                    (String) redisTemplate.opsForValue().get("promo_token_" + promoId + "_userid_" + userModel.getId() +
                            "_itemid_" + itemId);
            //先判断一下是否为空，若没有这个key，返回失败
            if (inRedisPromoToken == null) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
            if (!StringUtils.equals(promoToken, inRedisPromoToken)) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌校验失败");
            }
        }

//      UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");
//        不需要这种了，使用mq
//        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId, promoId, amount);

        //同步调用线程池的submit方法
        //拥塞窗口为20的等待队列，用来队列化泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //加入库存流水init状态，对应的库存流水就可以追踪异步扣减库存的消息
                String stockLogId = itemService.initStockLog(itemId, amount);

                //再去完成对应的下单事务型消息机制
                if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }

        return CommonReturnType.create(null);
    }
}
