package com.manster.seckill.controller;

import com.manster.seckill.error.BusinessException;
import com.manster.seckill.error.EmBusinessError;
import com.manster.seckill.mq.MqProducer;
import com.manster.seckill.response.CommonReturnType;
import com.manster.seckill.service.ItemService;
import com.manster.seckill.service.OrderService;
import com.manster.seckill.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

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

    //订单创建
    @PostMapping(value = "/createOrder", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType createItem(@RequestParam(name = "itemId") Integer itemId,
                                       @RequestParam(name = "amount") Integer amount,
                                       @RequestParam(name = "token", required = false) String token,
                                       @RequestParam(name = "promoId", required = false) Integer promoId) throws BusinessException {

        //判断是否登录
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

//      UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");

//        不需要这种了，使用mq
//        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId, promoId, amount);

        //判断库存是否已售罄，若对应的售罄key存在，则直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }

        //加入库存流水init状态，对应的库存流水就可以追踪异步扣减库存的消息
        String stockLogId = itemService.initStockLog(itemId, amount);

        //再去完成对应的下单事务型消息机制
        if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
        }
        return CommonReturnType.create(null);
    }
}
