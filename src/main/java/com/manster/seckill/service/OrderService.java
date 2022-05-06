package com.manster.seckill.service;

import com.manster.seckill.error.BusinessException;
import com.manster.seckill.service.model.OrderModel;

/**
 * @Author manster
 * @Date 2021/5/26
 **/
public interface OrderService {

    //1.通过前端url传秒杀活动id,然后下单接口内校验对应id是否属于对应的商品且活动已开始
    //2.直接在下单接口内判断对应商品是否存在秒杀活动，如存在就以秒杀价格下单
    OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount) throws BusinessException;

    String generateOrderNo();

}
