package com.manster.seckill.service;

import com.manster.seckill.service.model.PromoModel;

/**
 * @Author manster
 * @Date 2021/5/27
 **/
public interface PromoService {

    //根据商品id获取即将进行以及正在进行的活动信息
    PromoModel getPromoByItemId(Integer itemId);

    //活动发布
    void publishPromo(Integer promoId);

    //生成秒杀用的令牌
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);
}
