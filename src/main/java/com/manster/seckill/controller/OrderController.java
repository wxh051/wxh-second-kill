package com.manster.seckill.controller;

import com.manster.seckill.error.BusinessException;
import com.manster.seckill.error.EmBusinessError;
import com.manster.seckill.response.CommonReturnType;
import com.manster.seckill.service.OrderService;
import com.manster.seckill.service.model.OrderModel;
import com.manster.seckill.service.model.UserModel;
import org.springframework.beans.factory.annotation.Autowired;
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

    //订单创建
    @PostMapping(value = "/createOrder", consumes = {CONTENT_TYPE_FORMED})
    public CommonReturnType createItem(@RequestParam(name = "itemId")Integer itemId,
                               @RequestParam(name = "amount")Integer amount,
                               @RequestParam(name = "promoId",required = false)Integer promoId) throws BusinessException {

        //判断是否登录
        Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
        if(isLogin == null || !isLogin){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "请登录后下单");
        }

        //获取用户的登录信息
        UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");

        OrderModel orderModel = orderService.createOrder(userModel.getId(), itemId, promoId, amount);

        return CommonReturnType.create(null);
    }
}
